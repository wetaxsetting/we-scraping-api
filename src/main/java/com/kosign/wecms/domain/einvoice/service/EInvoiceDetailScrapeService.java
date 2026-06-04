package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailScrapeRequest;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailScrapeResult;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceDetailRow;
import com.kosign.wecms.domain.einvoice.entity.BusaDocDetl;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstrId;
import com.kosign.wecms.domain.einvoice.entity.EinvFeeDetl;
import com.kosign.wecms.domain.einvoice.entity.EinvTaxDetl;
import com.kosign.wecms.domain.einvoice.repository.BusaDocDetlRepository;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.domain.einvoice.repository.EinvFeeDetlRepository;
import com.kosign.wecms.domain.einvoice.repository.EinvTaxDetlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists GDT e-invoice DETAIL rows scraped by the local NXiSAS engine
 * (phase 2) — refactor port of {@code nxisas_einvoice_c002_act.jsp}.
 *
 * <p>Phase 1 ({@link EInvoiceScrapeService}) upserts the {@code BUSA_DOC_MSTR}
 * master rows from {@code INVOICE_LIST}. This phase-2 call carries the
 * decrypted {@code INVOICE_DETAIL} payloads: the service locates each
 * master row by its recomposed {@code DOC_ID}, lightly enriches it
 * (currency / counterparty name / amounts — the columns mapped on the
 * Sprint entity), then (re)writes its line items / fees / taxes:
 * <ul>
 *   <li>{@code BUSA_DOC_DETL} ← engine {@code productArr[]}
 *       (legacy {@code :740-789})</li>
 *   <li>{@code EINV_FEE_DETL} ← engine {@code feeArr[]} (legacy {@code :791-823})</li>
 *   <li>{@code EINV_TAX_DETL} ← engine {@code taxArr[]} (legacy {@code :825-865})</li>
 * </ul>
 *
 * <p><b>Idempotency:</b> delete-then-insert per {@code (BIZ_KEY, DOC_ID)}
 * so a re-scrape of the same range is safe (legacy upserts by
 * {@code DISP_ORD}; the refactor replaces the line set wholesale — the
 * same intent, simpler).
 *
 * <p><b>Counterparty auto-registration (Scope 4):</b> delegated to
 * {@link EInvoiceTrcoAutoRegistrar} — resolves/creates the
 * {@code TRCO_INFM} contact, seeds the {@code PGRO_BSAC_LNKN_INFM} group
 * link, upserts {@code TRCO_ACCT_INFO} bank accounts, and writes the
 * {@code TRCO_ACTV_HSTR} activity row, then stamps the resolved
 * {@code TRCO_KEY} onto the master (legacy {@code c002:333-535,883-909}).
 *
 * <p><b>Still deferred (legacy {@code c002} does more):</b> the OSMC/BI09
 * contact bridge; the representative columns + {@code TRCO_RSPB_INFM};
 * the {@code SYS_TRCO_INFM_DIS_NM} nickname normalisation; the
 * payment-term overdue-days recalculation. Tracked in
 * {@code docs/features/e-invoice/final-parity-audit.md}.
 *
 * <p><b>Runtime caveat (OQ-S1):</b> the upstream scrape leg depends on
 * the local {@code .exe}, licensed per page origin (not on localhost) —
 * this persistence layer is unit-testable in isolation, but the
 * end-to-end scrape is verified on a licensed origin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EInvoiceDetailScrapeService {

    private static final String ITEM_CD_DEFAULT = "0000000000"; // legacy :765
    private static final DateTimeFormatter DTM_14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BusaDocMstrRepository masterRepo;
    private final BusaDocDetlRepository detlRepo;
    private final EinvFeeDetlRepository feeRepo;
    private final EinvTaxDetlRepository taxRepo;
    private final EInvoiceTrcoAutoRegistrar trcoRegistrar;

    @Transactional
    public EInvoiceDetailScrapeResult saveScrapedDetails(EInvoiceDetailScrapeRequest req) {
        final String bizKey = requireBizKey();
        final String userId = TenantContext.currentUserId();
        final boolean isSales = "SALES".equalsIgnoreCase(req.type());
        final String regDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);

        int detailSaved = 0;
        int detailSkipped = 0;
        int lineItems = 0;

        for (ScrapedEInvoiceDetailRow row : req.details()) {
            final BusaDocMstr master = locateMaster(bizKey, isSales, row);
            if (master == null) {
                detailSkipped++;
                continue;
            }
            final String docId = master.getDocId();

            // ── Counterparty auto-registration (Scope 4 — legacy
            // c002:333-535 + 883-909). Resolves/creates the TRCO_INFM
            // contact + group-link + bank account + activity-history row,
            // and stamps the resolved TRCO_KEY onto the master. ──
            final BigDecimal totlAmt = EInvoiceScrapeService.parseAmount(row.totalAmt());
            final String trcoKey = trcoRegistrar.registerCounterparty(
                    isSales, bizKey, userId, regDtm, docId, row, totlAmt);
            master.setTrcoKey(trcoKey);

            // ── Full master enrichment (Scope 3 — legacy c002:264-687) ──
            // Issuer (ISSU_*) is our own company on the invoice: for SALES
            // we are the seller, for EXPENSE we are the buyer. The
            // counterparty (TRCO_CMNM) is the other party. The EINV_*
            // bank / citizen fields are stored verbatim (not type-swapped),
            // matching legacy.
            String ccy = normalizeCcy(row.currencyCd());
            if (ccy != null) master.setTrcoTrscCcyCd(ccy);

            String counterpartyName = isSales ? row.buyerName() : row.sellerName();
            if (notBlank(counterpartyName)) {
                master.setTrcoCmnm(clip(counterpartyName, 200));
                master.setTrcoCmenm(clip(counterpartyName, 200));
            } else if (notBlank(row.sellerName())) {
                // Fallback to the legacy light-enrich behaviour.
                master.setTrcoCmnm(clip(row.sellerName(), 200));
            }

            // Issuer (own company) name + address.
            String issuName = isSales ? row.sellerName() : row.buyerName();
            String issuAddr = isSales ? row.sellerAddr() : row.buyerAddr();
            if (notBlank(issuName)) {
                master.setIssuCmnm(clip(issuName, 200));
                master.setIssuCmenm(clip(issuName, 200));
            }
            applyIssuerAddress(master, issuAddr);

            // EINV_* scalar fields (verbatim from the detail payload).
            setIf(row.invoiceTitle(),      v -> master.setEinvTitle(clip(v, 300)));
            setIf(row.mccqt(),             v -> master.setEinvMccqt(clip(v, 50)));
            setIf(row.originalInvoice(),   v -> master.setEinvOriginalInvoice(clip(v, 200)));
            setIf(row.buyerCitizenId(),    v -> master.setEinvBuyercitizenid(clip(v, 12)));
            setIf(row.sellerBankAcc(),     v -> master.setEinvSellerbankacc(clip(v, 100)));
            setIf(row.sellerBankName(),    v -> master.setEinvSellerbankname(clip(v, 200)));
            setIf(row.buyerBankAcc(),      v -> master.setEinvBuyerbankacc(clip(v, 100)));
            setIf(row.buyerBankName(),     v -> master.setEinvBuyerbankname(clip(v, 200)));
            setIf(row.paymentType(),       v -> master.setEinvPaymenttype(clip(v, 50)));
            setIf(row.listNumber(),        v -> master.setEinvListnumber(clip(v, 50)));
            setIf(row.listDate(),          v -> master.setEinvListdate(clip(v, 50)));
            setIf(row.totalAmtInLetter(),  v -> master.setEinvTotalamtinletter(clip(v, 200)));
            setIf(row.buyerSignBy(),       v -> master.setEinvBuyersignby(clip(v, 200)));
            setIf(digitsClip(row.buyerSignDate(), 8),  master::setEinvBuyersigndate);
            setIf(digitsClip(row.buyerSignTime(), 6),  master::setEinvBuyersigntime);
            setIf(row.sellerSignBy(),      v -> master.setEinvSellersignby(clip(v, 200)));
            setIf(digitsClip(row.sellerSignDate(), 8), master::setEinvSellersigndate);
            setIf(digitsClip(row.sellerSignTime(), 6), master::setEinvSellersigntime);
            setIf(row.qrCode(),            v -> master.setEinvQrcode(clip(v, 1000)));

            BigDecimal fee = EInvoiceScrapeService.parseAmount(row.totalFee());
            if (fee != null) master.setTotlFeeAmt(fee);
            BigDecimal rate = EInvoiceScrapeService.parseAmount(row.rate());
            if (rate != null) master.setEinvRate(rate);

            // Amounts (legacy detail re-asserts these from the detail payload).
            if (totlAmt != null) master.setTotlAmt(totlAmt);
            BigDecimal sply = EInvoiceScrapeService.parseAmount(row.totalAmtWithoutTax());
            if (sply != null) master.setSplyAmtSum(sply);
            BigDecimal tax = EInvoiceScrapeService.parseAmount(row.totalTaxAmt());
            if (tax != null) master.setTaxAmtSum(tax);
            BigDecimal disc = EInvoiceScrapeService.parseAmount(row.totalDiscount());
            if (disc != null) master.setTotlDisAmt(disc);

            master.setModId(userId);
            master.setModDtm(regDtm);
            masterRepo.save(master);

            // ── Replace line items / fees / taxes (delete-then-insert) ──
            detlRepo.deleteByBizKeyAndDocId(bizKey, docId);
            feeRepo.deleteByBizKeyAndDocId(bizKey, docId);
            taxRepo.deleteByBizKeyAndDocId(bizKey, docId);

            lineItems += writeProducts(bizKey, docId, userId, regDtm, ccy, row.products());
            writeFees(bizKey, docId, userId, regDtm, row.fees());
            writeTaxes(bizKey, docId, userId, regDtm, row.taxes());

            detailSaved++;
        }

        log.info("e-invoice detail intake: bizKey={} type={} detailSaved={} skipped={} lineItems={} rslt={}",
                bizKey, req.type(), detailSaved, detailSkipped, lineItems, req.rsltCd());
        return new EInvoiceDetailScrapeResult(detailSaved, 0, detailSkipped, lineItems, regDtm);
    }

    /* ------------------------------------------------------------------ */

    /**
     * Recompose the master {@code DOC_ID} from the scraped identity and
     * find the phase-1 row. Tries both candidate forms (with and without
     * the seller-tax suffix) so the lookup works regardless of the
     * branch phase 1 used (Sales branch-A keeps the bare base; everything
     * else appends {@code -sellerTax}).
     */
    private BusaDocMstr locateMaster(String bizKey, boolean isSales, ScrapedEInvoiceDetailRow row) {
        final String base = trim(row.numericSymbol()) + trim(row.invoiceSymbol())
                + "-" + trim(row.invoiceNo());
        if (trim(row.invoiceNo()).isEmpty()) return null;
        final String sellerTax = trim(row.sellerTaxCode());
        final String withTax = sellerTax.isEmpty() ? null : base + "-" + sellerTax;

        final List<String> candidates = new ArrayList<>();
        if (isSales) {
            candidates.add(base);
            if (withTax != null) candidates.add(withTax);
        } else {
            if (withTax != null) candidates.add(withTax);
            candidates.add(base);
        }
        for (String docId : candidates) {
            BusaDocMstr m = masterRepo.findById(new BusaDocMstrId(bizKey, docId)).orElse(null);
            if (m != null) return m;
        }
        return null;
    }

    private int writeProducts(String bizKey, String docId, String userId, String regDtm,
                              String ccy, List<ScrapedEInvoiceDetailRow.Product> products) {
        if (products == null || products.isEmpty()) return 0;
        int n = 0;
        for (int i = 0; i < products.size(); i++) {
            ScrapedEInvoiceDetailRow.Product p = products.get(i);
            BusaDocDetl d = new BusaDocDetl();
            d.setBizKey(bizKey);
            d.setDocId(docId);
            d.setDispOrd(p.dispOrd() > 0 ? p.dispOrd() : i + 1);
            d.setUserId(userId);
            d.setItemCd(ITEM_CD_DEFAULT);
            d.setItemNm(clip(p.product(), 500));
            d.setItemSpec(clip(p.unit(), 4000));
            d.setItemQunt(EInvoiceScrapeService.parseAmount(p.quantity()));
            d.setItemUnitAmt(EInvoiceScrapeService.parseAmount(p.unitPrice()));
            d.setSplyAmt(EInvoiceScrapeService.parseAmount(p.supplyAmt()));
            d.setDcAmt(EInvoiceScrapeService.parseAmount(p.discount()));
            d.setCcyCd(ccy);
            d.setEinvProductname(clip(p.productName(), 500));
            d.setEinvTaxrate(clip(p.taxRate(), 10));
            d.setRegId(userId);
            d.setRegDtm(regDtm);
            d.setModId(userId);
            d.setModDtm(regDtm);
            detlRepo.save(d);
            n++;
        }
        return n;
    }

    private void writeFees(String bizKey, String docId, String userId, String regDtm,
                           List<ScrapedEInvoiceDetailRow.Fee> fees) {
        if (fees == null || fees.isEmpty()) return;
        for (int i = 0; i < fees.size(); i++) {
            ScrapedEInvoiceDetailRow.Fee f = fees.get(i);
            EinvFeeDetl e = new EinvFeeDetl();
            e.setBizKey(bizKey);
            e.setDocId(docId);
            e.setDispOrd(f.dispOrd() > 0 ? f.dispOrd() : i + 1);
            e.setEinvFeename(clip(f.feeName(), 100));
            e.setEinvFee(EInvoiceScrapeService.parseAmount(f.fee()));
            e.setRegId(userId);
            e.setRegDtm(regDtm);
            e.setModId(userId);
            e.setModDtm(regDtm);
            feeRepo.save(e);
        }
    }

    private void writeTaxes(String bizKey, String docId, String userId, String regDtm,
                            List<ScrapedEInvoiceDetailRow.Tax> taxes) {
        if (taxes == null || taxes.isEmpty()) return;
        for (int i = 0; i < taxes.size(); i++) {
            ScrapedEInvoiceDetailRow.Tax t = taxes.get(i);
            EinvTaxDetl e = new EinvTaxDetl();
            e.setBizKey(bizKey);
            e.setDocId(docId);
            e.setDispOrd(t.dispOrd() > 0 ? t.dispOrd() : i + 1);
            e.setEinvTaxrate(clip(t.taxRate(), 50));
            e.setAmt(EInvoiceScrapeService.parseAmount(t.amtWithoutTax()));
            e.setTaxAmt(EInvoiceScrapeService.parseAmount(t.taxAmt()));
            e.setRegId(userId);
            e.setRegDtm(regDtm);
            e.setModId(userId);
            e.setModDtm(regDtm);
            taxRepo.save(e);
        }
    }

    /** Normalise the GDT currency string to a 3-char code (legacy maps
     *  {@code "VNĐ" → "VND"}; {@code :241-243}). Blank → null. */
    static String normalizeCcy(String raw) {
        String v = trim(raw);
        if (v.isEmpty()) return null;
        String up = v.toUpperCase();
        if (up.startsWith("VN")) return "VND";
        return up.length() <= 3 ? up : up.substring(0, 3);
    }

    private static String clip(String s, int max) {
        String v = trim(s);
        if (v.isEmpty()) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }

    /** Apply {@code setter} only when {@code value} is non-blank — so an
     *  empty detail field never overwrites a good phase-1 value. */
    private static void setIf(String value, java.util.function.Consumer<String> setter) {
        if (notBlank(value)) setter.accept(value);
    }

    /** Strip non-digits and clip to {@code n} chars (for the
     *  {@code VARCHAR(8)} sign-date / {@code VARCHAR(6)} sign-time
     *  columns). Blank / no-digit → null. */
    private static String digitsClip(String raw, int n) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        return digits.length() <= n ? digits : digits.substring(0, n);
    }

    /**
     * Set the issuer (own-company) business address columns from the
     * comma-separated GDT address string. Simplified vs legacy's intricate
     * index math (c002:272-322): the full address goes to
     * {@code ISSU_BSUN_POST_ADRS}, the last comma segment to
     * {@code ISSU_BSUN_CITY}, and {@code ISSU_BSUN_ST} defaults to the
     * legacy "Việt Nam" when the address ends with it. Detail-address
     * sub-field decomposition fidelity is a documented simplification.
     */
    private static void applyIssuerAddress(BusaDocMstr master, String addr) {
        String a = trim(addr);
        if (a.isEmpty()) return;
        String[] parts = a.split(",");
        String last = parts.length > 0 ? parts[parts.length - 1].trim() : "";
        master.setIssuBsunPostAdrs(clip(a, 200));
        if (parts.length >= 2) {
            master.setIssuBsunCity(clip(last, 200));
        }
        master.setIssuBsunSt("Việt Nam".equalsIgnoreCase(last) ? "Việt Nam" : null);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String requireBizKey() {
        final String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE9999");
        }
        return bizKey;
    }
}
