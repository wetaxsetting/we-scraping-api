package com.kosign.wecms.domain.banking.smartorganizer.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.smartorganizer.dto.OrganizerRuleListResponse;
import com.kosign.wecms.domain.banking.smartorganizer.dto.OrganizerRuleReorderRequest;
import com.kosign.wecms.domain.banking.smartorganizer.dto.OrganizerRuleRow;
import com.kosign.wecms.domain.banking.smartorganizer.dto.OrganizerRuleSaveRequest;
import com.kosign.wecms.domain.banking.smartorganizer.entity.RmrkOrgInfm;
import com.kosign.wecms.domain.banking.smartorganizer.repository.RmrkOrgInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings &gt; Smart Organizer rules — CRUD + reorder. Drives the
 * Organizer Settings popup (legacy {@code fnsh_0200_002}). Tenant
 * scoped via {@link TenantContext#currentBizKey()}.
 */
@Service
@RequiredArgsConstructor
public class OrganizerRuleService {

    private static final DateTimeFormatter LEGACY_DTM =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RmrkOrgInfmRepository repo;
    private final LegacyFunctionRepository legacy;

    /** List every rule for the current tenant, ordered by DISP_ORD ASC. */
    @Transactional(readOnly = true)
    public OrganizerRuleListResponse list() {
        String bizKey = requireBizKey();
        List<RmrkOrgInfm> entities = repo.findByBizKeyOrderByDispOrdAscRmrkSeqNoAsc(bizKey);
        List<OrganizerRuleRow> rows = entities.stream().map(OrganizerRuleService::toRow).toList();
        return new OrganizerRuleListResponse(rows.size(), rows);
    }

    /**
     * Create a new rule. Allocates {@code RMRK_SEQ_NO} via the V161
     * {@code wb_get_next_val} branch; assigns {@code DISP_ORD =
     * MAX+1} per tenant when the caller omitted the value.
     */
    @Transactional
    public OrganizerRuleRow create(OrganizerRuleSaveRequest req) {
        String bizKey = requireBizKey();
        String userId = TenantContext.currentUserId();

        long nextSeqNo = parseSeqNo(legacy.nextRmrkSeqNo(bizKey));

        RmrkOrgInfm e = new RmrkOrgInfm();
        e.setBizKey(bizKey);
        e.setRmrkSeqNo(nextSeqNo);
        e.setRmrk(req.rmrk().trim());
        e.setMatchType(req.matchType());
        e.setRcvPayDv(req.rcvPayDv());
        e.setDispOrd(req.dispOrd() != null ? req.dispOrd() : nextDispOrd(bizKey));
        e.setTrcoKey(blankToNull(req.trcoKey()));
        e.setUseUsagCd(blankToNull(req.useUsagCd()));
        e.setVasAcctSeqNo(req.vasAcctSeqNo());
        e.setChartAcctSeqNo(blankToNull(req.chartAcctSeqNo()));
        e.setChartAcctCd(blankToNull(req.chartAcctCd()));
        e.setRegId(userId);
        e.setRegDtm(nowYyyyMMddHHmmss());

        RmrkOrgInfm saved = repo.save(e);
        return toRow(saved);
    }

    /**
     * Update an existing rule. Only the fields carried by the request
     * are written — the {@code REG_ID} / {@code REG_DTM} pair is
     * preserved (legacy has no MOD_* columns on this table).
     */
    @Transactional
    public OrganizerRuleRow update(Long rmrkSeqNo, OrganizerRuleSaveRequest req) {
        String bizKey = requireBizKey();
        RmrkOrgInfm e = repo.findByBizKeyAndRmrkSeqNo(bizKey, rmrkSeqNo)
                .orElseThrow(() -> new BusinessException("WBE_SMART_ORGANIZER_RULE_NOT_FOUND"));

        e.setRmrk(req.rmrk().trim());
        e.setMatchType(req.matchType());
        e.setRcvPayDv(req.rcvPayDv());
        if (req.dispOrd() != null) e.setDispOrd(req.dispOrd());
        e.setTrcoKey(blankToNull(req.trcoKey()));
        e.setUseUsagCd(blankToNull(req.useUsagCd()));
        e.setVasAcctSeqNo(req.vasAcctSeqNo());
        e.setChartAcctSeqNo(blankToNull(req.chartAcctSeqNo()));
        e.setChartAcctCd(blankToNull(req.chartAcctCd()));
        return toRow(repo.save(e));
    }

    /** Hard-delete a rule from the current tenant. */
    @Transactional
    public void delete(Long rmrkSeqNo) {
        String bizKey = requireBizKey();
        int n = repo.deleteByBizKeyAndRmrkSeqNo(bizKey, rmrkSeqNo);
        if (n == 0) throw new BusinessException("WBE_SMART_ORGANIZER_RULE_NOT_FOUND");
    }

    /**
     * Bulk reorder — applies the user-chosen {@code DISP_ORD} values
     * to each rule by surrogate. Mirrors legacy
     * {@code fnsh_0200_002_u001}. Every {@code rmrkSeqNo} in the
     * payload must belong to the current tenant.
     */
    @Transactional
    public void reorder(OrganizerRuleReorderRequest req) {
        String bizKey = requireBizKey();
        Set<Long> seen = new HashSet<>();
        for (OrganizerRuleReorderRequest.Entry entry : req.rows()) {
            if (!seen.add(entry.rmrkSeqNo())) {
                throw new BusinessException("WBE_SMART_ORGANIZER_RULE_DUPLICATE_REORDER");
            }
            int n = repo.updateDispOrd(bizKey, entry.rmrkSeqNo(), entry.dispOrd());
            if (n == 0) {
                throw new BusinessException("WBE_SMART_ORGANIZER_RULE_NOT_FOUND");
            }
        }
    }

    // ---- helpers ----------------------------------------------------

    private int nextDispOrd(String bizKey) {
        List<RmrkOrgInfm> existing = repo.findByBizKeyOrderByDispOrdAscRmrkSeqNoAsc(bizKey);
        int max = 0;
        for (RmrkOrgInfm e : existing) {
            if (e.getDispOrd() != null && e.getDispOrd() > max) max = e.getDispOrd();
        }
        return max + 1;
    }

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE1009");
        }
        return bizKey;
    }

    private static long parseSeqNo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("WBE9999");
        }
        return Long.parseLong(raw.trim());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String nowYyyyMMddHHmmss() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(LEGACY_DTM);
    }

    static OrganizerRuleRow toRow(RmrkOrgInfm e) {
        return new OrganizerRuleRow(
                e.getRmrkSeqNo(),
                e.getRcvPayDv(),
                e.getRmrk(),
                e.getMatchType(),
                e.getDispOrd(),
                e.getTrcoKey(),
                e.getUseUsagCd(),
                e.getVasAcctSeqNo(),
                e.getChartAcctSeqNo(),
                e.getChartAcctCd(),
                e.getRegId(),
                e.getRegDtm()
        );
    }
}
