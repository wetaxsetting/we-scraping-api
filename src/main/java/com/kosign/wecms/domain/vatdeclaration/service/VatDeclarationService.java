package com.kosign.wecms.domain.vatdeclaration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationCreateRequest;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationDetail;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationListResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationRow;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationUpdateRequest;
import com.kosign.wecms.domain.vatdeclaration.dto.VatIndicatorSet;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationIndicator;
import com.kosign.wecms.domain.vatdeclaration.repository.VatDeclarationIndicatorRepository;
import com.kosign.wecms.domain.vatdeclaration.repository.VatDeclarationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * VAT Declaration (Mẫu 01/GTGT TT80) — list / create / detail / save-draft /
 * delete / lock / unlock over {@code TAX_DECLARATIONS} +
 * {@code TAX_DECLARATION_ITEMS}. Spec:
 * docs/features/addon-service/vat-declaration/interface-specification.md.
 *
 * <p>Invoice auto-aggregation (sync) and the annex/export legs are pending
 * PO decisions (OQ-VAT-3/4/9) and land in a follow-up MR.
 */
@Service
@RequiredArgsConstructor
public class VatDeclarationService {

    static final String STATUS_DRAFT  = "DRAFT";
    static final String STATUS_LOCKED = "LOCKED";

    private static final String PERIOD_MONTH   = "month";
    private static final String PERIOD_QUARTER = "quarter";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE     = 100;

    private static final String DEFAULT_FORM_CODE    = "842";
    private static final String DEFAULT_XML_VERSION  = "2.6.8";
    private static final String DEFAULT_ACCT_ITEM    = "1701";
    private static final String DEFAULT_ACTIVITY_CD  = "0";
    private static final String DEFAULT_ACTIVITY_NM  = "Hoạt động sản xuất kinh doanh thông thường";

    /** Dashboard display labels (form 01/GTGT under Circular 80/2021/TT-BTC). */
    private static final String APPLICABLE_CIRCULAR = "80/2021/TT-BTC";
    private static final String DECL_FORM_NAME      = "01/GTGT";

    private final VatDeclarationRepository declRepo;
    private final VatDeclarationIndicatorRepository indicatorRepo;
    private final BusiInfmRepository busiInfmRepo;
    private final VatIndicatorCalculator calculator;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public VatDeclarationListResponse list(Integer year, String status, int page, int size) {
        String bizKey = TenantContext.currentBizKey();
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Page<VatDeclaration> result = declRepo.search(
                bizKey, year, normalizeStatusFilter(status),
                PageRequest.of(Math.max(page, 0), pageSize));

        List<Long> sids = result.getContent().stream().map(VatDeclaration::getSid).toList();
        Map<Long, VatDeclarationIndicator> bySid = sids.isEmpty() ? Map.of()
                : indicatorRepo.findByDeclaration_SidInAndDelYn(sids, "N").stream()
                        .collect(java.util.stream.Collectors.toMap(
                                i -> i.getDeclaration().getSid(), i -> i, (a, b) -> a));

        List<VatDeclarationRow> rows = result.getContent().stream()
                .map(d -> toRow(d, bySid.get(d.getSid())))
                .toList();
        return new VatDeclarationListResponse(rows, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public VatDeclarationDetail detail(Long sid) {
        VatDeclaration decl = load(sid);
        return toDetail(decl, loadIndicator(decl));
    }

    @Transactional
    public VatDeclarationDetail create(VatDeclarationCreateRequest req) {
        String bizKey = TenantContext.currentBizKey();
        String periodType = toDbPeriodType(req.periodType());
        validatePeriodValue(req.periodType(), req.periodValue());

        if (declRepo.existsByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndSupplNoAndDelYn(
                bizKey, periodType, req.periodValue(), req.declYear(), 0, "N")) {
            throw new BusinessException("WBE_VAT_DECL_DUPLICATE_PERIOD");
        }

        VatDeclaration previous = findAdjacent(bizKey, req.periodType(), req.periodValue(), req.declYear(), -1);
        if (previous != null && !STATUS_LOCKED.equals(previous.getStatus())) {
            throw new BusinessException("WBE_VAT_DECL_PREV_NOT_LOCKED");
        }
        BigDecimal carryOver = previous == null ? BigDecimal.ZERO
                : nz(loadIndicator(previous).getCreditNext());

        BusiInfm company = busiInfmRepo.findById(bizKey)
                .orElseThrow(() -> new BusinessException("WBE_VAT_DECL_NOT_FOUND"));

        LocalDate[] range = periodRange(req.periodType(), req.periodValue(), req.declYear());
        VatDeclaration decl = VatDeclaration.builder()
                .bizKey(bizKey)
                .periodType(periodType)
                .periodValue(req.periodValue())
                .periodYear(req.declYear())
                .formCode(DEFAULT_FORM_CODE)
                .xmlVer(DEFAULT_XML_VERSION)
                .activityName(defaultIfBlank(req.bsnsLineNm(), DEFAULT_ACTIVITY_NM))
                .activityCode(defaultIfBlank(req.bsnsLineCd(), DEFAULT_ACTIVITY_CD))
                .acctItem(DEFAULT_ACCT_ITEM)
                .period(periodLabel(req.periodType(), req.periodValue(), req.declYear()))
                .supplNo(0)
                .declType("C")
                .tpName(company.getCmpnNm())
                .tpTaxCode(defaultIfBlank(company.getTaxId(), ""))
                .tpProv("")
                .periodKind(req.periodType())
                .fromDt(range[0])
                .toDt(range[1])
                .officeCode(defaultIfBlank(req.officeCode(), ""))
                .officeName(defaultIfBlank(req.officeName(), ""))
                .declDt(LocalDate.now())
                .indicators("{}")
                .delYn("N")
                .status(STATUS_DRAFT)
                .annexBkbrYn(yn(req.annexBkbrYn(), true))       // 01-1/GTGT default ticked
                .annexBkmvYn(yn(req.annexBkmvYn(), true))       // 01-2/GTGT default ticked
                .annexAllocYn(yn(req.annexAllocYn(), false))    // 01-6/GTGT off by default
                .annexReduce8Yn(yn(req.annexReduce8Yn(), false))// 8% reduction off by default
                .build();
        stampAudit(decl);
        decl = declRepo.save(decl);

        VatDeclarationIndicator ind = VatDeclarationIndicator.builder()
                .declaration(decl)
                .prevCredit(carryOver)
                .delYn("N")
                .build();
        stampAudit(ind);
        calculator.recompute(ind);
        indicatorRepo.save(ind);

        decl.setIndicators(snapshotJson(ind));
        return toDetail(decl, ind);
    }

    @Transactional
    public VatDeclarationDetail update(Long sid, VatDeclarationUpdateRequest req) {
        VatDeclaration decl = load(sid);
        assertDraft(decl);
        VatDeclarationIndicator ind = loadIndicator(decl);

        apply(req.ct21(),  "ct21",  ind::setNoActivity);
        apply(req.ct23(),  "ct23",  ind::setPurchAmt);
        apply(req.ct24(),  "ct24",  ind::setPurchAmtVat);
        apply(req.ct23a(), "ct23a", ind::setImpAmt);
        apply(req.ct24a(), "ct24a", ind::setImpAmtVat);
        apply(req.ct26(),  "ct26",  ind::setSalesAmtNondecl);
        apply(req.ct29(),  "ct29",  ind::setSalesAmt0);
        apply(req.ct30(),  "ct30",  ind::setSalesAmt5);
        apply(req.ct32(),  "ct32",  ind::setSalesAmt10);
        apply(req.ct32a(), "ct32a", ind::setSalesAmtNontax);
        apply(req.ct33a(), "ct33a", ind::setSalesVatOther);
        apply(req.ct37(),  "ct37",  ind::setAdjDecrease);
        apply(req.ct38(),  "ct38",  ind::setAdjIncrease);
        apply(req.ct39a(), "ct39a", ind::setTransfVat);
        apply(req.ct40b(), "ct40b", ind::setInvestOffsetVatAmt);
        apply(req.ct42(),  "ct42",  ind::setVatRefundReq);

        calculator.recompute(ind);
        ind.setModId(TenantContext.currentUserId());
        decl.setIndicators(snapshotJson(ind));
        decl.setModId(TenantContext.currentUserId());
        return toDetail(decl, ind);
    }

    @Transactional
    public void delete(Long sid) {
        VatDeclaration decl = load(sid);
        assertDraft(decl);
        indicatorRepo.findByDeclaration_SidAndDeclaration_BizKeyAndDelYn(
                decl.getSid(), decl.getBizKey(), "N").ifPresent(i -> {
            i.setDelYn("Y");
            i.setModId(TenantContext.currentUserId());
        });
        decl.setDelYn("Y");
        decl.setModId(TenantContext.currentUserId());
    }

    @Transactional
    public VatDeclarationDetail lock(Long sid) {
        VatDeclaration decl = load(sid);
        assertDraft(decl);
        VatDeclaration previous = findAdjacent(decl.getBizKey(),
                toWirePeriodType(decl.getPeriodType()), decl.getPeriodValue(), decl.getPeriodYear(), -1);
        if (previous != null && !STATUS_LOCKED.equals(previous.getStatus())) {
            throw new BusinessException("WBE_VAT_DECL_PREV_NOT_LOCKED");
        }
        decl.setStatus(STATUS_LOCKED);
        decl.setModId(TenantContext.currentUserId());
        return toDetail(decl, loadIndicator(decl));
    }

    @Transactional
    public VatDeclarationDetail unlock(Long sid) {
        VatDeclaration decl = load(sid);
        if (!STATUS_LOCKED.equals(decl.getStatus())) {
            return toDetail(decl, loadIndicator(decl));
        }
        VatDeclaration next = findAdjacent(decl.getBizKey(),
                toWirePeriodType(decl.getPeriodType()), decl.getPeriodValue(), decl.getPeriodYear(), +1);
        if (next != null && STATUS_LOCKED.equals(next.getStatus())) {
            throw new BusinessException("WBE_VAT_DECL_NEXT_LOCKED");
        }
        decl.setStatus(STATUS_DRAFT);
        decl.setModId(TenantContext.currentUserId());
        return toDetail(decl, loadIndicator(decl));
    }

    // ---------------------------------------------------------------- helpers

    private VatDeclaration load(Long sid) {
        return declRepo.findBySidAndBizKeyAndDelYn(sid, TenantContext.currentBizKey(), "N")
                .orElseThrow(() -> new BusinessException("WBE_VAT_DECL_NOT_FOUND"));
    }

    private VatDeclarationIndicator loadIndicator(VatDeclaration decl) {
        return indicatorRepo.findByDeclaration_SidAndDeclaration_BizKeyAndDelYn(
                        decl.getSid(), decl.getBizKey(), "N")
                .orElseThrow(() -> new BusinessException("WBE_VAT_DECL_NOT_FOUND"));
    }

    private static void assertDraft(VatDeclaration decl) {
        if (!STATUS_DRAFT.equals(decl.getStatus())) {
            throw new BusinessException("WBE_VAT_DECL_LOCKED");
        }
    }

    /** offset −1 = period K-1, +1 = period K+1; null when none exists. */
    private VatDeclaration findAdjacent(String bizKey, String wireType, int value, int year, int offset) {
        int cycle = "M".equals(wireType) ? 12 : 4;
        int v = value + offset;
        int y = year;
        if (v < 1)      { v = cycle; y--; }
        if (v > cycle)  { v = 1;     y++; }
        return declRepo.findTopByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndDelYnOrderBySupplNoDesc(
                bizKey, toDbPeriodType(wireType), v, y, "N").orElse(null);
    }

    private static void validatePeriodValue(String wireType, int value) {
        int max = "M".equals(wireType) ? 12 : 4;
        if (value < 1 || value > max) {
            throw new BusinessException("WBE_VALIDATION");
        }
    }

    private static String toDbPeriodType(String wireType) {
        return "M".equals(wireType) ? PERIOD_MONTH : PERIOD_QUARTER;
    }

    private static String toWirePeriodType(String dbType) {
        return PERIOD_MONTH.equals(dbType) ? "M" : "Q";
    }

    private static String periodLabel(String wireType, int value, int year) {
        return "M".equals(wireType)
                ? String.format("%02d/%d", value, year)
                : String.format("Q%d/%d", value, year);
    }

    private static LocalDate[] periodRange(String wireType, int value, int year) {
        if ("M".equals(wireType)) {
            LocalDate from = LocalDate.of(year, value, 1);
            return new LocalDate[]{from, from.plusMonths(1).minusDays(1)};
        }
        LocalDate from = LocalDate.of(year, (value - 1) * 3 + 1, 1);
        return new LocalDate[]{from, from.plusMonths(3).minusDays(1)};
    }

    private static String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) return null;
        return switch (status.trim().toUpperCase()) {
            case "D", "DRAFT"  -> STATUS_DRAFT;
            case "C", "LOCKED" -> STATUS_LOCKED;
            default -> throw new BusinessException("WBE_VALIDATION");
        };
    }

    private void apply(String raw, String field, Consumer<BigDecimal> setter) {
        if (raw == null) return;
        setter.accept(parseAmount(raw, field));
    }

    /** Integer-VND string → BigDecimal; negative / non-integer → business error. */
    private static BigDecimal parseAmount(String raw, String field) {
        try {
            BigDecimal v = new BigDecimal(raw.trim());
            if (v.signum() < 0 || v.stripTrailingZeros().scale() > 0) {
                throw new BusinessException("WBE_VAT_DECL_INVALID_INDICATOR", field);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new BusinessException("WBE_VAT_DECL_INVALID_INDICATOR", field);
        }
    }

    private void stampAudit(Object entity) {
        String userId = TenantContext.currentUserId();
        if (entity instanceof VatDeclaration d) {
            d.setRegId(userId);
            d.setModId(userId);
        } else if (entity instanceof VatDeclarationIndicator i) {
            i.setRegId(userId);
            i.setModId(userId);
        }
    }

    /** Serializes the ct block into the header's {@code indicators} JSONB snapshot. */
    private String snapshotJson(VatDeclarationIndicator ind) {
        Map<String, Object> m = new LinkedHashMap<>();
        VatIndicatorSet set = toIndicatorSet(ind);
        m.put("21", set.ct21());   m.put("22", set.ct22());   m.put("23", set.ct23());
        m.put("24", set.ct24());   m.put("23a", set.ct23a()); m.put("24a", set.ct24a());
        m.put("25", set.ct25());   m.put("26", set.ct26());   m.put("27", set.ct27());
        m.put("28", set.ct28());   m.put("29", set.ct29());   m.put("30", set.ct30());
        m.put("31", set.ct31());   m.put("32", set.ct32());   m.put("32a", set.ct32a());
        m.put("33", set.ct33());   m.put("33a", set.ct33a()); m.put("34", set.ct34());
        m.put("35", set.ct35());   m.put("36", set.ct36());   m.put("37", set.ct37());
        m.put("38", set.ct38());   m.put("39a", set.ct39a()); m.put("40a", set.ct40a());
        m.put("40b", set.ct40b()); m.put("40", set.ct40());   m.put("41", set.ct41());
        m.put("42", set.ct42());   m.put("43", set.ct43());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("indicator snapshot serialization failed", e);
        }
    }

    private VatDeclarationRow toRow(VatDeclaration d, VatDeclarationIndicator ind) {
        String wireType = toWirePeriodType(d.getPeriodType());
        return new VatDeclarationRow(
                d.getSid(),
                wireType,
                d.getPeriodValue(),
                d.getPeriodYear(),
                periodLabel(wireType, d.getPeriodValue(), d.getPeriodYear()),
                d.getSupplNo(),
                d.getDeclType(),
                APPLICABLE_CIRCULAR,
                DECL_FORM_NAME,
                null,                                            // e-transaction code: no submission flow yet (Phase 2) → FE shows "Not submitted"
                d.getStatus(),
                ind == null ? "0" : str(ind.getPrevCredit()),   // ct22
                ind == null ? "0" : str(ind.getVatPayableNet()),// ct40
                ind == null ? "0" : str(ind.getCreditNext()),   // ct43
                d.getModDtm() == null ? null : d.getModDtm().toString());
    }

    private VatDeclarationDetail toDetail(VatDeclaration d, VatDeclarationIndicator ind) {
        return new VatDeclarationDetail(
                d.getSid(),
                toWirePeriodType(d.getPeriodType()),
                d.getPeriodValue(),
                d.getPeriodYear(),
                periodLabel(toWirePeriodType(d.getPeriodType()), d.getPeriodValue(), d.getPeriodYear()),
                d.getSupplNo(),
                d.getDeclType(),
                d.getStatus(),
                d.getTpName(),
                d.getTpTaxCode(),
                d.getActivityCode(),
                d.getActivityName(),
                d.getFromDt() == null ? null : d.getFromDt().toString(),
                d.getToDt() == null ? null : d.getToDt().toString(),
                d.getDeclDt() == null ? null : d.getDeclDt().toString(),
                toIndicatorSet(ind));
    }

    private static VatIndicatorSet toIndicatorSet(VatDeclarationIndicator i) {
        return new VatIndicatorSet(
                str(i.getNoActivity()), str(i.getPrevCredit()),
                str(i.getPurchAmt()), str(i.getPurchAmtVat()),
                str(i.getImpAmt()), str(i.getImpAmtVat()),
                str(i.getDeductVatAmt()), str(i.getSalesAmtNondecl()),
                str(i.getTolSalesAmt()), str(i.getTotSalesAmtVat()),
                str(i.getSalesAmt0()), str(i.getSalesAmt5()), str(i.getSalesAmtVat5()),
                str(i.getSalesAmt10()), str(i.getSalesAmtNontax()),
                str(i.getSalesAmtVat10()), str(i.getSalesVatOther()),
                str(i.getTotalSalesAmt()), str(i.getTotalSalesAmtVat()),
                str(i.getVatIncurred()), str(i.getAdjDecrease()), str(i.getAdjIncrease()),
                str(i.getTransfVat()), str(i.getVatPayableActivity()),
                str(i.getInvestOffsetVatAmt()), str(i.getVatPayableNet()),
                str(i.getVatNotDeducted()), str(i.getVatRefundReq()),
                str(i.getCreditNext()));
    }

    private static String str(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String defaultIfBlank(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    /** Normalise a wire Y/N annex flag; null/blank → the per-annex default. */
    private static String yn(String raw, boolean defaultOn) {
        if (raw == null || raw.isBlank()) {
            return defaultOn ? "Y" : "N";
        }
        return "Y".equalsIgnoreCase(raw.trim()) ? "Y" : "N";
    }
}
