package com.kosign.wecms.domain.setting.approvalline.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.setting.approvalline.ApprovalLineConstants;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineApproverInput;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineApproverRow;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineCreateRequest;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineListResponse;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineRow;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineSortField;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineUpdateRequest;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLine;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLineApprover;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLineApproverId;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLineId;
import com.kosign.wecms.domain.setting.approvalline.repository.ApprovalLineApproverRepository;
import com.kosign.wecms.domain.setting.approvalline.repository.ApprovalLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings &gt; Approval Line — CRUD service. Mirrors the five
 * legacy {@code comm_0222_*} action JSPs:
 * <ul>
 *   <li>{@link #list(String, String, String)} → {@code comm_0222_001_r001}
 *       (legacy {@code IDO.APPR_LINE_INFM_DETL_R002} +
 *       {@code USER_BUSI_CONN_INFM.USER_IMG} per-row lookup).</li>
 *   <li>{@link #detail(Long)} → {@code comm_0222_003_r001}.</li>
 *   <li>{@link #create(ApprovalLineCreateRequest)} →
 *       {@code comm_0222_002_c001}. Allocates {@code APPR_LINE_NO}
 *       via the legacy {@code wb_get_next_val('appr_line_infm',
 *       'appr_line_no', biz_key)} branch (V91 / OQ-7).</li>
 *   <li>{@link #update(Long, ApprovalLineUpdateRequest)} →
 *       {@code comm_0222_003_u001}. Rename + diff-sync of approvers
 *       (insert / update / delete in one transaction).</li>
 *   <li>{@link #delete(Long)} → {@code comm_0222_001_d001}. Hard
 *       delete of the master + all detail rows.</li>
 * </ul>
 *
 * <p>Audit-log: <b>NO writes</b> to {@code AUDIT_LOGS_INFM} for any
 * mutation in this feature. Feature § 14 OQ-6 resolved 2026-05-13
 * (PO decision — legacy parity; live JEX writes no audit rows for
 * {@code comm_0222_*} either). Deliberately divergent from the
 * {@code firm-banking-settings} / {@code contacts} precedents.
 *
 * <p>Tenant scoping: every read / write constrains on
 * {@link TenantContext#currentBizKey()} as a PK component (writes) or
 * a WHERE-clause predicate (reads). Request body / query / path
 * parameters never carry {@code biz_key}.
 *
 * <p>Approver validation: every {@code apprvr_id} in the request is
 * checked against {@code USER_BUSI_CONN_INFM} via
 * {@code findAllByUserIdInAndBizKeyAndDelYn(...)} — defensive guard
 * not present in legacy JEX. The picker is trusted client-side; the
 * service rejects ids that don't have an active link to the current
 * tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalLineService {

    /** Default ORDER BY for the list endpoint:
     *  {@code reg_dtm DESC, appr_line_no DESC}. The second clause is a
     *  deterministic tie-break for rows that share a timestamp. */
    static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("regDtm"), Sort.Order.desc("apprLineNo"));

    /** Legacy {@code yyyyMMddHHmmss} timestamp shape — what
     *  {@code APPR_LINE_INFM.REG_DTM} / {@code MOD_DTM} stores. UTC
     *  to match the JEX {@code DateTimeUtils.utcNow()} convention. */
    private static final DateTimeFormatter DTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Empty-string sentinel for {@code APPRVR_POSI} / {@code APPRVR_DEPT}
     *  when the request omits them. Legacy parity with
     *  {@code comm_0222_003_u001_act.jsp:144-148}. */
    private static final String EMPTY = "";

    /** Slug-based user-image URL template. The streaming endpoint
     *  itself is out of scope for this MR (feature § 4.5 / OQ-1
     *  resolved 2026-05-13). When the user has no stored image, the
     *  service returns {@code null} and the frontend's existing
     *  {@code <Avatar>} fallback renders an icon. */
    private static final String USER_IMAGE_URI_TEMPLATE = "/api/v1/users/%s/image";

    /** Active-link sentinel for {@code USER_BUSI_CONN_INFM.DEL_YN}. */
    private static final String DEL_YN_ACTIVE = "N";

    /** Active-tenant sentinel — only links that haven't been logically
     *  deleted are eligible to be approvers. */
    private static final String FIND_DEL_YN = DEL_YN_ACTIVE;

    private final ApprovalLineRepository         lineRepo;
    private final ApprovalLineApproverRepository approverRepo;
    private final UserBusiConnInfmRepository     userBusiRepo;
    private final LegacyFunctionRepository       legacyFnRepo;

    /* ---------------------------------------------------------------
     *  Reads
     * --------------------------------------------------------------- */

    /**
     * List every approval line for the current tenant, with the full
     * approver chain inline (ordered by {@code apprvr_disp_ord ASC}).
     * No pagination — feature § 4.3 / OQ-3 resolved 2026-05-13.
     *
     * @param q             optional name substring; case-insensitive
     *                      ILIKE match against {@code APPR_LINE_NM}
     * @param sortField     wire token ({@code REG_DTM} /
     *                      {@code APPR_LINE_NM} / {@code MOD_DTM}) or
     *                      null for default
     * @param sortDirection {@code ASC} / {@code DESC} or null for
     *                      default
     */
    @Transactional(readOnly = true)
    public ApprovalLineListResponse list(String q, String sortField, String sortDirection) {
        String bizKey = requireBizKey();
        Sort   sort   = buildSort(
                ApprovalLineSortField.parse(sortField).orElse(null),
                SortDirection.parse(sortDirection));

        List<ApprovalLine> lines = (q == null || q.isBlank())
                ? lineRepo.findByBizKey(bizKey, sort)
                : lineRepo.findByBizKeyAndApprLineNmContainingIgnoreCase(bizKey, q.trim(), sort);

        if (lines.isEmpty()) {
            log.debug("approval-line list returned 0 rows for bizKey={}", bizKey);
            return new ApprovalLineListResponse(0, List.of());
        }

        List<Long> lineNos = lines.stream().map(ApprovalLine::getApprLineNo).toList();
        List<ApprovalLineApprover> approvers = approverRepo
                .findAllByBizKeyAndApprLineNoInOrderByApprLineNoAscApprvrDispOrdAsc(bizKey, lineNos);

        Map<Long, List<ApprovalLineApprover>> byLine = new LinkedHashMap<>();
        for (ApprovalLineApprover a : approvers) {
            byLine.computeIfAbsent(a.getApprLineNo(), k -> new java.util.ArrayList<>()).add(a);
        }

        Map<String, String> imgUrlByApprvrId =
                resolveImageUrls(bizKey, approvers.stream().map(ApprovalLineApprover::getApprvrId).toList());

        List<ApprovalLineRow> rows = lines.stream()
                .map(line -> toRow(line, byLine.getOrDefault(line.getApprLineNo(), List.of()), imgUrlByApprvrId))
                .toList();

        log.debug("approval-line list returned {} rows for bizKey={}", rows.size(), bizKey);
        return new ApprovalLineListResponse(rows.size(), rows);
    }

    /**
     * Detail by surrogate. Mirrors {@code comm_0222_003_r001_act.jsp:
     * 86-111}. Returns the master + ordered approver chain — same
     * shape as one element of the list response (the frontend Edit
     * dialog reads this on mount).
     *
     * @throws BusinessException {@code WBE_APPROVAL_LINE_NOT_FOUND}
     *         when no row matches the PK for the current tenant.
     */
    @Transactional(readOnly = true)
    public ApprovalLineRow detail(Long apprLineNo) {
        String bizKey = requireBizKey();
        ApprovalLine line = lineRepo.findById(new ApprovalLineId(bizKey, apprLineNo))
                .orElseThrow(() -> {
                    log.warn("approval-line detail not found bizKey={} apprLineNo={}",
                            bizKey, apprLineNo);
                    return new BusinessException("WBE_APPROVAL_LINE_NOT_FOUND");
                });
        List<ApprovalLineApprover> approvers = approverRepo
                .findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(bizKey, apprLineNo);
        Map<String, String> imgUrlByApprvrId =
                resolveImageUrls(bizKey, approvers.stream().map(ApprovalLineApprover::getApprvrId).toList());
        return toRow(line, approvers, imgUrlByApprvrId);
    }

    /* ---------------------------------------------------------------
     *  Writes
     * --------------------------------------------------------------- */

    /**
     * Create a new approval line with 1..5 approvers for the current
     * tenant. Mirrors {@code comm_0222_002_c001_act.jsp:75-110}:
     * <ol>
     *   <li>Service-layer revalidation of name + size cap + duplicate
     *       guard + tenant-link check (defence in depth on top of
     *       bean validation).</li>
     *   <li>Allocate {@code APPR_LINE_NO} via
     *       {@code legacyFnRepo.nextApprLineNo(bizKey)}.</li>
     *   <li>Save master + every approver row inside one
     *       {@code @Transactional}. {@code apprvr_disp_ord} is
     *       assigned from request-array position (1..N), ignoring
     *       any client-supplied value.</li>
     * </ol>
     *
     * @throws BusinessException various {@code WBE_APPROVAL_LINE_*}
     *         codes — see {@link #validateApproverList} +
     *         {@link #verifyTenantLinks}.
     */
    @Transactional
    public ApprovalLineRow create(ApprovalLineCreateRequest req) {
        String bizKey = requireBizKey();
        String userId = requireUserId();
        String now    = utcNow14();

        String apprLineNm = requireApprLineNm(req.apprLineNm());
        validateApproverList(req.approvers());
        verifyTenantLinks(bizKey, req.approvers());

        Long apprLineNo = parseApprLineNo(legacyFnRepo.nextApprLineNo(bizKey));

        ApprovalLine line = new ApprovalLine();
        line.setBizKey(bizKey);
        line.setApprLineNo(apprLineNo);
        line.setApprLineNm(apprLineNm);
        line.setRegId(userId);
        line.setRegDtm(now);
        line.setModId(userId);
        line.setModDtm(now);
        lineRepo.save(line);

        for (int i = 0; i < req.approvers().size(); i++) {
            ApprovalLineApproverInput a = req.approvers().get(i);
            ApprovalLineApprover row = new ApprovalLineApprover();
            row.setBizKey(bizKey);
            row.setApprLineNo(apprLineNo);
            row.setApprvrId(a.apprvrId());
            row.setApprvrDispOrd(i + 1);
            row.setApprvrNm(a.apprvrNm());
            row.setApprvrPosi(coalesce(a.apprvrPosi()));
            row.setApprvrDept(coalesce(a.apprvrDept()));
            row.setRegId(userId);
            row.setRegDtm(now);
            row.setModId(userId);
            row.setModDtm(now);
            approverRepo.save(row);
        }

        log.info("approval-line created bizKey={} apprLineNo={} apprLineNm={} approvers={}",
                bizKey, apprLineNo, apprLineNm, req.approvers().size());

        return detail(apprLineNo);
    }

    /**
     * Rename + diff-sync the approver chain for one approval line.
     * Mirrors {@code comm_0222_003_u001_act.jsp:87-194}:
     * <ol>
     *   <li>Service-layer revalidation (same checks as Create).</li>
     *   <li>Load existing master (throw {@code WBE_APPROVAL_LINE_NOT_FOUND}
     *       when missing). Update name + audit fields.</li>
     *   <li>Build {@code Map<apprvrId, existingRow>} for the line.</li>
     *   <li>For each request approver: UPDATE if id already exists
     *       (new disp_ord, name, posi, dept, audit); INSERT
     *       otherwise.</li>
     *   <li>For ids in existing but not in request: DELETE.</li>
     * </ol>
     *
     * @throws BusinessException {@code WBE_APPROVAL_LINE_NOT_FOUND} on
     *         missing master; various validation codes — see
     *         {@link #validateApproverList} + {@link #verifyTenantLinks}.
     */
    @Transactional
    public ApprovalLineRow update(Long apprLineNo, ApprovalLineUpdateRequest req) {
        String bizKey = requireBizKey();
        String userId = requireUserId();
        String now    = utcNow14();

        String apprLineNm = requireApprLineNm(req.apprLineNm());
        validateApproverList(req.approvers());
        verifyTenantLinks(bizKey, req.approvers());

        ApprovalLine line = lineRepo.findById(new ApprovalLineId(bizKey, apprLineNo))
                .orElseThrow(() -> {
                    log.warn("approval-line update missing-row bizKey={} apprLineNo={}",
                            bizKey, apprLineNo);
                    return new BusinessException("WBE_APPROVAL_LINE_NOT_FOUND");
                });
        line.setApprLineNm(apprLineNm);
        line.setModId(userId);
        line.setModDtm(now);

        List<ApprovalLineApprover> existing = approverRepo
                .findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(bizKey, apprLineNo);
        Map<String, ApprovalLineApprover> existingById = new HashMap<>(existing.size());
        for (ApprovalLineApprover row : existing) {
            existingById.put(row.getApprvrId(), row);
        }
        Set<String> incomingIds = new HashSet<>(req.approvers().size());

        for (int i = 0; i < req.approvers().size(); i++) {
            ApprovalLineApproverInput a = req.approvers().get(i);
            int dispOrd = i + 1;
            incomingIds.add(a.apprvrId());
            ApprovalLineApprover row = existingById.get(a.apprvrId());
            if (row == null) {
                row = new ApprovalLineApprover();
                row.setBizKey(bizKey);
                row.setApprLineNo(apprLineNo);
                row.setApprvrId(a.apprvrId());
                row.setRegId(userId);
                row.setRegDtm(now);
            }
            row.setApprvrDispOrd(dispOrd);
            row.setApprvrNm(a.apprvrNm());
            row.setApprvrPosi(coalesce(a.apprvrPosi()));
            row.setApprvrDept(coalesce(a.apprvrDept()));
            row.setModId(userId);
            row.setModDtm(now);
            approverRepo.save(row);
        }

        for (Map.Entry<String, ApprovalLineApprover> e : existingById.entrySet()) {
            if (!incomingIds.contains(e.getKey())) {
                approverRepo.deleteById(new ApprovalLineApproverId(
                        bizKey, apprLineNo, e.getKey()));
            }
        }

        log.info("approval-line updated bizKey={} apprLineNo={} apprLineNm={} approvers={}",
                bizKey, apprLineNo, apprLineNm, req.approvers().size());

        return detail(apprLineNo);
    }

    /**
     * Hard-delete an approval line and every detail row for the
     * current tenant. Mirrors {@code comm_0222_001_d001_act.jsp:84-110}.
     *
     * @throws BusinessException {@code WBE_APPROVAL_LINE_NOT_FOUND}
     *         when no row matches the PK for the current tenant.
     */
    @Transactional
    public void delete(Long apprLineNo) {
        String bizKey = requireBizKey();
        ApprovalLineId id = new ApprovalLineId(bizKey, apprLineNo);
        if (!lineRepo.existsById(id)) {
            log.warn("approval-line delete missing-row bizKey={} apprLineNo={}",
                    bizKey, apprLineNo);
            throw new BusinessException("WBE_APPROVAL_LINE_NOT_FOUND");
        }
        approverRepo.deleteAllByBizKeyAndApprLineNo(bizKey, apprLineNo);
        lineRepo.deleteById(id);
        log.info("approval-line deleted bizKey={} apprLineNo={}", bizKey, apprLineNo);
    }

    /* ---------------------------------------------------------------
     *  Internal helpers
     * --------------------------------------------------------------- */

    /**
     * Build the Spring Data {@code Sort} from a parsed field +
     * direction pair. Falls back to {@link #DEFAULT_SORT} on null
     * inputs (unknown / blank wire token).
     */
    private static Sort buildSort(ApprovalLineSortField field, SortDirection direction) {
        if (field == null || direction == null) return DEFAULT_SORT;
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        // Always tie-break on apprLineNo DESC for deterministic ordering.
        return Sort.by(primary).and(Sort.by(Sort.Order.desc("apprLineNo")));
    }

    /**
     * Validate the approvers list at the service layer (defence in
     * depth on top of bean validation): non-empty, ≤ 5, no duplicate
     * {@code apprvr_id}. Throws the feature-specific WBE code so the
     * error envelope is precise rather than generic
     * {@code WBE_VALIDATION}.
     */
    private static void validateApproverList(List<ApprovalLineApproverInput> approvers) {
        if (approvers == null || approvers.isEmpty()) {
            throw new BusinessException("WBE_APPROVAL_LINE_EMPTY_APPROVERS");
        }
        if (approvers.size() > ApprovalLineConstants.MAX_APPROVERS) {
            throw new BusinessException("WBE_APPROVAL_LINE_TOO_MANY_APPROVERS");
        }
        Set<String> seen = new HashSet<>(approvers.size());
        for (ApprovalLineApproverInput a : approvers) {
            if (!seen.add(a.apprvrId())) {
                throw new BusinessException("WBE_APPROVAL_LINE_DUP_APPROVER", a.apprvrId());
            }
        }
    }

    /**
     * Verify that every {@code apprvr_id} in the request has an active
     * link ({@code DEL_YN='N'}) to the current tenant in
     * {@code USER_BUSI_CONN_INFM}. The picker is trusted client-side,
     * so this is a defensive guard against forged / stale ids.
     */
    private void verifyTenantLinks(String bizKey, List<ApprovalLineApproverInput> approvers) {
        Set<String> requested = new HashSet<>(approvers.size());
        for (ApprovalLineApproverInput a : approvers) requested.add(a.apprvrId());

        List<UserBusiConnInfm> links = userBusiRepo
                .findAllByUserIdInAndBizKeyAndDelYn(requested, bizKey, DEL_YN_ACTIVE);
        Set<String> linkedIds = new HashSet<>(links.size());
        for (UserBusiConnInfm link : links) linkedIds.add(link.getUserId());

        for (String id : requested) {
            if (!linkedIds.contains(id)) {
                log.warn("approval-line approver not linked to tenant bizKey={} apprvrId={}",
                        bizKey, id);
                throw new BusinessException("WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT", id);
            }
        }
    }

    /**
     * Resolve {@code user_img_url} for every approver in the supplied
     * id list with a single batched {@code findAllByUserIdInAndBizKeyAndDelYn}.
     * Returns a map keyed by {@code apprvrId}; missing approvers map
     * to {@code null} so the row builder ships a null URL and the
     * frontend's existing {@code <Avatar>} fallback renders the icon.
     */
    private Map<String, String> resolveImageUrls(String bizKey, List<String> apprvrIds) {
        if (apprvrIds.isEmpty()) return Map.of();
        Set<String> distinct = new HashSet<>(apprvrIds);
        List<UserBusiConnInfm> links = userBusiRepo
                .findAllByUserIdInAndBizKeyAndDelYn(distinct, bizKey, FIND_DEL_YN);
        Map<String, String> out = new HashMap<>(links.size());
        for (UserBusiConnInfm link : links) {
            String image = link.getUserImg();
            if (image != null && !image.isBlank()) {
                out.put(link.getUserId(), String.format(USER_IMAGE_URI_TEMPLATE, link.getUserId()));
            }
        }
        return out;
    }

    private static ApprovalLineRow toRow(
            ApprovalLine line,
            List<ApprovalLineApprover> approvers,
            Map<String, String> imgUrlByApprvrId) {
        List<ApprovalLineApproverRow> approverRows = approvers.stream()
                .map(a -> new ApprovalLineApproverRow(
                        a.getApprvrDispOrd(),
                        a.getApprvrId(),
                        a.getApprvrNm(),
                        a.getApprvrPosi(),
                        a.getApprvrDept(),
                        imgUrlByApprvrId.get(a.getApprvrId())))
                .toList();
        return new ApprovalLineRow(
                line.getApprLineNo(),
                line.getApprLineNm(),
                line.getRegDtm(),
                approverRows);
    }

    /* ---------------------------------------------------------------
     *  Tiny utilities
     * --------------------------------------------------------------- */

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            // Should never happen — the JWT filter populates the
            // TenantContext on every authenticated request. An empty
            // bizKey here means an unauthenticated path leaked into the
            // service layer — a configuration bug.
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

    private static String requireApprLineNm(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("WBE_APPROVAL_LINE_NAME_BLANK");
        }
        return value.trim();
    }

    private static String utcNow14() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FORMAT);
    }

    private static String coalesce(String value) {
        return value == null ? EMPTY : value;
    }

    private static Long parseApprLineNo(String generated) {
        if (generated == null || generated.isBlank()) {
            // The legacy function should never return null for a valid
            // (table, field, biz_key) triple — this would indicate a
            // local DB migration drift. Surface as a server error so
            // the trace shows up in logs.
            throw new BusinessException("WBE9999");
        }
        return Long.parseLong(generated.trim());
    }
}
