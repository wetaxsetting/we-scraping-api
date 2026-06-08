package com.kosign.wecms.domain.setting.chartofaccounts.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.setting.chartofaccounts.ChartOfAccountsConstants;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountBulkActionRequest;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountBulkActionResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountBulkUploadRequest;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountBulkUploadResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountCreateRequest;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountListResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountRow;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountTreeResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountUpdateRequest;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountsSortField;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccountId;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Settings &gt; Chart of Accounts — CRUD service. Mirrors the seven
 * legacy {@code comm_0223_001_*} action JSPs:
 * <ul>
 *   <li>{@link #list(String, String, String)} → {@code comm_0223_001_r001}
 *       (legacy {@code IDO.CHART_ACCT_INFM_R001} +
 *       {@code SRCH_WD} ILIKE filter).</li>
 *   <li>{@link #tree()} → {@code comm_0223_001_r002} (legacy
 *       {@code IDO.CHART_ACCT_INFM_R002} — 100-line hierarchy CASE
 *       moved to Java in {@link ChartOfAccountsTreeBuilder}).</li>
 *   <li>{@link #detail(String)} → equivalent of the legacy single-row
 *       fetch (no dedicated JSP — used by the refactor's frontend
 *       inline edit refresh path).</li>
 *   <li>{@link #create(ChartOfAccountCreateRequest)} →
 *       {@code comm_0223_001_c001}. Allocates {@code CHART_ACCT_SEQ_NO}
 *       via the legacy {@code wb_get_next_val('chart_acct_infm',
 *       'chart_acct_seq_no', biz_key)} branch (V95). Revives
 *       soft-deleted rows with the same code instead of inserting.</li>
 *   <li>{@link #update(String, ChartOfAccountUpdateRequest)} →
 *       {@code comm_0223_001_u001}. Cycle / depth / self-parent
 *       additions vs. legacy (refactor recommendations endorsed by
 *       PO direction; OQ-3 cleanup branch deliberately NOT ported).</li>
 *   <li>{@link #delete(String)} → {@code comm_0223_001_d001}. Soft
 *       delete: re-parent children → rename own code on UK collision
 *       → flip {@code DEL_YN='Y'}. Folds legacy {@code u002} parent-
 *       null patch.</li>
 * </ul>
 *
 * <p>Audit-log: writes one event per Create / Update / Delete via the
 * shared {@link AuditLogService} with {@code wsvc_id =
 * "chart-of-accounts"}. Matches the firm-banking-settings / contacts
 * precedent (approval-line is the explicit legacy-parity exception).
 *
 * <p>Tenant scoping: every read / write constrains on
 * {@link TenantContext#currentBizKey()} as a PK component (writes) or
 * a WHERE-clause predicate (reads). Request body / query / path
 * parameters never carry {@code biz_key}.
 *
 * <p>OQ resolution status (as of 2026-05-13):
 * <ul>
 *   <li>OQ-1 (USER vs ADMIN write gating): OPEN pending PO. NOTE the
 *       controller IS gated by {@code @PreAuthorize(@menuAuthz
 *       .canAccess('settings-core-chart-of-accounts'))} — the menu
 *       grant is enforced at runtime; only the USER-vs-ADMIN write
 *       distinction is undecided. Earlier "no @PreAuthorize" wording was
 *       stale (corrected 2026-06-08).</li>
 *   <li>OQ-2 (cycle / depth / self-parent guards): YES — refactor
 *       additions enforced via {@link ChartOfAccountsValidator}.</li>
 *   <li>OQ-3 (legacy u001:237-256 cleanup branch): DROPPED. See
 *       memory {@code project_chart_of_accounts_oq_pending.md}.</li>
 *   <li>OQ-Audit: ENABLED (this class).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartOfAccountsService {

    /** Default ORDER BY for the list endpoint: {@code chart_acct_cd
     *  ASC} (mirrors legacy R001). Always tied-broken on
     *  {@code chartAcctSeqNo ASC} for determinism when codes are
     *  equal (impossible inside one tenant given the UK, but
     *  defensive). */
    static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.asc("chartAcctCd"),
            Sort.Order.asc("chartAcctSeqNo"));

    /** Legacy {@code yyyyMMddHHmmss} timestamp shape — what every
     *  {@code REG_DTM} / {@code MOD_DTM} column on the table stores. */
    private static final DateTimeFormatter DTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ChartOfAccountRepository  repo;
    private final LegacyFunctionRepository  legacyFnRepo;
    private final AuditLogService           auditLog;

    /** Self-reference through the Spring proxy so {@link #bulkUpload}
     *  can call {@link #create} with its own {@code @Transactional}
     *  scope — each row gets an independent transaction. */
    @Autowired @Lazy
    private ChartOfAccountsService self;

    /* ---------------------------------------------------------------
     *  Reads
     * --------------------------------------------------------------- */

    /**
     * List every active row for the current tenant. Optional
     * case-insensitive substring search over {@code chart_acct_cd} OR
     * {@code chart_acct_nm_en} (mirrors legacy ILIKE pair).
     *
     * @param search        optional substring; null / blank → no filter
     * @param sortField     wire token ({@code CHART_ACCT_CD} /
     *                      {@code CHART_ACCT_NM_EN} / {@code STTS} /
     *                      {@code MOD_DTM}) — unknown / blank → default
     * @param sortDirection {@code ASC} / {@code DESC} — unknown / blank → default
     */
    @Transactional(readOnly = true)
    public ChartOfAccountListResponse list(String search, String sortField, String sortDirection) {
        String bizKey = requireBizKey();
        Sort   sort   = buildSort(
                ChartOfAccountsSortField.parse(sortField).orElse(null),
                SortDirection.parse(sortDirection));

        List<ChartOfAccount> rows = (search == null || search.isBlank())
                ? repo.findByBizKeyAndDelYn(bizKey, ChartOfAccountsConstants.DEL_YN_ACTIVE, sort)
                : repo.searchByBizKey(bizKey, ChartOfAccountsConstants.DEL_YN_ACTIVE, search.trim(), sort);

        List<ChartOfAccountRow> projected = ChartOfAccountsTreeBuilder.buildRows(rows);
        log.debug("chart-of-accounts list returned {} rows for bizKey={}", projected.size(), bizKey);
        return new ChartOfAccountListResponse(
                projected,
                projected.size(),
                ChartOfAccountsConstants.RESTRICTED_CODES);
    }

    /**
     * Tree projection for the parent-account picker. Returns active
     * rows whose level resolves to 1..4 (orphans / deeper rows
     * dropped — mirrors legacy R002 outer filter).
     */
    @Transactional(readOnly = true)
    public ChartOfAccountTreeResponse tree() {
        String bizKey = requireBizKey();
        List<ChartOfAccount> rows = repo.findByBizKeyAndDelYn(
                bizKey, ChartOfAccountsConstants.DEL_YN_ACTIVE, DEFAULT_SORT);
        return new ChartOfAccountTreeResponse(ChartOfAccountsTreeBuilder.buildTree(rows));
    }

    /**
     * Single-row read. Used by the frontend when navigating to the
     * Settings panel with a deep-link / after a refresh.
     *
     * @throws BusinessException {@code WBE_CHART_OF_ACCOUNTS_NOT_FOUND}
     *         when no active row matches the PK for the current tenant.
     */
    @Transactional(readOnly = true)
    public ChartOfAccountRow detail(String chartAcctSeqNo) {
        String bizKey = requireBizKey();
        ChartOfAccount row = repo.findById(new ChartOfAccountId(bizKey, chartAcctSeqNo))
                .filter(c -> ChartOfAccountsConstants.DEL_YN_ACTIVE.equals(c.getDelYn()))
                .orElseThrow(() -> {
                    log.warn("chart-of-accounts detail not found bizKey={} seqNo={}",
                            bizKey, chartAcctSeqNo);
                    return new BusinessException("WBE_CHART_OF_ACCOUNTS_NOT_FOUND");
                });
        // Build via the tree builder so the row carries computed
        // chart_acct_level / has_children / restricted alongside the
        // raw entity columns (one re-fetch of the slice is cheap).
        List<ChartOfAccount> slice = repo.findByBizKeyAndDelYn(
                bizKey, ChartOfAccountsConstants.DEL_YN_ACTIVE, DEFAULT_SORT);
        return ChartOfAccountsTreeBuilder.buildRows(slice).stream()
                .filter(r -> r.chartAcctSeqNo().equals(row.getChartAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("WBE_CHART_OF_ACCOUNTS_NOT_FOUND"));
    }

    /* ---------------------------------------------------------------
     *  Writes
     * --------------------------------------------------------------- */

    /**
     * Create a new chart-of-accounts row, or revive a soft-deleted row
     * with the same code. Mirrors {@code comm_0223_001_c001_act.jsp:118-198}:
     * <ol>
     *   <li>Validate parent existence / cycle / depth / self-parent
     *       via {@link ChartOfAccountsValidator}.</li>
     *   <li>Block duplicate active code →
     *       {@code WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE}.</li>
     *   <li>If a soft-deleted row exists with the same code: revive
     *       it (clear {@code DEL_YN}, overwrite fields).</li>
     *   <li>Otherwise allocate {@code CHART_ACCT_SEQ_NO} from
     *       {@code wb_get_next_val} (V95) and INSERT. One-shot retry
     *       on {@code DataIntegrityViolationException} (PK race).</li>
     *   <li>Write an audit-log event.</li>
     * </ol>
     */
    @Transactional
    public ChartOfAccountRow create(ChartOfAccountCreateRequest req) {
        String bizKey = requireBizKey();
        String userId = requireUserId();
        String now    = utcNow14();

        String code       = trim(req.chartAcctCd());
        String parentCode = blankToNull(req.parentChartAcctCd());

        // Load tenant slice ONCE for the validator and pass through.
        List<ChartOfAccount> tenantSlice = repo.findByBizKeyAndDelYn(
                bizKey, ChartOfAccountsConstants.DEL_YN_ACTIVE, DEFAULT_SORT);
        Map<String, ChartOfAccount> activeByCode =
                ChartOfAccountsValidator.indexActiveByCode(tenantSlice);

        // Parent code is required on every user-driven create. PO
        // directive 2026-05-25 — level 1 (NULL parent) is reserved for
        // the three seeded RESTRICTED_CODES (ACC-01/02/03); user-driven
        // writes must land at level 2 or level 3. The depth-cap check
        // below (MAX_HIERARCHY_DEPTH=3) covers the upper bound.
        ChartOfAccountsValidator.assertParentRequired(parentCode);
        ChartOfAccountsValidator.assertNotSelfParent(code, parentCode);
        ChartOfAccountsValidator.assertParentExists(parentCode, activeByCode);
        // Calc Rule only allowed on level-3 rows. PO directive
        // 2026-05-25 — level 1 + level 2 act as grouping headers and
        // must not carry a +/- sign.
        ChartOfAccountsValidator.assertCalRuleLevel3Only(
                parentCode, req.calRule(), activeByCode);
        // Depth check assumes the new row is a leaf with no descendants
        // yet — so the deepest leaf is just the row's own depth.
        if (parentCode != null) {
            int depth = 1;
            String cursor = parentCode;
            for (int i = 0; i <= ChartOfAccountsConstants.MAX_HIERARCHY_DEPTH; i++) {
                if (cursor == null) break;
                depth++;
                ChartOfAccount n = activeByCode.get(cursor);
                if (n == null) break;
                cursor = n.getParentChartAcctCd();
            }
            if (depth > ChartOfAccountsConstants.MAX_HIERARCHY_DEPTH) {
                throw new BusinessException("WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED");
            }
        }

        Optional<ChartOfAccount> active = repo.findByBizKeyAndChartAcctCdAndDelYn(
                bizKey, code, ChartOfAccountsConstants.DEL_YN_ACTIVE);
        if (active.isPresent()) {
            throw new BusinessException("WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE", code);
        }

        Optional<ChartOfAccount> deleted = repo.findByBizKeyAndChartAcctCdAndDelYn(
                bizKey, code, ChartOfAccountsConstants.DEL_YN_DELETED);
        ChartOfAccount entity;
        if (deleted.isPresent()) {
            // Revive — same seq_no, refresh every field, flip DEL_YN.
            entity = deleted.get();
            entity.setDelYn(ChartOfAccountsConstants.DEL_YN_ACTIVE);
            applyRequest(entity, code, req, parentCode);
            entity.setModId(userId);
            entity.setModDtm(now);
            repo.save(entity);
            log.info("chart-of-accounts revived bizKey={} seqNo={} code={}",
                    bizKey, entity.getChartAcctSeqNo(), code);
        } else {
            entity = new ChartOfAccount();
            entity.setBizKey(bizKey);
            entity.setChartAcctSeqNo(allocSeqNo(bizKey));
            applyRequest(entity, code, req, parentCode);
            entity.setDelYn(ChartOfAccountsConstants.DEL_YN_ACTIVE);
            entity.setRegId(userId);
            entity.setRegDtm(now);
            entity.setModId(userId);
            entity.setModDtm(now);
            try {
                repo.save(entity);
            } catch (DataIntegrityViolationException ex) {
                // One-shot retry: re-check active dup (another caller
                // may have just inserted) and, if still clear, attempt
                // a fresh seq_no allocation. Second failure surfaces
                // the cleaner duplicate-code envelope.
                log.warn("chart-of-accounts insert race bizKey={} code={} — retrying once",
                        bizKey, code);
                if (repo.findByBizKeyAndChartAcctCdAndDelYn(
                        bizKey, code, ChartOfAccountsConstants.DEL_YN_ACTIVE).isPresent()) {
                    throw new BusinessException("WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE", code);
                }
                entity.setChartAcctSeqNo(allocSeqNo(bizKey));
                try {
                    repo.save(entity);
                } catch (DataIntegrityViolationException second) {
                    log.warn("chart-of-accounts insert race retry failed bizKey={} code={}",
                            bizKey, code, second);
                    throw new BusinessException("WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE", code);
                }
            }
            log.info("chart-of-accounts created bizKey={} seqNo={} code={}",
                    bizKey, entity.getChartAcctSeqNo(), code);
        }

        appendAudit("audit.chart_of_accounts.create.detail",
                entity.getChartAcctSeqNo(), entity.getChartAcctCd());
        return projectSavedRow(bizKey, entity);
    }

    /**
     * Update a chart-of-accounts row. Mirrors
     * {@code comm_0223_001_u001_act.jsp:170-279} (minus the
     * deliberately-dropped lines 237-256 — see OQ-3).
     */
    @Transactional
    public ChartOfAccountRow update(String chartAcctSeqNo, ChartOfAccountUpdateRequest req) {
        String bizKey = requireBizKey();
        String userId = requireUserId();
        String now    = utcNow14();

        ChartOfAccount existing = repo.findById(new ChartOfAccountId(bizKey, chartAcctSeqNo))
                .filter(c -> ChartOfAccountsConstants.DEL_YN_ACTIVE.equals(c.getDelYn()))
                .orElseThrow(() -> {
                    log.warn("chart-of-accounts update missing-row bizKey={} seqNo={}",
                            bizKey, chartAcctSeqNo);
                    return new BusinessException("WBE_CHART_OF_ACCOUNTS_NOT_FOUND");
                });

        ChartOfAccountsValidator.assertNotRestricted(existing.getChartAcctCd());

        String newCode       = trim(req.chartAcctCd());
        String newParentCode = blankToNull(req.parentChartAcctCd());

        ChartOfAccountsValidator.assertNotRenamingToRestricted(existing.getChartAcctCd(), newCode);
        // Parent stays required on Update — the user cannot promote a
        // level-2/3 row to a level-1 root by clearing its parent. Roots
        // are reserved for the three seeded RESTRICTED_CODES.
        ChartOfAccountsValidator.assertParentRequired(newParentCode);
        ChartOfAccountsValidator.assertNotSelfParent(newCode, newParentCode);

        if (!newCode.equals(existing.getChartAcctCd())) {
            Optional<ChartOfAccount> dup = repo.findByBizKeyAndChartAcctCdAndDelYnAndChartAcctSeqNoNot(
                    bizKey, newCode, ChartOfAccountsConstants.DEL_YN_ACTIVE, chartAcctSeqNo);
            if (dup.isPresent()) {
                throw new BusinessException("WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE", newCode);
            }
        }

        List<ChartOfAccount> tenantSlice = repo.findByBizKeyAndDelYn(
                bizKey, ChartOfAccountsConstants.DEL_YN_ACTIVE, DEFAULT_SORT);
        Map<String, ChartOfAccount> activeByCode =
                ChartOfAccountsValidator.indexActiveByCode(tenantSlice);

        ChartOfAccountsValidator.assertParentExists(newParentCode, activeByCode);
        ChartOfAccountsValidator.assertNoCycle(newCode, newParentCode, activeByCode);
        ChartOfAccountsValidator.assertDepthCap(existing.getChartAcctCd(), newParentCode, activeByCode);
        // Calc Rule only allowed on level-3 rows (PO directive
        // 2026-05-25). Rejects edits that try to attach a +/- sign to
        // a level-1 / level-2 row, even if the row already had one in
        // grandfathered DEV data.
        ChartOfAccountsValidator.assertCalRuleLevel3Only(
                newParentCode, req.calRule(), activeByCode);

        applyRequest(existing, newCode, req, newParentCode);
        existing.setModId(userId);
        existing.setModDtm(now);
        try {
            repo.save(existing);
        } catch (DataIntegrityViolationException ex) {
            log.warn("chart-of-accounts update conflict bizKey={} seqNo={} newCode={}",
                    bizKey, chartAcctSeqNo, newCode, ex);
            throw new BusinessException("WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE", newCode);
        }

        log.info("chart-of-accounts updated bizKey={} seqNo={} newCode={}",
                bizKey, chartAcctSeqNo, newCode);

        appendAudit("audit.chart_of_accounts.update.detail",
                chartAcctSeqNo, newCode);
        return projectSavedRow(bizKey, existing);
    }

    /**
     * Soft-delete a chart-of-accounts row. Mirrors
     * {@code comm_0223_001_d001_act.jsp:151-235}:
     * <ol>
     *   <li>Block restricted-code deletion →
     *       {@code WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE}.</li>
     *   <li>Re-parent every active child to the deleting row's parent
     *       (a single {@code UPDATE} statement).</li>
     *   <li>If another soft-deleted row exists with the same code,
     *       archive-rename the row being deleted
     *       ({@code prefix(0..4) + last5(seq_no) + 'D'}) so the UK
     *       {@code (BIZ_KEY, CHART_ACCT_CD, DEL_YN='Y')} stays unique.</li>
     *   <li>Flip {@code DEL_YN='Y'} and stamp audit columns.</li>
     * </ol>
     *
     * @throws BusinessException {@code WBE_CHART_OF_ACCOUNTS_NOT_FOUND}
     *         when no active row matches the PK for the current tenant;
     *         {@code WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE} when the
     *         row's code is in
     *         {@link ChartOfAccountsConstants#RESTRICTED_CODES}.
     */
    @Transactional
    public void delete(String chartAcctSeqNo) {
        String bizKey = requireBizKey();
        String userId = requireUserId();
        String now    = utcNow14();

        ChartOfAccount existing = repo.findById(new ChartOfAccountId(bizKey, chartAcctSeqNo))
                .filter(c -> ChartOfAccountsConstants.DEL_YN_ACTIVE.equals(c.getDelYn()))
                .orElseThrow(() -> {
                    log.warn("chart-of-accounts delete missing-row bizKey={} seqNo={}",
                            bizKey, chartAcctSeqNo);
                    return new BusinessException("WBE_CHART_OF_ACCOUNTS_NOT_FOUND");
                });

        ChartOfAccountsValidator.assertNotRestricted(existing.getChartAcctCd());

        // Step 1: re-parent active children to the deleting row's
        // parent. Folds legacy comm_0223_001_u002 in (parent-null
        // patch was always invoked from inside d001). Single UPDATE
        // statement keeps the transaction lean.
        int reparented = repo.reparentActiveChildren(
                bizKey,
                existing.getChartAcctCd(),
                existing.getParentChartAcctCd(),
                userId,
                now);
        if (reparented > 0) {
            log.info("chart-of-accounts re-parented {} children of seqNo={} → parentCd={}",
                    reparented, chartAcctSeqNo, existing.getParentChartAcctCd());
        }

        // Step 2: archive-rename if a deleted dup with the same code
        // already exists in the tenant (would collide on the UK on
        // DEL_YN='Y' after we flip). Mirrors legacy d001:120-126.
        String archiveCode = existing.getChartAcctCd();
        Optional<ChartOfAccount> deletedDup =
                repo.findByBizKeyAndChartAcctCdAndDelYnAndChartAcctSeqNoNot(
                        bizKey,
                        existing.getChartAcctCd(),
                        ChartOfAccountsConstants.DEL_YN_DELETED,
                        chartAcctSeqNo);
        if (deletedDup.isPresent()) {
            archiveCode = buildArchivedCode(existing.getChartAcctCd(), existing.getChartAcctSeqNo());
            log.info("chart-of-accounts archive-rename bizKey={} seqNo={} oldCode={} archiveCode={}",
                    bizKey, chartAcctSeqNo, existing.getChartAcctCd(), archiveCode);
        }
        existing.setChartAcctCd(archiveCode);

        // Step 3: flip DEL_YN='Y' + audit stamp.
        existing.setDelYn(ChartOfAccountsConstants.DEL_YN_DELETED);
        existing.setModId(userId);
        existing.setModDtm(now);
        repo.save(existing);

        log.info("chart-of-accounts deleted bizKey={} seqNo={}", bizKey, chartAcctSeqNo);

        appendAudit("audit.chart_of_accounts.delete.detail",
                chartAcctSeqNo, archiveCode);
    }

    /**
     * Deactivate a chart-of-accounts row — flip {@code STTS='I'}. Idempotent:
     * already-inactive rows pass through with no DB write. Mirrors the
     * legacy update path's status flip, scoped to the single column so the
     * bulk-action surface does not require the caller to ship every form
     * field. Restricted roots ({@code ACC-01 / 02 / 03}) are rejected with
     * {@code WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE} — matches Update's
     * restricted-edit guard.
     *
     * @throws BusinessException {@code WBE_CHART_OF_ACCOUNTS_NOT_FOUND}
     *         when no active row matches the PK for the current tenant;
     *         {@code WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE} when the
     *         row's code is in
     *         {@link ChartOfAccountsConstants#RESTRICTED_CODES}.
     */
    @Transactional
    public ChartOfAccountRow deactivate(String chartAcctSeqNo) {
        String bizKey = requireBizKey();
        String userId = requireUserId();
        String now    = utcNow14();

        ChartOfAccount existing = repo.findById(new ChartOfAccountId(bizKey, chartAcctSeqNo))
                .filter(c -> ChartOfAccountsConstants.DEL_YN_ACTIVE.equals(c.getDelYn()))
                .orElseThrow(() -> {
                    log.warn("chart-of-accounts deactivate missing-row bizKey={} seqNo={}",
                            bizKey, chartAcctSeqNo);
                    return new BusinessException("WBE_CHART_OF_ACCOUNTS_NOT_FOUND");
                });

        ChartOfAccountsValidator.assertNotRestricted(existing.getChartAcctCd());

        if (ChartOfAccountsConstants.STTS_INACTIVE.equals(existing.getStts())) {
            // Idempotent — already inactive. Skip the audit write to avoid
            // log noise on bulk re-runs.
            return projectSavedRow(bizKey, existing);
        }

        existing.setStts(ChartOfAccountsConstants.STTS_INACTIVE);
        existing.setModId(userId);
        existing.setModDtm(now);
        repo.save(existing);

        log.info("chart-of-accounts deactivated bizKey={} seqNo={} code={}",
                bizKey, chartAcctSeqNo, existing.getChartAcctCd());

        appendAudit("audit.chart_of_accounts.deactivate.detail",
                chartAcctSeqNo, existing.getChartAcctCd());
        return projectSavedRow(bizKey, existing);
    }

    /**
     * Bulk soft-delete chart-of-accounts rows by seq_no. Each row is
     * processed independently via {@link #delete(String)} through the
     * Spring proxy ({@code self}) so per-row failures (restricted code,
     * not found, etc.) do NOT roll back rows that already committed.
     * Matches the {@link #bulkUpload(ChartOfAccountBulkUploadRequest)}
     * precedent.
     */
    public ChartOfAccountBulkActionResponse bulkDelete(ChartOfAccountBulkActionRequest req) {
        int processed = 0;
        int failed    = 0;
        for (String seqNo : req.chartAcctSeqNos()) {
            try {
                self.delete(seqNo);
                processed++;
            } catch (Exception e) {
                log.warn("chart-of-accounts bulk-delete row skipped seqNo={}: {}",
                        seqNo, e.getMessage());
                failed++;
            }
        }
        log.info("chart-of-accounts bulk-delete done processed={} failed={}", processed, failed);
        return new ChartOfAccountBulkActionResponse(processed, failed);
    }

    /**
     * Bulk deactivate chart-of-accounts rows by seq_no. Each row is
     * processed independently via {@link #deactivate(String)} through
     * the Spring proxy ({@code self}) so per-row failures do NOT roll
     * back rows that already committed.
     */
    public ChartOfAccountBulkActionResponse bulkDeactivate(ChartOfAccountBulkActionRequest req) {
        int processed = 0;
        int failed    = 0;
        for (String seqNo : req.chartAcctSeqNos()) {
            try {
                self.deactivate(seqNo);
                processed++;
            } catch (Exception e) {
                log.warn("chart-of-accounts bulk-deactivate row skipped seqNo={}: {}",
                        seqNo, e.getMessage());
                failed++;
            }
        }
        log.info("chart-of-accounts bulk-deactivate done processed={} failed={}", processed, failed);
        return new ChartOfAccountBulkActionResponse(processed, failed);
    }

    /**
     * Bulk-create chart-of-accounts rows from an Excel upload batch.
     * Each row in {@code req.items()} is processed independently: a
     * failure on one row (duplicate code, depth exceeded, etc.) does
     * NOT roll back rows that already committed. The totals are returned
     * to the frontend so it can show the partial-failure dialog.
     *
     * <p>Calls {@link #create(ChartOfAccountCreateRequest)} through the
     * Spring proxy ({@code self}) so each row runs in its own
     * {@code @Transactional} scope.
     */
    public ChartOfAccountBulkUploadResponse bulkUpload(ChartOfAccountBulkUploadRequest req) {
        int created = 0;
        int failed  = 0;
        for (ChartOfAccountCreateRequest item : req.items()) {
            try {
                self.create(item);
                created++;
            } catch (Exception e) {
                log.warn("chart-of-accounts bulk-upload row skipped code={}: {}",
                        item.chartAcctCd(), e.getMessage());
                failed++;
            }
        }
        log.info("chart-of-accounts bulk-upload done created={} failed={}", created, failed);
        return new ChartOfAccountBulkUploadResponse(created, failed);
    }

    /* ---------------------------------------------------------------
     *  Internal helpers
     * --------------------------------------------------------------- */

    /** Project the just-saved / just-updated entity as a wire row.
     *  Builds against a refreshed tenant slice so {@code chart_acct_level}
     *  / {@code has_children} reflect the latest state. Falls back to a
     *  single-row projection if the slice mock doesn't contain the
     *  saved entity (the helper is the single touch-point for the
     *  post-write row build — keeps create / update symmetric and
     *  avoids the redundant {@code findById} that delegating to
     *  {@link #detail(String)} would trigger). */
    private ChartOfAccountRow projectSavedRow(String bizKey, ChartOfAccount entity) {
        List<ChartOfAccount> slice = repo.findByBizKeyAndDelYn(
                bizKey, ChartOfAccountsConstants.DEL_YN_ACTIVE, DEFAULT_SORT);
        Optional<ChartOfAccountRow> match = ChartOfAccountsTreeBuilder.buildRows(slice).stream()
                .filter(r -> r.chartAcctSeqNo().equals(entity.getChartAcctSeqNo()))
                .findFirst();
        if (match.isPresent()) return match.get();
        // Fallback: slice didn't carry the saved entity yet (test
        // harness or a race before the JPA cache flushes). Build a
        // synthetic projection from the entity itself — level /
        // has_children may read 1 / false on an orphaned synthetic
        // slice, but the wire envelope is still well-formed.
        return ChartOfAccountsTreeBuilder.buildRows(List.of(entity)).get(0);
    }

    /** Build the Spring Data Sort from a parsed field + direction pair.
     *  Falls back to {@link #DEFAULT_SORT} on null inputs. */
    private static Sort buildSort(ChartOfAccountsSortField field, SortDirection direction) {
        if (field == null || direction == null) return DEFAULT_SORT;
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        return Sort.by(primary).and(Sort.by(Sort.Order.asc("chartAcctSeqNo")));
    }

    /** Apply the wire request to a managed entity. Used by both
     *  Create (new row + revive) and Update. {@code code} and
     *  {@code parentCode} are pre-normalised by the caller. */
    private static void applyRequest(ChartOfAccount entity, String code,
                                      Object req, String parentCode) {
        // Cast at the call site keeps the helper signature generic
        // (Create and Update records share the same shape but no
        // common type — using sealed reflection adds zero value).
        if (req instanceof ChartOfAccountCreateRequest cr) {
            entity.setChartAcctCd(code);
            entity.setChartAcctNmEn(trim(cr.chartAcctNmEn()));
            entity.setChartAcctNmVn(blankToNull(cr.chartAcctNmVn()));
            entity.setChartAcctNmKo(blankToNull(cr.chartAcctNmKo()));
            entity.setChartAcctType(blankToNull(cr.chartAcctType()));
            entity.setParentChartAcctCd(parentCode);
            entity.setCalRule(blankToNull(cr.calRule()));
            entity.setStts(cr.stts());
        } else if (req instanceof ChartOfAccountUpdateRequest ur) {
            entity.setChartAcctCd(code);
            entity.setChartAcctNmEn(trim(ur.chartAcctNmEn()));
            entity.setChartAcctNmVn(blankToNull(ur.chartAcctNmVn()));
            entity.setChartAcctNmKo(blankToNull(ur.chartAcctNmKo()));
            entity.setChartAcctType(blankToNull(ur.chartAcctType()));
            entity.setParentChartAcctCd(parentCode);
            entity.setCalRule(blankToNull(ur.calRule()));
            entity.setStts(ur.stts());
        } else {
            throw new IllegalArgumentException(
                    "Unsupported request type: " + (req == null ? "null" : req.getClass().getName()));
        }
    }

    /** Build the legacy archive-code shape per
     *  {@code comm_0223_001_d001_act.jsp:120-126}:
     *  {@code prefix(0..4) + last5(seq_no) + 'D'}. */
    static String buildArchivedCode(String code, String seqNo) {
        String base = code == null ? "DEL0" : code;
        String seq  = seqNo == null ? "00000" : seqNo;
        String prefix    = base.length() > 4 ? base.substring(0, 4) : base;
        String seqSuffix = seq.length() > 5 ? seq.substring(seq.length() - 5) : seq;
        return prefix + seqSuffix + ChartOfAccountsConstants.ARCHIVE_CODE_SUFFIX;
    }

    /** Best-effort audit append. Failure is swallowed by the writer
     *  per its REQUIRES_NEW contract — the business write is the
     *  source of truth for "did the operation succeed". */
    private void appendAudit(String detailKey, String chartAcctSeqNo, String chartAcctCd) {
        auditLog.append(AuditLogContext.builder()
                .wsvcId(ChartOfAccountsConstants.WSVC_SLUG)
                .refNo(ChartOfAccountsConstants.AUDIT_REF_NO)
                .refNo1(chartAcctSeqNo)
                .refNo2(chartAcctCd)
                .activityKey("audit.chart_of_accounts.activity")
                .detailKey(detailKey)
                .build());
    }

    private String allocSeqNo(String bizKey) {
        String allocated = legacyFnRepo.nextChartAcctSeqNo(bizKey);
        if (allocated == null || allocated.isBlank()) {
            // Should never happen — the V95 branch always returns a
            // value for a valid tenant. Surface as a server error so
            // the trace shows up in logs.
            throw new BusinessException("WBE9999");
        }
        return allocated.trim();
    }

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            // Unauthenticated path leaked into the service — a config
            // bug, not a user-visible business outcome.
            throw new BusinessException("WBE9999");
        }
        return bizKey;
    }

    private static String requireUserId() {
        String userId = TenantContext.currentUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("WBE9999");
        }
        return userId;
    }

    private static String utcNow14() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FORMAT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
