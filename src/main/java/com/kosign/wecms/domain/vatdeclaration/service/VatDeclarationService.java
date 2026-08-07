package com.kosign.wecms.domain.vatdeclaration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationCreateRequest;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationDetail;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationListResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationRow;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationUpdateRequest;
import com.kosign.wecms.domain.vatdeclaration.dto.VatIndicatorSet;
import com.kosign.wecms.domain.vatdeclaration.dto.AnnexInvoiceListResponse;
import com.kosign.wecms.domain.vatdeclaration.dto.AnnexInvoiceProjection;
import com.kosign.wecms.domain.vatdeclaration.dto.AnnexRow;
import com.kosign.wecms.domain.vatdeclaration.dto.AnnexTaxGroup;
import com.kosign.wecms.domain.vatdeclaration.export.VatExportFile;
import com.kosign.wecms.domain.vatdeclaration.export.VatPdfExporter;
import com.kosign.wecms.domain.vatdeclaration.export.VatXmlExporter;
import com.kosign.wecms.domain.vatdeclaration.repository.AnnexTaxGroupProjection;
import com.kosign.wecms.domain.vatdeclaration.repository.VatSyncBucketProjection;
import com.kosign.wecms.domain.vatdeclaration.entity.AnnexType;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationIndicator;
import com.kosign.wecms.domain.vatdeclaration.repository.VatAnnexInvoiceRepository;
import com.kosign.wecms.domain.vatdeclaration.repository.VatDeclarationIndicatorRepository;
import com.kosign.wecms.domain.vatdeclaration.repository.VatDeclarationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * VAT Declaration (Mẫu 01/GTGT TT80) — list / create / detail / save-draft /
 * delete / lock / unlock over {@code VAT_DECL} +
 * {@code VAT_DECL_ITEMS}. Spec:
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

    /**
     * BUSA_DOC_MSTR.DOC_TYPE — output (sales) vs input (purchase) invoices.
     *
     * <p>These two are the ONLY types a declaration reads. {@code 'E'} is
     * deliberately out of scope (decision 2026-07-24): the rows carry an
     * {@code R-} document id, {@code HAND_REG_YN='Y'}, the tenant itself as
     * counterparty, a NULL {@code EINV_INVOICESTATUS}, and — decisively — zero
     * {@code BUSA_DOC_DETL} lines, so they could not contribute an annex line
     * even if they were let through. Excluding them here makes that explicit
     * rather than an accident of the join.
     */
    private static final String DOC_TYPE_SALES    = "I";
    private static final String DOC_TYPE_PURCHASE = "B";
    /** EINV_TAXRATE value carrying the Resolution 204/2025/QH15 reduction. */
    private static final String RATE_8_PCT = "8%";

    /**
     * Invoice statuses that must NOT feed a declaration (PO decision 2026-07-24).
     *
     * <p>A <b>replacement</b> invoice voids its original outright, so the
     * superseded row would double-count — it is dropped. A <b>cancelled</b>
     * invoice never had legal effect. An <b>adjustment</b> pair is deliberately
     * NOT excluded: the adjustment records only the delta, so the original must
     * stay counted for the two to add up.
     *
     * <p>{@code EINV_INVOICESTATUS} stores the localized Vietnamese label rather
     * than a code (see CLAUDE.md BLOCKERS), so these strings are the contract.
     * They are pinned here — never inlined into SQL — and match the frontend's
     * canonical registry verbatim (wecms-web/lib/api/einvoiceStatus.ts).
     */
    private static final List<String> EXCLUDED_INVOICE_STATUSES = List.of(
            "Hóa đơn đã bị thay thế",   // replaced — superseded by a replacement
            "Hóa đơn đã bị hủy");       // cancelled
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Dashboard display labels (form 01/GTGT under Circular 80/2021/TT-BTC). */
    private static final String APPLICABLE_CIRCULAR = "80/2021/TT-BTC";
    private static final String DECL_FORM_NAME      = "01/GTGT";

    private final VatDeclarationRepository declRepo;
    private final VatDeclarationIndicatorRepository indicatorRepo;
    private final VatAnnexInvoiceRepository annexInvoiceRepo;
    private final BusiInfmRepository busiInfmRepo;
    private final VatIndicatorCalculator calculator;
    private final ObjectMapper objectMapper;
    private final VatXmlExporter xmlExporter;
    private final VatPdfExporter pdfExporter;

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
        // "Business Industry *" is required on the PO create form (Dictionary
        // WEB_VAT_CRE_006) and ships its own message (WEB_VAT_CRE_MSG_004), so it
        // is checked here rather than with @NotBlank — bean validation would
        // collapse it into the generic WBE_VALIDATION and lose the PO wording.
        if (blankToNull(req.bsnsLineCd()) == null) {
            throw new BusinessException("WBE_VAT_DECL_BSNS_LINE_REQUIRED");
        }

        if (declRepo.existsByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndSupplNoAndDelYn(
                bizKey, periodType, req.periodValue(), req.declYear(), 0, "N")) {
            throw new BusinessException("WBE_VAT_DECL_DUPLICATE_PERIOD");
        }

        // Contiguity guard (PO 2026-08-06): declarations must be created in order.
        // K may be created only when K-1 exists AND is LOCKED — or when K is the
        // tenant's very first period. No skipping (can't create M3 while M2 is
        // absent), and the immediately-preceding period must be closed first.
        VatDeclaration previous = findAdjacent(bizKey, req.periodType(), req.periodValue(), req.declYear(), -1);
        if (previous == null) {
            if (declRepo.existsEarlierPeriod(bizKey, periodType, req.declYear(), req.periodValue())) {
                throw new BusinessException("WBE_VAT_DECL_PREV_MISSING",
                        periodLabel(req.periodType(), req.periodValue(), req.declYear()));
            }
        } else if (!STATUS_LOCKED.equals(previous.getStatus())) {
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
                .annexAllocYn(yn(req.annexAllocYn(), false))    // 01-6/GTGT CSSX off by default
                .annexAllocTeleYn(yn(req.annexAllocTeleYn(), false)) // 01-6/GTGT telecom off
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
        aggregateInvoicesInto(decl, ind);   // pre-fill the period's e-invoice figures on create
        indicatorRepo.save(ind);

        decl.setIndicators(snapshotJson(ind));
        return toDetail(decl, ind);
    }

    @Transactional
    public VatDeclarationDetail update(Long sid, VatDeclarationUpdateRequest req) {
        VatDeclaration decl = load(sid);
        assertDraft(decl);
        VatDeclarationIndicator ind = loadIndicator(decl);

        // Which ct-keys the client actually sent — the calculator needs this to
        // tell "omitted, derive it" from "user overrode the derived value".
        Set<String> sent = new HashSet<>();
        applyFlag(sent, req.ct21(), "ct21", ind::setNoActivity);
        apply(sent, req.ct22(),  "ct22",  ind::setPrevCredit);
        apply(sent, req.ct23(),  "ct23",  ind::setPurchAmt);
        apply(sent, req.ct24(),  "ct24",  ind::setPurchAmtVat);
        apply(sent, req.ct23a(), "ct23a", ind::setImpAmt);
        apply(sent, req.ct24a(), "ct24a", ind::setImpAmtVat);
        apply(sent, req.ct25(),  "ct25",  ind::setDeductVatAmt);
        apply(sent, req.ct26(),  "ct26",  ind::setSalesAmtNondecl);
        apply(sent, req.ct29(),  "ct29",  ind::setSalesAmt0);
        apply(sent, req.ct30(),  "ct30",  ind::setSalesAmt5);
        apply(sent, req.ct31(),  "ct31",  ind::setSalesAmtVat5);
        apply(sent, req.ct32(),  "ct32",  ind::setSalesAmt10);
        apply(sent, req.ct32a(), "ct32a", ind::setSalesAmtNontax);
        apply(sent, req.ct33(),  "ct33",  ind::setSalesAmtVat10);
        apply(sent, req.ct33a(), "ct33a", ind::setSalesVatOther);
        apply(sent, req.ct37(),  "ct37",  ind::setAdjDecrease);
        apply(sent, req.ct38(),  "ct38",  ind::setAdjIncrease);
        apply(sent, req.ct39a(), "ct39a", ind::setTransfVat);
        apply(sent, req.ct40b(), "ct40b", ind::setInvestOffsetVatAmt);
        apply(sent, req.ct42(),  "ct42",  ind::setVatRefundReq);
        apply(sent, req.ct42a(), "ct42a", ind::setVatRefundReq1331);
        apply(sent, req.ct42b(), "ct42b", ind::setVatRefundReq1332);

        // Header [06]–[11A] — tax agent + dependent unit. null = leave unchanged
        // (Save Draft posts the whole form; a partial payload keeps prior values).
        if (req.agentNm()      != null) decl.setAgentName(blankToNull(req.agentNm()));
        if (req.agentTaxCode() != null) decl.setAgentTaxCode(blankToNull(req.agentTaxCode()));
        if (req.agentCtrNo()   != null) decl.setAgentCtrNo(blankToNull(req.agentCtrNo()));
        if (req.agentCtrDt()   != null) decl.setAgentCtrDt(parseDate(req.agentCtrDt()));
        if (req.duNm()         != null) decl.setDuName(blankToNull(req.duNm()));
        if (req.duTaxCode()    != null) decl.setDuTaxCode(blankToNull(req.duTaxCode()));
        if (req.duAddr()       != null) decl.setDuAddr(blankToNull(req.duAddr()));
        if (req.duProv()       != null) decl.setDuProv(blankToNull(req.duProv()));
        if (req.duDist()       != null) decl.setDuDist(blankToNull(req.duDist()));
        if (req.duWard()       != null) decl.setDuWard(blankToNull(req.duWard()));

        calculator.recompute(ind, sent);
        ind.setModId(TenantContext.currentUserId());
        decl.setIndicators(snapshotJson(ind));
        decl.setModId(TenantContext.currentUserId());
        return toDetail(decl, ind);
    }

    /** Wire {@code yyyy-MM-dd} → LocalDate; blank → null; invalid → WBE_VALIDATION. */
    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException("WBE_VALIDATION");
        }
    }

    /**
     * Soft-deletes a Draft declaration.
     *
     * <p>OQ-VAT-6: refused while ANY later period exists — not just a locked one.
     * K+1 carries a [22] taken from K's [43]; drop K and that figure references a
     * period that is gone, and K+1 can never be locked afterwards because the
     * chain check would see a hole. Delete the tail first, newest to oldest.
     */
    @Transactional
    public void delete(Long sid) {
        VatDeclaration decl = load(sid);
        assertDraft(decl);
        if (declRepo.existsLaterPeriod(decl.getBizKey(), decl.getPeriodType(),
                decl.getPeriodYear(), decl.getPeriodValue())) {
            throw new BusinessException("WBE_VAT_DECL_LATER_PERIOD_EXISTS",
                    periodLabel(toWirePeriodType(decl.getPeriodType()),
                            decl.getPeriodValue(), decl.getPeriodYear()));
        }
        // Defensive: a Draft row should hold no invoice stamps (lock sets them,
        // unlock clears them), but never leave markers pointing at a dead row.
        annexInvoiceRepo.clearProcessed(decl.getBizKey(), decl.getSid());
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
        if (STATUS_LOCKED.equals(decl.getStatus())) {
            return toDetail(decl, loadIndicator(decl));   // idempotent toggle
        }
        VatDeclarationIndicator ind = loadIndicator(decl);
        assertPreviousPeriodClosed(decl, ind);

        decl.setStatus(STATUS_LOCKED);
        decl.setModId(TenantContext.currentUserId());
        stampInvoicesProcessed(decl);
        return toDetail(decl, ind);
    }

    /**
     * Closing a declaration stamps the period's invoices as processed
     * ({@code VAT_DECL_PROC_YN='Y'} + {@code VAT_DECL_SID}) — PO decision
     * 2026-07-24, marker on the invoice row instead of a link table (V253).
     *
     * <p>What it buys: the reverse lookup "which declaration closed this
     * invoice", and — because a late-arriving invoice is inserted with the
     * default {@code 'N'} — the ability to list invoices that fall inside an
     * already-closed period but were never included, which is exactly the set
     * that needs a supplementary declaration (KHBS).
     *
     * <p>Both directions are stamped: an invoice is in the period's scope
     * whether it feeds BKBR (output) or BKMV (input).
     */
    private void stampInvoicesProcessed(VatDeclaration decl) {
        annexInvoiceRepo.markProcessed(decl.getBizKey(),
                decl.getFromDt().format(YYYYMMDD), decl.getToDt().format(YYYYMMDD),
                List.of(DOC_TYPE_SALES, DOC_TYPE_PURCHASE),
                EXCLUDED_INVOICE_STATUSES, decl.getSid());
    }

    /**
     * Lock walks BACKWARD one period: a declaration may only be closed once the
     * chain behind it is intact, because [22] of this period is [43] of the one
     * before it.
     *
     * <ol>
     *   <li>K-1 exists → it must be LOCKED
     *       ({@code WBE_VAT_DECL_PREV_NOT_LOCKED}).</li>
     *   <li>K-1 missing → the lock is ALLOWED (PO 2026-08-06). This is either the
     *       tenant's first declaration or a deliberate gap in filing — VAT periods
     *       need not be contiguous, so an older, non-adjacent declaration must NOT
     *       block a period whose immediate predecessor was never filed. Only the
     *       immediately-preceding period gates the lock.</li>
     *   <li>K-1 exists → this period's [22] must equal K-1's [43]. A drift means
     *       K-1 was re-opened and edited after K was created, so K's figures are
     *       stale ({@code WBE_VAT_DECL_PREV_CREDIT_MISMATCH}). Deliberately an
     *       error, not a silent re-sync: [22] is user-editable, and lock is the
     *       user asserting the figures are final — the server must not quietly
     *       change a number the user is signing off.</li>
     * </ol>
     */
    private void assertPreviousPeriodClosed(VatDeclaration decl, VatDeclarationIndicator ind) {
        String wireType = toWirePeriodType(decl.getPeriodType());
        VatDeclaration previous = findAdjacent(decl.getBizKey(), wireType,
                decl.getPeriodValue(), decl.getPeriodYear(), -1);

        if (previous == null) {
            // No immediately-preceding period on file -> nothing to carry forward,
            // so allow the lock. First declaration OR a deliberate filing gap; an
            // older, non-adjacent declaration must not block it (PO 2026-08-06).
            return;
        }
        if (!STATUS_LOCKED.equals(previous.getStatus())) {
            throw new BusinessException("WBE_VAT_DECL_PREV_NOT_LOCKED");
        }
        BigDecimal carried = nz(loadIndicator(previous).getCreditNext());   // K-1 [43]
        if (nz(ind.getPrevCredit()).compareTo(carried) != 0) {              // K   [22]
            throw new BusinessException("WBE_VAT_DECL_PREV_CREDIT_MISMATCH",
                    str(ind.getPrevCredit()), str(carried));
        }
    }

    @Transactional
    public VatDeclarationDetail unlock(Long sid) {
        VatDeclaration decl = load(sid);
        if (!STATUS_LOCKED.equals(decl.getStatus())) {
            return toDetail(decl, loadIndicator(decl));
        }
        // Unlock walks FORWARD: re-opening K lets its [43] change, which
        // invalidates the [22] of every later period — so no later period may
        // already be closed. Checking the whole tail, not just K+1, closes the
        // hole where K+1 is still Draft but K+2 was locked out of order.
        if (declRepo.existsLaterLockedPeriod(decl.getBizKey(), decl.getPeriodType(),
                decl.getPeriodYear(), decl.getPeriodValue(), STATUS_LOCKED)) {
            throw new BusinessException("WBE_VAT_DECL_NEXT_LOCKED");
        }
        decl.setStatus(STATUS_DRAFT);
        decl.setModId(TenantContext.currentUserId());
        // Re-opening releases the invoices it had claimed, so they are free to
        // be counted again by whichever declaration finally closes them.
        annexInvoiceRepo.clearProcessed(decl.getBizKey(), decl.getSid());
        return toDetail(decl, loadIndicator(decl));
    }

    /**
     * Re-aggregate the period's e-invoices into the invoice-source indicators
     * (API #5, {@code POST /{sid}/sync}). Draft only. Scans
     * {@code EINV_TAX_DETL ⋈ BUSA_DOC_MSTR} for the declaration's period,
     * excluding replaced / cancelled invoices, and distributes the per-rate
     * buckets into the source cells per the Business-Rules table:
     *
     * <ul>
     *   <li>purchases (DOC_TYPE 'B'): [23] += base, [24] += VAT (all rates)</li>
     *   <li>sales 0% → [29]; 5% → [30]/[31]; 8%+10% → [32]/[33]; KKKNT → [26];
     *       KCT → [32a] (mapping per the PO prototype {@code aggFor}, OQ-VAT-4)</li>
     * </ul>
     *
     * <p>Only the 9 invoice-source cells are overwritten; carry-forward [22] and
     * the manual adjustments [37]/[38]/[39a]/[40b]/[42] are preserved. Derived
     * cells are then recomputed — [31]/[33] are pinned to the real invoice VAT
     * (not the floor formula). The annex rows are already invoice-derived (live
     * read), so there is nothing to regenerate there.
     */
    @Transactional
    public VatDeclarationDetail sync(Long sid) {
        VatDeclaration decl = load(sid);
        assertDraft(decl);
        VatDeclarationIndicator ind = loadIndicator(decl);
        aggregateInvoicesInto(decl, ind);
        ind.setModId(TenantContext.currentUserId());
        decl.setIndicators(snapshotJson(ind));
        decl.setModId(TenantContext.currentUserId());
        return toDetail(decl, ind);
    }

    /**
     * Aggregate the period's e-invoices into the invoice-source indicators
     * ([23]/[24] purchase, [26]/[29]/[30]/[32]/[33]/[32a] sales) and recompute
     * the derived cells — [31]/[33] pinned to the real invoice VAT (not the
     * floor formula). Manual white-cell adjustments already on {@code ind}
     * ([37]/[38]/[39a]/[40b]/[42]) and the carried-forward [22] are preserved.
     * Shared by {@link #sync(Long)} (re-aggregate) and {@link #create} (initial
     * fill so a freshly-created draft already carries the period's figures).
     */
    private void aggregateInvoicesInto(VatDeclaration decl, VatDeclarationIndicator ind) {
        String from = decl.getFromDt().format(YYYYMMDD);
        String to = decl.getToDt().format(YYYYMMDD);

        BigDecimal ct23 = BigDecimal.ZERO, ct24 = BigDecimal.ZERO, ct26 = BigDecimal.ZERO,
                ct29 = BigDecimal.ZERO, ct30 = BigDecimal.ZERO, ct31 = BigDecimal.ZERO,
                ct32 = BigDecimal.ZERO, ct33 = BigDecimal.ZERO, ct32a = BigDecimal.ZERO;

        for (VatSyncBucketProjection b : annexInvoiceRepo.syncBuckets(
                decl.getBizKey(), from, to, List.of(DOC_TYPE_SALES, DOC_TYPE_PURCHASE),
                EXCLUDED_INVOICE_STATUSES)) {
            BigDecimal amt = nz(b.getAmt());
            BigDecimal tax = nz(b.getTaxAmt());
            if (DOC_TYPE_PURCHASE.equals(b.getDocType())) {
                ct23 = ct23.add(amt);   // [23] purchase base — all rates
                ct24 = ct24.add(tax);   // [24] purchase VAT
                continue;
            }
            switch (b.getTaxRate() == null ? "" : b.getTaxRate().trim()) {
                case "0%"        -> ct29 = ct29.add(amt);
                case "5%"        -> { ct30 = ct30.add(amt); ct31 = ct31.add(tax); }
                case "8%", "10%" -> { ct32 = ct32.add(amt); ct33 = ct33.add(tax); }
                case "KKKNT"     -> ct26 = ct26.add(amt);     // [26] not-subject sales (per PO prototype aggFor)
                case "KCT"       -> ct32a = ct32a.add(amt);   // [32a] not-tax-calculated sales (per PO prototype aggFor)
                default          -> { /* unknown / null rate → not mapped */ }
            }
        }

        ind.setPurchAmt(ct23);
        ind.setPurchAmtVat(ct24);
        ind.setSalesAmtNondecl(ct26);
        ind.setSalesAmt0(ct29);
        ind.setSalesAmt5(ct30);
        ind.setSalesAmtVat5(ct31);
        ind.setSalesAmt10(ct32);
        ind.setSalesAmtVat10(ct33);
        ind.setSalesAmtNontax(ct32a);

        calculator.recompute(ind, Set.of("ct31", "ct33"));
    }

    // ---------------------------------------------------------------- helpers

    // ---------------------------------------------------------------- annex----

    /**
     * Annex invoice modal (API #9) — rows are <b>derived from the period's real
     * invoices</b>, never from a stored annex table.
     *
     * <p>Scope comes from the declaration itself: {@code FROM_DT..TO_DT} over
     * {@code BUSA_DOC_MSTR.ISSU_DT}, plus the direction implied by the annex
     * type — BKBR reads output invoices ({@code DOC_TYPE 'I'}), BKMV reads input
     * ones ({@code 'B'}), REDUCE8 reads both but only the 8% lines. The two
     * 01-6/GTGT allocation annexes have no invoice-derived source and return
     * empty until their allocation rule is specified.
     *
     * <p>Flat paged list: {@code rows} + {@code total_count} / {@code page_number}
     * / {@code page_size} / {@code total_pages}, matching the shape the other
     * add-on list endpoints already use. The footer — per-VAT-rate
     * {@code taxGroups} + a {@code grandTotal} ("Total Output" row) — is folded
     * into this response (no separate /totals endpoint). One aggregate pass by
     * rate feeds the footer AND the paging total / reduction, so a large period
     * never loads every line into memory, the footer spans the FULL filtered set
     * (stable while paging), and the sections can never disagree with the rows.
     */
    @Transactional(readOnly = true)
    public AnnexInvoiceListResponse listAnnexInvoices(Long sid, String annexType, String search,
                                                      String ioDv, int page, int size) {
        VatDeclaration decl = load(sid);
        String type = resolveAnnexType(annexType);
        int pageNumber = Math.max(page, 0);
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        long offset = (long) pageNumber * pageSize;   // long: page is client input

        List<String> docTypes = docTypesFor(type, ioDv);
        if (docTypes.isEmpty()) {
            return new AnnexInvoiceListResponse(List.of(), 0L, 0, pageNumber, pageSize,
                    List.of(), grandTotal(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        String rate = AnnexType.REDUCE8.code().equals(type) ? RATE_8_PCT : null;
        String q = search == null || search.isBlank() ? null : search.trim();
        String from = decl.getFromDt().format(YYYYMMDD);
        String to = decl.getToDt().format(YYYYMMDD);

        // One GROUP BY einv_taxrate pass yields every section subtotal AND the
        // paging total (Σ row_count) AND the reduction — no separate count /
        // sum-reduced query.
        List<AnnexTaxGroupProjection> raw = annexInvoiceRepo.groupTotals(
                decl.getBizKey(), from, to, docTypes, rate, q, EXCLUDED_INVOICE_STATUSES);
        List<AnnexTaxGroup> taxGroups = new ArrayList<>(raw.size());
        BigDecimal gSply = BigDecimal.ZERO, gRed = BigDecimal.ZERO, gVat = BigDecimal.ZERO;
        long total = 0;
        for (AnnexTaxGroupProjection p : raw) {
            BigDecimal s = nz(p.getTotalSplyAmt()), r = nz(p.getTotalReducedAmt()), v = nz(p.getTotalVatAmt());
            taxGroups.add(new AnnexTaxGroup(p.getGroupCd(), p.getGroupCd(), p.getRowCount(), str(s), str(r), str(v)));
            gSply = gSply.add(s); gRed = gRed.add(r); gVat = gVat.add(v); total += p.getRowCount();
        }
        AnnexTaxGroup grand = grandTotal(total, gSply, gRed, gVat);

        List<AnnexRow> rows = offset >= total ? List.of()
                : annexInvoiceRepo.findRows(decl.getBizKey(), from, to, docTypes, rate, q,
                        EXCLUDED_INVOICE_STATUSES, offset, pageSize)
                .stream().map(a -> toAnnexRow(a, type)).toList();

        int totalPages = (int) ((total + pageSize - 1) / pageSize);
        return new AnnexInvoiceListResponse(rows, total, totalPages, pageNumber, pageSize,
                taxGroups, grand);
    }

    private static AnnexTaxGroup grandTotal(long rows, BigDecimal sply, BigDecimal reduced, BigDecimal vat) {
        return new AnnexTaxGroup("TOTAL", null, rows, str(sply), str(reduced), str(vat));
    }

    // ---------------------------------------------------------------- export

    /**
     * All annex rows (unpaged) for the Excel export provider — same derived
     * data path + filters as {@link #listAnnexInvoices}. Bounded by the shared
     * export cap so a huge period can't exhaust heap.
     */
    @Transactional(readOnly = true)
    public List<AnnexRow> annexRowsForExport(Long sid, String annexType, String search, String ioDv) {
        VatDeclaration decl = load(sid);
        String type = resolveAnnexType(annexType);
        List<String> docTypes = docTypesFor(type, ioDv);
        if (docTypes.isEmpty()) {
            return List.of();
        }
        String rate = AnnexType.REDUCE8.code().equals(type) ? RATE_8_PCT : null;
        String q = search == null || search.isBlank() ? null : search.trim();
        String from = decl.getFromDt().format(YYYYMMDD);
        String to = decl.getToDt().format(YYYYMMDD);
        return annexInvoiceRepo.findRows(decl.getBizKey(), from, to, docTypes, rate, q,
                        EXCLUDED_INVOICE_STATUSES, 0L, ExcelExportService.MAX_ROWS)
                .stream().map(a -> toAnnexRow(a, type)).toList();
    }

    /**
     * HTKK XML export (API #12) — locked declarations only (OQ-VAT-7).
     * Draft → {@code WBE_VAT_DECL_NOT_LOCKED}.
     */
    @Transactional(readOnly = true)
    public VatExportFile exportXml(Long sid) {
        VatDeclaration decl = load(sid);
        assertLocked(decl);
        return xmlExporter.build(decl, loadIndicator(decl));
    }

    /**
     * A4 PDF export (API #14) — locked declarations only (OQ-VAT-7).
     * Draft → {@code WBE_VAT_DECL_NOT_LOCKED}.
     */
    @Transactional(readOnly = true)
    public VatExportFile exportPdf(Long sid) {
        VatDeclaration decl = load(sid);
        assertLocked(decl);
        return pdfExporter.build(decl, loadIndicator(decl));
    }

    private static void assertLocked(VatDeclaration decl) {
        if (!STATUS_LOCKED.equals(decl.getStatus())) {
            throw new BusinessException("WBE_VAT_DECL_NOT_LOCKED");
        }
    }

    /**
     * Which {@code BUSA_DOC_MSTR.DOC_TYPE}s feed this annex, narrowed by the
     * caller's optional direction filter. Empty = the annex has no invoice
     * source (the two 01-6/GTGT allocation variants).
     */
    private static List<String> docTypesFor(String annexType, String ioDv) {
        List<String> base;
        if (AnnexType.BKBR.code().equals(annexType)) {
            base = List.of(DOC_TYPE_SALES);
        } else if (AnnexType.BKMV.code().equals(annexType)) {
            base = List.of(DOC_TYPE_PURCHASE);
        } else if (AnnexType.REDUCE8.code().equals(annexType)) {
            base = List.of(DOC_TYPE_SALES, DOC_TYPE_PURCHASE);
        } else {
            return List.of();
        }
        String io = ioDv == null || ioDv.isBlank() ? null : ioDv.trim().toUpperCase();
        if (io == null) {
            return base;
        }
        String wanted = "O".equals(io) ? DOC_TYPE_SALES : DOC_TYPE_PURCHASE;
        return base.contains(wanted) ? List.of(wanted) : List.of();
    }

    private static AnnexRow toAnnexRow(AnnexInvoiceProjection a, String annexType) {
        // id / allocRatio are null and handRegYn is always 'N': rows are derived
        // from invoices, so there is nothing manually registered to identify.
        return new AnnexRow(null, annexType, a.getIoDv(), a.getSrcDocId(),
                a.getInvNo(), a.getInvSymbol(), a.getInvDt(), a.getTrcoTaxCode(), a.getTrcoNm(),
                a.getProdNm(), str(a.getSplyAmt()), a.getVatRate(), str(a.getVatAmt()),
                str(a.getReducedAmt()), null, "N",
                a.getVatDeclProcYn(), a.getVatDeclSid(), a.getVatRateOrder());
    }

    /** Validate + normalise the annex type; blank defaults to REDUCE8 (8% modal). */
    private static String resolveAnnexType(String raw) {
        return AnnexType.fromCode(raw == null || raw.isBlank() ? "REDUCE8" : raw)
                .map(AnnexType::code)
                .orElseThrow(() -> new BusinessException("WBE_VALIDATION"));
    }


    private static BigDecimal dec(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("WBE_VALIDATION");
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
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

    /** Applies one editable indicator and records that the client sent it. */
    private void apply(Set<String> sent, String raw, String field, Consumer<BigDecimal> setter) {
        if (raw == null) return;
        setter.accept(parseAmount(raw, field));
        sent.add(field);
    }

    /**
     * [21] is the "no purchase/sale activity in the period" flag, not an amount —
     * the prototype renders a checkbox. Accepts only {@code 0} / {@code 1}
     * (also tolerates {@code N} / {@code Y}); stored as 0/1 in the numeric column
     * so the HTKK XML can emit {@code <ct21>1</ct21>} verbatim.
     */
    private void applyFlag(Set<String> sent, String raw, String field, Consumer<BigDecimal> setter) {
        if (raw == null) return;
        String v = raw.trim();
        BigDecimal flag = switch (v.toUpperCase()) {
            case "1", "Y" -> BigDecimal.ONE;
            case "0", "N", "" -> BigDecimal.ZERO;
            default -> throw new BusinessException("WBE_VAT_DECL_INVALID_INDICATOR", field);
        };
        setter.accept(flag);
        sent.add(field);
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
        m.put("42", set.ct42());   m.put("42a", set.ct42a());
        m.put("42b", set.ct42b()); m.put("43", set.ct43());
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
                toIndicatorSet(ind),
                yn(d.getAnnexBkbrYn(), true),
                yn(d.getAnnexBkmvYn(), true),
                yn(d.getAnnexAllocYn(), true),
                yn(d.getAnnexAllocTeleYn(), true),
                yn(d.getAnnexReduce8Yn(), true),
                d.getAgentName(),
                d.getAgentTaxCode(),
                d.getAgentCtrNo(),
                d.getAgentCtrDt() == null ? null : d.getAgentCtrDt().toString(),
                d.getDuName(),
                d.getDuTaxCode(),
                d.getDuAddr(),
                d.getDuProv(),
                d.getDuDist(),
                d.getDuWard()
        );
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
                str(i.getVatRefundReq1331()), str(i.getVatRefundReq1332()),
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
