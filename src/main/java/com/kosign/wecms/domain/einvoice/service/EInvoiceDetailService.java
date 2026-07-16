package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailResponse.FeeRow;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailResponse.ItemRow;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailResponse.Party;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailResponse.TaxRow;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailResponse.Totals;
import com.kosign.wecms.domain.einvoice.entity.BusaDocDetl;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.entity.EinvFeeDetl;
import com.kosign.wecms.domain.einvoice.entity.EinvTaxDetl;
import com.kosign.wecms.domain.einvoice.repository.BusaDocDetlRepository;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.domain.einvoice.repository.EinvFeeDetlRepository;
import com.kosign.wecms.domain.einvoice.repository.EinvTaxDetlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.StringJoiner;

/**
 * "View e-Invoice Details" — single-invoice detail assembly for the shared
 * Sales / Expenses popup. Refactor port of the legacy
 * {@code einv_0002_001_r001} read path (see
 * {@code docs/jex/features/view-e-invoice-details.md} §3).
 *
 * <p>Legacy parity notes:
 * <ul>
 *   <li>Doc-type resolution reuses {@link EInvoiceService#resolveDocType}:
 *       {@code SALES}/{@code I} → {@code 'I'}, {@code EXPENSE}/{@code BILL} →
 *       {@code 'B'}; {@code 'E'}(EXPENSE)/{@code 'S'} are rejected with
 *       {@code WBE2002} — mirroring the legacy action's {@code WBE0001}
 *       reject of {@code DOC_TYPE='E'}.</li>
 *   <li>The master is restricted to {@code HAND_REG_YN='N'} (auto-scraped
 *       docs), matching {@code einv_0002_001_r001_act.jsp:127-134}.</li>
 *   <li>Seller / buyer identity swap by {@code doc_type}; bank + sign +
 *       buyer full-name / citizen-id come from dedicated columns and are not
 *       swapped (JEX audit §5).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EInvoiceDetailService {

    private static final String CODE_NOT_FOUND = "WBE2019";

    private final EInvoiceService eInvoiceService;
    private final BusaDocMstrRepository mstrRepo;
    private final BusaDocDetlRepository detlRepo;
    private final EinvFeeDetlRepository feeRepo;
    private final EinvTaxDetlRepository taxRepo;

    @Transactional(readOnly = true)
    public EInvoiceDetailResponse getDetail(String docId, String type) {
        final String bizKey  = TenantContext.currentBizKey();
        final String docType = eInvoiceService.resolveDocType(type); // WBE2002 on E/S/unknown
        if (docId == null || docId.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "doc_id is required");
        }

        BusaDocMstr m = mstrRepo
                .findByBizKeyAndDocIdAndDocTypeAndHandRegYn(bizKey, docId.trim(), docType, "N")
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND, docId));

        boolean sale = "I".equals(docType);

        String issuAddr = joinAddr(m.getIssuBsunPostAdrs(), m.getIssuBsunDtlAdrs(),
                m.getIssuBsunCity(), m.getIssuBsunSt());
        String trcoAddr = joinAddr(m.getTrcoBsunPostAdrs(), m.getTrcoBsunDtlAdrs(),
                m.getTrcoBsunCity(), m.getTrcoBsunSt());

        // Company identity swaps by doc_type (JEX audit §5.1).
        Party seller = new Party(
                sale ? m.getIssuCmnm() : m.getTrcoCmnm(),
                sale ? m.getIssuTaxId() : m.getTrcoTaxId(),
                sale ? issuAddr : trcoAddr,
                sale ? m.getIssuRprsTlph() : m.getTrcoRprsTlph(),
                null,   // seller has no "full name" line
                null,   // seller has no citizen id
                m.getEinvSellerbankacc(),   // bank + sign: dedicated columns, not swapped (§5.2)
                m.getEinvSellerbankname(),
                m.getEinvSellersignby(),
                m.getEinvSellersigndate(),
                m.getEinvSellersigntime());

        Party buyer = new Party(
                sale ? m.getTrcoCmnm() : m.getIssuCmnm(),
                sale ? m.getTrcoTaxId() : m.getIssuTaxId(),
                sale ? trcoAddr : issuAddr,
                sale ? m.getTrcoRprsTlph() : m.getIssuRprsTlph(),
                m.getEinvBuyerfullname(),
                m.getEinvBuyercitizenid(),
                m.getEinvBuyerbankacc(),
                m.getEinvBuyerbankname(),
                null, null, null);   // buyer has no signature block

        List<ItemRow> items = detlRepo.findByBizKeyAndDocIdOrderByDispOrdAsc(bizKey, docId).stream()
                .map(EInvoiceDetailService::toItem).toList();
        List<FeeRow> fees = feeRepo.findByBizKeyAndDocIdOrderByDispOrdAsc(bizKey, docId).stream()
                .map(EInvoiceDetailService::toFee).toList();
        List<TaxRow> taxes = taxRepo.findByBizKeyAndDocIdOrderByDispOrdAsc(bizKey, docId).stream()
                .map(EInvoiceDetailService::toTax).toList();

        Totals totals = new Totals(
                str(m.getSplyAmtSum()),
                str(m.getTaxAmtSum()),
                str(m.getTotlFeeAmt()),
                str(m.getTotlDisAmt()),
                str(m.getTotlAmt()),
                m.getEinvTotalamtinletter());

        return new EInvoiceDetailResponse(
                m.getDocId(),
                docType,
                m.getEinvTitle(),
                m.getEinvNumericsymbol(),
                m.getEinvInvoicesymbol(),
                m.getHandDocId(),
                m.getEinvInvoicestatus(),
                m.getEinvMccqt(),
                m.getEinvQrcode(),
                m.getEinvOriginalInvoice(),
                m.getIssuDt(),
                m.getTrcoTrscCcyCd() != null ? m.getTrcoTrscCcyCd().trim() : null,
                str(m.getEinvRate()),
                m.getEinvListnumber(),
                m.getEinvListdate(),
                m.getEinvPaymenttype(),
                m.getEinvTestresults(),
                seller,
                buyer,
                items,
                fees,
                taxes,
                totals);
        // OQ-CON resolved 2026-07-15: the refactor has no Unlink feature, so
        // the legacy linked-expense self-lookup (con_doc_id) is intentionally
        // not surfaced.
    }

    private static ItemRow toItem(BusaDocDetl d) {
        return new ItemRow(
                d.getDispOrd(),
                d.getItemNm(),
                d.getEinvProductname(),
                d.getItemSpec(),
                str(d.getItemQunt()),
                str(d.getItemUnitAmt()),
                str(d.getDcAmt()),
                d.getEinvTaxrate(),
                str(d.getSplyAmt()));
    }

    private static FeeRow toFee(EinvFeeDetl f) {
        return new FeeRow(f.getDispOrd(), f.getEinvFeename(), str(f.getEinvFee()));
    }

    private static TaxRow toTax(EinvTaxDetl t) {
        return new TaxRow(t.getEinvTaxrate(), str(t.getAmt()), str(t.getTaxAmt()));
    }

    /** Join the four address parts (POST, DTL, CITY, ST) in the legacy
     *  IDO {@code BUSA_DOC_MSTR_R029} order, dropping blanks. Returns
     *  {@code null} when every part is blank. */
    private static String joinAddr(String post, String dtl, String city, String st) {
        StringJoiner j = new StringJoiner(", ");
        for (String part : new String[]{post, dtl, city, st}) {
            if (part != null && !part.isBlank()) j.add(part.trim());
        }
        return j.length() == 0 ? null : j.toString();
    }

    /** Raw plain-decimal string (frontend formats); {@code null} passes through. */
    private static String str(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }
}
