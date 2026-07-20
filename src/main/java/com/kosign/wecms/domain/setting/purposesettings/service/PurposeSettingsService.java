package com.kosign.wecms.domain.setting.purposesettings.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.purposesettings.dto.DeletedPurposeListResponse;
import com.kosign.wecms.domain.setting.purposesettings.dto.DeletedPurposeRow;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeReorderItem;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeReorderRequest;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingCreateRequest;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingDetail;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingListResponse;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingRow;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingUpdateRequest;
import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSetInfm;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeInUseRepository;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository;
import com.kosign.wecms.domain.setting.purposesettings.repository.SystemUsageNameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings &gt; Purpose — single service entrypoint for every CRUD
 * + reorder + bulk-upload operation. Mirrors the legacy
 * {@code comm_0187_001_*} action JSPs:
 *
 * <ul>
 *   <li>{@link #list(String)} → legacy {@code _r001} + {@code _r002}
 *       merged. Refactor scope: {@code usag_grp_cd IS NULL},
 *       {@code use_yn = 'Y'}, reserved-codes excluded, optional
 *       keyword on {@code use_usag_nm}.</li>
 *   <li>{@link #detail(String)} → single-PK convenience for the edit
 *       prefetch.</li>
 *   <li>{@link #create(PurposeSettingCreateRequest)} → legacy
 *       {@code _c001}. PK generated via the ported
 *       {@code wb_get_next_val} DB function (OQ-3); {@code disp_ord}
 *       computed in-service ({@code MAX+1}, OQ-3 (p2)). One-shot retry
 *       on PK-collision {@code DataIntegrityViolationException}.</li>
 *   <li>{@link #update(String, PurposeSettingUpdateRequest)} → legacy
 *       {@code _u002} (REC sub-rule).</li>
 *   <li>{@link #delete(String)} → legacy {@code _u002} (REC2 sub-rule),
 *       PLUS the OQ-5 recursive transaction-reference check. One wire
 *       code ({@code WBE1199}) covers both the legacy "has children"
 *       case and the OQ-5 transaction-reference case.</li>
 *   <li>{@link #reorder(PurposeReorderRequest)} → legacy {@code _u004}.</li>
 *   <li>Excel upload (preview + import) → legacy
 *       {@code comm_0187_004_c001}. Per-row Create validation in one
 *       transaction; whole batch rolls back on first failure.</li>
 * </ul>
 *
 * <p><b>Tenant scoping:</b> every method reads {@code bizKey} from
 * {@link TenantContext#currentBizKey()} and threads it through every
 * repository call. Cross-tenant access cannot succeed.
 *
 * <p><b>Error codes (OQ-8 decided 2026-05-12):</b> {@code WBW1152}
 * (duplicate {@code hand_use_usag_cd}), {@code WBW1138} (duplicate
 * {@code use_usag_nm}), {@code WBE1199} (delete blocked — children
 * OR transaction-references), {@code WBE_VALIDATION} (every other
 * service-layer validation failure). No new {@code WBE_PURPOSE_*}
 * codes.
 *
 * <p><b>Audit log:</b> Create / Update / Delete / Reorder / Upload
 * write rows via the shared {@link AuditLogService} (one
 * {@code audit_logs_seq_no} per event, fanned out across the four
 * supported locales). {@code wsvc_id = "purpose-settings"} (refactor
 * route slug, per the 2026-05-09 cutover).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurposeSettingsService {

    /** Legacy {@code REF_NO} value referencing the
     *  {@code BUSI_USAG_SET_INFM} table. Audit-log column. */
    static final String REF_NO_BUSI_USAG_SET_INFM = "BUSI_USAG_SET_INFM";

    /** Refactor route slug used as the {@code AUDIT_LOGS_INFM.WSVC_ID}
     *  discriminator. Per the 2026-05-09 workspace CLAUDE.md cutover
     *  every refactor-written audit row uses the slug (NOT the legacy
     *  JEX page id). */
    static final String AUDIT_WSVC_ID = "purpose-settings";

    /** i18n keys for audit-log activity / detail strings — resolved
     *  per locale by {@link AuditLogService}. */
    private static final String AUDIT_ACTIVITY_KEY        = "audit.purpose_settings.activity";
    private static final String AUDIT_CREATE_DETAIL_KEY   = "audit.purpose_settings.create.detail";
    private static final String AUDIT_UPDATE_DETAIL_KEY   = "audit.purpose_settings.update.detail";
    private static final String AUDIT_DELETE_DETAIL_KEY   = "audit.purpose_settings.delete.detail";
    private static final String AUDIT_REORDER_DETAIL_KEY  = "audit.purpose_settings.reorder.detail";
    private static final String AUDIT_RESTORE_DETAIL_KEY   = "audit.purpose_settings.restore.detail";

    /** Reserved system codes never surfaced by the list endpoint.
     *  Mirrors {@code comm_0187_001_r001_act.jsp:58-67}. */
    static final Set<String> RESERVED_SYSTEM_CODES = Set.of(
            "1000000301",
            "3000000401", "3000000402", "3000000403", "3000000404",
            "3000000405", "3000000406", "3000000407", "3000000408");

    /** Default {@code disp_yn} when the wire field is absent on Create. */
    private static final String DEFAULT_DISP_YN = "Y";

    /** {@code BUSI_USAG_SET_INFM.MOD_DTM} legacy format —
     *  {@code yyyyMMddHHmmss} UTC. */
    private static final DateTimeFormatter MOD_DTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PurposeSettingsRepository  repo;
    private final PurposeInUseRepository     inUseRepo;
    private final AuditLogService            auditLogService;
    private final LegacyFunctionRepository   legacyFunctions;
    private final SystemUsageNameRepository  sysNameRepo;

    /* -----------------------------------------------------------------
     *  Read paths
     * ----------------------------------------------------------------- */

    /** Backward-compatible entrypoint — historic {@code usag_grp_cd IS
     *  NULL} scope. Kept so existing callers (e.g. {@link #reorder}) and
     *  any consumer that doesn't care about tabs stay unchanged. */
    @Transactional(readOnly = true)
    public PurposeSettingListResponse list(String keyword) {
        return list(keyword, null);
    }

    /**
     * List active purposes for a tab group. {@code group} is
     * {@code null} (historic refactor scope), {@code "4"} (Banking
     * Deposit) or {@code "6"} (Banking Withdrawal) — mirrors legacy
     * {@code comm_0187_001_r001_act.jsp} {@code USAG_GRP_CD} branch.
     */
    @Transactional(readOnly = true)
    public PurposeSettingListResponse list(String keyword, String group) {
        String bizKey = requireBizKey();
        String grp    = normalizeGroup(group);
        // Empty-string sentinel — never null. The repository's JPQL
        // checks `:keyword = ''` to short-circuit the LIKE arm so the
        // null parameter never reaches PostgreSQL's LOWER(...) where
        // it would infer `bytea` and fail with `function lower(bytea)
        // does not exist`. See repository method Javadoc.
        String norm = (keyword == null || keyword.isBlank()) ? "" : keyword.trim();

        List<BusiUsagSetInfm> rows = repo.findActiveForTenant(bizKey, grp, RESERVED_SYSTEM_CODES, norm);
        Set<String> inUse = resolveInUseCodes(bizKey, rows);
        Map<String, SystemUsageNameRepository.Name> sysNames = resolveSystemNames(rows);

        List<PurposeSettingRow> tree = assembleTree(rows, inUse, sysNames);
        log.debug("purpose-settings list bizKey={} group={} rows={} top={} keyword={}",
                bizKey, grp, rows.size(), tree.size(), norm);
        return new PurposeSettingListResponse(tree);
    }

    /**
     * "Deleted Purpose" tab — soft-deleted rows ({@code use_yn = 'N'})
     * across the deposit/withdrawal buckets plus the historic NULL
     * scope. Mirrors legacy {@code comm_0187_003_r001} (fetchDataUnused).
     * Flat list; the frontend renders the Categories column from
     * {@code usag_grp_cd} and offers Restore per row.
     */
    @Transactional(readOnly = true)
    public DeletedPurposeListResponse listDeleted() {
        String bizKey = requireBizKey();
        List<BusiUsagSetInfm> rows = repo.findDeletedForTenant(bizKey, RESERVED_SYSTEM_CODES);
        Map<String, SystemUsageNameRepository.Name> sysNames = resolveSystemNames(rows);
        List<DeletedPurposeRow> out = new ArrayList<>(rows.size());
        for (BusiUsagSetInfm r : rows) {
            out.add(new DeletedPurposeRow(
                    r.getUseUsagCd(),
                    r.getHandUseUsagCd(),
                    resolveName(sysNames, r.getUseUsagCd(), r.getUseUsagNm()),
                    resolveCont(sysNames, r.getUseUsagCd(), r.getUseUsagCont()),
                    r.getUsagGrpCd(),
                    r.getModId(),
                    r.getModDtm()));
        }
        log.debug("purpose-settings deleted list bizKey={} rows={}", bizKey, out.size());
        return new DeletedPurposeListResponse(out);
    }

    /**
     * Restore a soft-deleted purpose ({@code use_yn = 'N' -> 'Y'}).
     * Mirrors legacy {@code comm_0187_003_u001} REC sub-rule.
     */
    @Transactional
    public void restore(String useUsagCd) {
        String bizKey = requireBizKey();
        String userId = requireUserId();
        String safe   = requireField("use_usag_cd", useUsagCd);

        BusiUsagSetInfm entity = repo.findByBizKeyAndUseUsagCd(bizKey, safe)
                .orElseThrow(() -> notFoundDetail(bizKey, safe));

        entity.setUseYn("Y");
        entity.setModId(userId);
        entity.setModDtm(nowAsLegacyTs());
        repo.save(entity);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_BUSI_USAG_SET_INFM)
                .refNo1(safe)
                .refNo2(entity.getHandUseUsagCd())
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_RESTORE_DETAIL_KEY)
                .build());

        log.info("purpose-settings restored bizKey={} useUsagCd={} handCd={}",
                bizKey, safe, entity.getHandUseUsagCd());
    }

    /** Normalise a wire group value — blank → {@code null} (historic
     *  scope); otherwise the trimmed {@code "4"}/{@code "6"} literal. */
    private static String normalizeGroup(String group) {
        if (group == null) return null;
        String t = group.trim();
        return t.isEmpty() ? null : t;
    }

    @Transactional(readOnly = true)
    public PurposeSettingDetail detail(String useUsagCd) {
        String bizKey = requireBizKey();
        String safe = requireField("use_usag_cd", useUsagCd);
        BusiUsagSetInfm entity = repo.findByBizKeyAndUseUsagCd(bizKey, safe)
                .orElseThrow(() -> notFoundDetail(bizKey, safe));
        Set<String> inUse = resolveInUseCodes(bizKey, List.of(entity));
        return toDetail(entity, inUse.contains(entity.getUseUsagCd()));
    }

    /* -----------------------------------------------------------------
     *  Create
     * ----------------------------------------------------------------- */

    @Transactional
    public PurposeSettingDetail create(PurposeSettingCreateRequest req) {
        String bizKey = requireBizKey();
        String userId = requireUserId();

        String handCd = req.handUseUsagCd().trim();
        String name   = req.useUsagNm().trim();
        String cont   = trimToNull(req.useUsagCont());
        String parent = trimToNull(req.parentUseUsagCd());
        String dispYn = (req.dispYn() == null) ? DEFAULT_DISP_YN : req.dispYn();
        // Target tab group — '4' (Deposit) / '6' (Withdrawal) / null
        // (historic scope). Duplicate + disp_ord scoping follow the group.
        String group  = normalizeGroup(req.usagGrpCd());

        // Legacy parity (WBW1152 / WBW1138): duplicate scope is
        // (biz_key, usag_grp_cd = group, use_yn = 'Y').
        if (repo.findActiveByHandCd(bizKey, group, handCd).isPresent()) {
            throw new BusinessException("WBW1152");
        }
        if (repo.findActiveByName(bizKey, group, name).isPresent()) {
            throw new BusinessException("WBW1138");
        }
        validateParent(bizKey, parent, /* selfUseUsagCd */ null);

        int nextDispOrd = repo.findMaxDispOrd(bizKey, group).orElse(0) + 1;
        BusiUsagSetInfm saved = persistCreate(bizKey, userId, handCd, name, cont, parent, dispYn, group, nextDispOrd);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_BUSI_USAG_SET_INFM)
                .refNo1(saved.getUseUsagCd())
                .refNo2(handCd)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_CREATE_DETAIL_KEY)
                .build());

        log.info("purpose-settings created bizKey={} useUsagCd={} handCd={} parent={}",
                bizKey, saved.getUseUsagCd(), handCd, parent);
        // Newly-created row cannot be referenced by anything yet —
        // usedInTransactions is always false on the post-Create echo.
        return toDetail(saved, false);
    }

    /** Persist a new row using the ported {@code wb_get_next_val}
     *  function for PK generation. The function uses bare {@code MAX+1}
     *  with no lock; on the rare concurrent-create race the PK
     *  constraint catches the collision and we retry once. */
    private BusiUsagSetInfm persistCreate(
            String bizKey, String userId,
            String handCd, String name, String cont, String parent,
            String dispYn, String group, int dispOrd) {
        DataIntegrityViolationException lastDive = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            // PK via the single LegacyFunctionRepository funnel (V140 #12b).
            // Empty usag_grp_cd → plain 0000000001.. sequence; '4'/'6' →
            // the legacy deposit/withdrawal branch (mirrors legacy
            // comm_0187_001_c001 SeqHelper.getNextVal with the group).
            String useUsagCd = legacyFunctions.nextUseUsagCd(bizKey, group == null ? "" : group);
            if (useUsagCd == null || useUsagCd.isBlank()) {
                // Defensive: the branch always returns a value when the
                // function's argument-presence checks pass. A null
                // here indicates a function-shape regression.
                throw new BusinessException("WBE9999");
            }
            BusiUsagSetInfm entity = new BusiUsagSetInfm();
            entity.setBizKey(bizKey);
            entity.setUseUsagCd(useUsagCd);
            entity.setHandUseUsagCd(handCd);
            entity.setUseUsagNm(name);
            entity.setUseUsagCont(cont);
            entity.setParentUseUsagCd(parent);
            entity.setDispYn(dispYn);
            entity.setDispOrd(dispOrd);
            entity.setUseYn("Y");
            // Tab group: '4' (Deposit) / '6' (Withdrawal); null keeps the
            // historic refactor scope. Legacy comm_0187_001 inserts the
            // active tab's data-usag-grp-cd here.
            entity.setUsagGrpCd(group);
            entity.setModId(userId);
            entity.setModDtm(nowAsLegacyTs());
            try {
                return repo.saveAndFlush(entity);
            } catch (DataIntegrityViolationException dive) {
                lastDive = dive;
                log.warn("purpose-settings PK collision bizKey={} useUsagCd={} attempt={}",
                        bizKey, useUsagCd, attempt + 1);
            }
        }
        throw new BusinessException("WBE9999", lastDive != null ? lastDive.getMessage() : null);
    }

    /* -----------------------------------------------------------------
     *  Update
     * ----------------------------------------------------------------- */

    @Transactional
    public PurposeSettingDetail update(String useUsagCd, PurposeSettingUpdateRequest req) {
        String bizKey = requireBizKey();
        String userId = requireUserId();
        String safe   = requireField("use_usag_cd", useUsagCd);

        String handCd = req.handUseUsagCd().trim();
        String name   = req.useUsagNm().trim();
        String cont   = trimToNull(req.useUsagCont());
        String parent = trimToNull(req.parentUseUsagCd());
        String dispYn = (req.dispYn() == null) ? null : req.dispYn();

        BusiUsagSetInfm entity = repo.findByBizKeyAndUseUsagCd(bizKey, safe)
                .orElseThrow(() -> notFoundDetail(bizKey, safe));

        // Legacy parity (OQ-4): NO duplicate-handCd check on update.
        // use_usag_nm uniqueness still applies, excluding self — scoped
        // to the row's own tab group so a Deposit name can coexist with
        // the same Withdrawal name (each tab is its own namespace).
        if (repo.findDuplicateNameExcludingSelf(bizKey, entity.getUsagGrpCd(), name, safe).isPresent()) {
            throw new BusinessException("WBW1138");
        }
        validateParent(bizKey, parent, safe);
        validateNoDemoteWithChildren(bizKey, safe, parent);

        entity.setHandUseUsagCd(handCd);
        entity.setUseUsagNm(name);
        entity.setUseUsagCont(cont);
        entity.setParentUseUsagCd(parent);
        if (dispYn != null) entity.setDispYn(dispYn);
        entity.setModId(userId);
        entity.setModDtm(nowAsLegacyTs());

        BusiUsagSetInfm saved = repo.save(entity);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_BUSI_USAG_SET_INFM)
                .refNo1(saved.getUseUsagCd())
                .refNo2(handCd)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_UPDATE_DETAIL_KEY)
                .build());

        log.info("purpose-settings updated bizKey={} useUsagCd={} handCd={} parent={}",
                bizKey, saved.getUseUsagCd(), handCd, parent);
        Set<String> inUse = resolveInUseCodes(bizKey, List.of(saved));
        return toDetail(saved, inUse.contains(saved.getUseUsagCd()));
    }

    /* -----------------------------------------------------------------
     *  Delete (soft) — OQ-5 recursive pre-check
     * ----------------------------------------------------------------- */

    @Transactional
    public void delete(String useUsagCd) {
        String bizKey = requireBizKey();
        String userId = requireUserId();
        String safe   = requireField("use_usag_cd", useUsagCd);

        BusiUsagSetInfm entity = repo.findByBizKeyAndUseUsagCd(bizKey, safe)
                .orElseThrow(() -> notFoundDetail(bizKey, safe));

        // 1. Legacy parity — block delete when an active child references
        //    this row as parent. Mirrors comm_0187_001_u002_act.jsp:75-91.
        if (repo.hasActiveChildren(bizKey, safe)) {
            throw new BusinessException("WBE1199");
        }
        // 2. OQ-5 — recursive transaction-reference check. The row
        //    itself OR any active child of the row referenced by an
        //    active payment / transfer / expense row blocks delete
        //    with the same WBE1199 code (per OQ-8 — one wire code).
        Set<String> codesToCheck = new HashSet<>();
        codesToCheck.add(safe);
        codesToCheck.addAll(repo.findActiveChildCodes(bizKey, safe));
        if (inUseRepo.existsAnyByBizKeyAndUseUsagCdIn(bizKey, codesToCheck)) {
            throw new BusinessException("WBE1199");
        }

        entity.setUseYn("N");
        entity.setModId(userId);
        entity.setModDtm(nowAsLegacyTs());
        repo.save(entity);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_BUSI_USAG_SET_INFM)
                .refNo1(safe)
                .refNo2(entity.getHandUseUsagCd())
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_DELETE_DETAIL_KEY)
                .build());

        log.info("purpose-settings deleted bizKey={} useUsagCd={} handCd={}",
                bizKey, safe, entity.getHandUseUsagCd());
    }

    /* -----------------------------------------------------------------
     *  Reorder
     * ----------------------------------------------------------------- */

    @Transactional
    public PurposeSettingListResponse reorder(PurposeReorderRequest req) {
        String bizKey = requireBizKey();
        String userId = requireUserId();

        List<PurposeReorderItem> items = req.items();
        Map<String, Integer> nextOrdByCd = new LinkedHashMap<>(items.size() * 2);
        Set<Integer> seenOrds = new HashSet<>(items.size() * 2);
        for (PurposeReorderItem item : items) {
            if (nextOrdByCd.put(item.useUsagCd(), item.dispOrd()) != null) {
                throw new BusinessException("WBE_VALIDATION",
                        "items: duplicate use_usag_cd " + item.useUsagCd());
            }
            if (!seenOrds.add(item.dispOrd())) {
                throw new BusinessException("WBE_VALIDATION",
                        "items: duplicate disp_ord " + item.dispOrd());
            }
        }

        List<BusiUsagSetInfm> rows = repo.findAllActiveForReorder(bizKey, nextOrdByCd.keySet());
        if (rows.size() != nextOrdByCd.size()) {
            // Either a stale row (since deleted) or a code from another
            // tenant. Either way the payload is invalid; reject the
            // whole batch rather than partially applying.
            throw new BusinessException("WBE_VALIDATION",
                    "items: one or more use_usag_cd is not active in this tenant");
        }

        String modDtm = nowAsLegacyTs();
        for (BusiUsagSetInfm row : rows) {
            row.setDispOrd(nextOrdByCd.get(row.getUseUsagCd()));
            row.setModId(userId);
            row.setModDtm(modDtm);
        }
        // Hibernate dirty-checks the managed entities; no explicit save
        // needed for the whole list. saveAll is harmless either way.
        repo.saveAll(rows);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_BUSI_USAG_SET_INFM)
                .refNo1(String.valueOf(rows.size()))
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_REORDER_DETAIL_KEY)
                .msgArgs(new Object[]{ rows.size() })
                .build());

        log.info("purpose-settings reordered bizKey={} rows={}", bizKey, rows.size());
        return list(null);
    }

    /* -----------------------------------------------------------------
     *  Bulk upload moved to PurposeExcelService — two-step preview +
     *  import flow mirroring VAS Account + legacy comm_0187_004.
     * ----------------------------------------------------------------- */

    /* -----------------------------------------------------------------
     *  Internal helpers — tree assembly + validation + mapping
     * ----------------------------------------------------------------- */

    /** Resolve system-master localized names for a row set (legacy
     *  {@code USAG_INFM_LANG_DETL}). Runs in its own inner transaction;
     *  a missing master table (partially-migrated dev DB) degrades to an
     *  empty map so names simply stay blank — no read-path failure. */
    private Map<String, SystemUsageNameRepository.Name> resolveSystemNames(List<BusiUsagSetInfm> rows) {
        if (rows.isEmpty()) return Map.of();
        Set<String> codes = new HashSet<>(rows.size() * 2);
        for (BusiUsagSetInfm r : rows) codes.add(r.getUseUsagCd());
        try {
            return sysNameRepo.resolveByLang(codes, currentLangCode());
        } catch (Exception e) {
            log.debug("purpose-settings system name resolve skipped: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Display name = per-business value when present, else the
     *  system-master localized name. */
    private static String resolveName(
            Map<String, SystemUsageNameRepository.Name> sys, String cd, String bizValue) {
        if (bizValue != null && !bizValue.isBlank()) return bizValue;
        SystemUsageNameRepository.Name n = sys.get(cd);
        return n != null ? n.nm() : bizValue;
    }

    /** Description = per-business value when present, else the
     *  system-master localized description. */
    private static String resolveCont(
            Map<String, SystemUsageNameRepository.Name> sys, String cd, String bizValue) {
        if (bizValue != null && !bizValue.isBlank()) return bizValue;
        SystemUsageNameRepository.Name n = sys.get(cd);
        return n != null ? n.cont() : bizValue;
    }

    /** Current request language code (EN/KO/VI/KM) for the system-master
     *  name lookup; defaults to EN. */
    private static String currentLangCode() {
        LangCd lang = TenantContext.currentLangCd();
        return lang == null ? LangCd.EN.name() : lang.name();
    }

    /** Bulk-resolve {@code used_in_transactions} for a row set. One
     *  query against {@code v_purpose_in_use} covers every row. */
    private Set<String> resolveInUseCodes(String bizKey, List<BusiUsagSetInfm> rows) {
        if (rows.isEmpty()) return Set.of();
        Set<String> codes = new HashSet<>(rows.size() * 2);
        for (BusiUsagSetInfm r : rows) codes.add(r.getUseUsagCd());
        return inUseRepo.findInUseCodes(bizKey, codes);
    }

    /** Assemble the flat repo result into a level-1-with-children tree
     *  for the wire. Maintains {@code disp_ord} ordering at each
     *  level. Orphans (level-2 rows whose parent is missing) surface
     *  as top-level rows so they remain reachable. */
    private List<PurposeSettingRow> assembleTree(
            List<BusiUsagSetInfm> rows,
            Set<String> inUse,
            Map<String, SystemUsageNameRepository.Name> sysNames) {
        Map<String, PurposeSettingRow> byCode = new LinkedHashMap<>(rows.size() * 2);
        Map<String, List<PurposeSettingRow>> childrenByParent = new HashMap<>();

        for (BusiUsagSetInfm r : rows) {
            boolean used = inUse.contains(r.getUseUsagCd());
            PurposeSettingRow row = new PurposeSettingRow(
                    r.getUseUsagCd(),
                    r.getHandUseUsagCd(),
                    // System-default purposes carry a NULL per-business name;
                    // fall back to the system-master localized name so the
                    // grid reads the same as legacy comm_0187_001.
                    resolveName(sysNames, r.getUseUsagCd(), r.getUseUsagNm()),
                    resolveCont(sysNames, r.getUseUsagCd(), r.getUseUsagCont()),
                    r.getParentUseUsagCd(),
                    r.getDispYn(),
                    r.getDispOrd(),
                    used,
                    r.getModId(),
                    r.getModDtm(),
                    null);
            byCode.put(r.getUseUsagCd(), row);
        }

        List<PurposeSettingRow> top = new ArrayList<>();
        for (BusiUsagSetInfm r : rows) {
            PurposeSettingRow row = byCode.get(r.getUseUsagCd());
            String parent = r.getParentUseUsagCd();
            if (parent != null && byCode.containsKey(parent)) {
                childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(row);
            } else {
                top.add(row);
            }
        }
        // Repack the row with its resolved children list. Records are
        // immutable so we rebuild any row that has children.
        List<PurposeSettingRow> out = new ArrayList<>(top.size());
        for (PurposeSettingRow row : top) {
            List<PurposeSettingRow> kids = childrenByParent.get(row.useUsagCd());
            out.add(kids == null ? row : new PurposeSettingRow(
                    row.useUsagCd(),
                    row.handUseUsagCd(),
                    row.useUsagNm(),
                    row.useUsagCont(),
                    row.parentUseUsagCd(),
                    row.dispYn(),
                    row.dispOrd(),
                    row.usedInTransactions(),
                    row.modId(),
                    row.modDtm(),
                    kids));
        }
        return out;
    }

    /** Service-layer guard for the parent reference — verifies the
     *  candidate parent (a) exists, (b) is active, (c) is itself a
     *  level-1 row, and (d) isn't the row being updated.
     *  All failures throw {@code WBE_VALIDATION} per OQ-8. */
    private void validateParent(String bizKey, String parent, String selfUseUsagCd) {
        if (parent == null) return;
        if (parent.equals(selfUseUsagCd)) {
            throw new BusinessException("WBE_VALIDATION",
                    "parent_use_usag_cd: cannot equal use_usag_cd");
        }
        BusiUsagSetInfm candidate = repo.findByBizKeyAndUseUsagCd(bizKey, parent)
                .orElseThrow(() -> new BusinessException("WBE_VALIDATION",
                        "parent_use_usag_cd: not found"));
        if (!"Y".equals(candidate.getUseYn())) {
            throw new BusinessException("WBE_VALIDATION",
                    "parent_use_usag_cd: not active");
        }
        if (candidate.getParentUseUsagCd() != null) {
            // Three-level nesting forbidden.
            throw new BusinessException("WBE_VALIDATION",
                    "parent_use_usag_cd: must reference an active level-1 purpose");
        }
    }

    /** Forbid demoting a level-1 row that currently has active
     *  children. Such a move would create three levels. Throws
     *  {@code WBE_VALIDATION} per OQ-8. */
    private void validateNoDemoteWithChildren(String bizKey, String useUsagCd, String newParent) {
        if (newParent == null) return;
        if (repo.hasActiveChildren(bizKey, useUsagCd)) {
            throw new BusinessException("WBE_VALIDATION",
                    "parent_use_usag_cd: cannot assign a parent to a purpose that itself has active children");
        }
    }

    private static PurposeSettingDetail toDetail(BusiUsagSetInfm e, boolean inUse) {
        return new PurposeSettingDetail(
                e.getUseUsagCd(),
                e.getHandUseUsagCd(),
                e.getUseUsagNm(),
                e.getUseUsagCont(),
                e.getParentUseUsagCd(),
                e.getDispYn(),
                e.getDispOrd(),
                inUse,
                e.getModId(),
                e.getModDtm());
    }

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            // Unauthenticated path leaked into the service — config bug.
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

    private static String requireField(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", name + ": is required");
        }
        return value.trim();
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nowAsLegacyTs() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(MOD_DTM_FORMAT);
    }

    private static BusinessException notFoundDetail(String bizKey, String useUsagCd) {
        // Logged at warn — a missing row is a request-shape problem,
        // not a server fault. Frontend lists the same single envelope
        // regardless of "wrong tenant" vs "no such row" so no row
        // existence leaks.
        log.warn("purpose-settings detail not found bizKey={} useUsagCd={}", bizKey, useUsagCd);
        return new BusinessException("WBE_VALIDATION",
                "use_usag_cd: not found in this tenant");
    }
}
