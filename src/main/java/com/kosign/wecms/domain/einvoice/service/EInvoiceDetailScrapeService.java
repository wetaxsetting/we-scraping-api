package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailScrapeRequest;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailScrapeResult;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceDetailRow;
import com.kosign.wecms.domain.einvoice.entity.BusaDocDetl;
import com.kosign.wecms.domain.einvoice.entity.BusaDocDetlId;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstrId;
import com.kosign.wecms.domain.einvoice.entity.EinvFeeDetl;
import com.kosign.wecms.domain.einvoice.entity.EinvFeeDetlId;
import com.kosign.wecms.domain.einvoice.entity.EinvTaxDetl;
import com.kosign.wecms.domain.einvoice.entity.EinvTaxDetlId;
import com.kosign.wecms.domain.einvoice.repository.BusaDocDetlRepository;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.domain.einvoice.repository.EinvFeeDetlRepository;
import com.kosign.wecms.domain.einvoice.repository.EinvTaxDetlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * <p><b>Idempotency:</b> upsert per {@code (BIZ_KEY, DOC_ID, DISP_ORD)}
 * so a re-scrape of the same range is safe — find-by-PK then UPDATE or
 * INSERT, mirroring legacy {@code c002:740-864} exactly. A wholesale
 * delete-then-insert was tried first but is UNSAFE against the live JEX DB:
 * each detail table ({@code BUSA_DOC_DETL} / {@code EINV_FEE_DETL} /
 * {@code EINV_TAX_DETL}) carries an {@code AFTER} trigger logging every
 * change into a {@code *_HIS} table keyed on
 * {@code (BIZ_KEY, DOC_ID, DISP_ORD, MOD_DTM)}, where {@code MOD_DTM =
 * to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')}. PostgreSQL freezes
 * {@code now()} at transaction start, so the DELETE's {@code 'D'} history
 * row and the re-INSERT's {@code 'C'} history row for the same
 * {@code DISP_ORD} land on an identical {@code MOD_DTM} → duplicate-key on
 * {@code pk_einv_tax_detl_his}. Upserting keeps it to one DML per
 * {@code DISP_ORD} per transaction, so each {@code *_HIS} row gets a unique
 * PK. As in legacy, stale higher-{@code DISP_ORD} rows from a larger prior
 * scrape are left in place (not deleted).
 *
 * <p><b>Counterparty auto-registration (Scope 4):</b> delegated to
 * {@link EInvoiceTrcoAutoRegistrar} — resolves/creates the
 * {@code TRCO_INFM} contact, seeds the {@code PGRO_BSAC_LNKN_INFM} group
 * link, upserts {@code TRCO_ACCT_INFO} bank accounts, and writes the
 * {@code TRCO_ACTV_HSTR} activity row, then stamps the resolved
 * {@code TRCO_KEY} onto the master (legacy {@code c002:333-535,883-909}).
 *
 * <p><b>Implemented 2026-07-14 (PO-approved legacy parity):</b> the
 * representative columns + {@code TRCO_RSPB_INFM} (D5.3) and the
 * {@code SYS_TRCO_INFM_DIS_NM} nickname normalisation (D5.2b) via
 * {@link EInvoiceTrcoAutoRegistrar}; the payment-term due-date recompute
 * (D5.4, {@link #applyDueDate}, legacy {@code c002:691-711} — overwrites /
 * NULLs {@code DUE_DT} on every scrape). <b>Out of scope:</b> only the
 * OSMC/BI09 contact bridge — legacy {@code comm_0800_001_u001} is
 * {@code PrdtType.C/D}-gated + external OSMC, inert for WeCMS product-type-F
 * (D4). See {@code Claude Code (AI)/notes/update-einvoice-*}.
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
    /** ISSU_DT / DUE_DT wire format (D5.4). */
    private static final DateTimeFormatter DATE_8 =
            DateTimeFormatter.ofPattern("yyyyMMdd");

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
        // Batch timestamp for MOD_DTM: reuse the phase-1 stamp when the client
        // forwards it so the master rows keep the SAME batch id as the audit
        // row + the returned mod_dtm — the modal "View" and the History →
        // Detail drill-down both key on this. Falls back to a fresh stamp
        // (legacy behaviour) when the client omits it.
        final String batchDtm =
                (req.modDtm() != null && req.modDtm().matches("\\d{14}")) ? req.modDtm() : regDtm;

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
            final EInvoiceTrcoAutoRegistrar.CounterpartyResult cp = trcoRegistrar.registerCounterparty(
                    isSales, bizKey, userId, regDtm, docId, row, totlAmt);
            master.setTrcoKey(cp.trcoKey());

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

            // ── Counterparty (TRCO_*) address + phone + issuer tax id ──
            // Legacy c002:625-634 copies the resolved contact's BsunAddr +
            // telNo onto BUSA_DOC_MSTR.TRCO_BSUN_* / TRCO_RPRS_TLPH. The
            // registrar (D5.1) parses the counterparty address into the
            // TRCO_INFM contact, but the master was never stamped — so the
            // View-Details popup (which reads master.TRCO_BSUN_*) showed an
            // empty seller address on Bills. Counterparty = buyer for a Sale,
            // seller (supplier) for a Bill.
            String trcoAddr = isSales ? row.buyerAddr() : row.sellerAddr();
            applyCounterpartyAddress(master, trcoAddr);
            // Seller phone → TRCO_RPRS_TLPH (legacy c002:632-634 — sellerPhone
            // is the counterparty's phone on a Bill; the payload carries no
            // buyer phone, so Sales leaves it untouched).
            setIf(row.sellerPhone(), v -> master.setTrcoRprsTlph(clip(v, 50)));
            // Overwrite the phase-1 ISSU_TAX_ID placeholder ("9999…" for Bills)
            // with the own-company tax id once the detail provides it
            // (legacy c002:270 — Bill: own=buyer→buyerTaxCode, Sale:
            // own=seller→sellerTaxCode). Guarded so a blank never wipes it.
            String ownTaxId = isSales ? row.sellerTaxCode() : row.buyerTaxCode();
            setIf(ownTaxId, v -> master.setIssuTaxId(clip(v, 40)));

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

            // ── D5.4 — payment-term due-date recompute (legacy c002:691-711) ──
            applyDueDate(master, cp.trcoKey(), cp.paymTerm());

            master.setModId(userId);
            master.setModDtm(batchDtm);
            masterRepo.save(master);

            // ── Upsert line items / fees / taxes by (BIZ_KEY, DOC_ID, DISP_ORD) ──
            // Legacy parity (c002:740-864): find-by-PK → UPDATE, else INSERT.
            // A wholesale delete-then-insert is UNSAFE here: each of these
            // tables carries an AFTER-trigger that logs every row change into
            // its {@code *_HIS} table with PK (BIZ_KEY, DOC_ID, DISP_ORD,
            // MOD_DTM), where MOD_DTM = to_char(utc now(), 'YYYYMMDDHH24MISS').
            // now() is frozen at transaction start, so a DELETE ('D') and a
            // re-INSERT ('C') of the same disp_ord in one tx collide on the
            // _HIS PK (dup-key on pk_einv_tax_detl_his). Upserting keeps it to
            // ONE DML per disp_ord per tx → one _HIS row → unique MOD_DTM.
            lineItems += writeProducts(bizKey, docId, userId, regDtm, row.products());
            writeFees(bizKey, docId, userId, regDtm, row.fees());
            writeTaxes(bizKey, docId, userId, regDtm, row.taxes());

            detailSaved++;
        }

        log.info("e-invoice detail intake: bizKey={} type={} detailSaved={} skipped={} lineItems={} batchDtm={} rslt={}",
                bizKey, req.type(), detailSaved, detailSkipped, lineItems, batchDtm, req.rsltCd());
        return new EInvoiceDetailScrapeResult(detailSaved, 0, detailSkipped, lineItems, batchDtm);
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
                              List<ScrapedEInvoiceDetailRow.Product> products) {
        if (products == null || products.isEmpty()) return 0;
        int n = 0;
        for (int i = 0; i < products.size(); i++) {
            ScrapedEInvoiceDetailRow.Product p = products.get(i);
            final int dispOrd = p.dispOrd() > 0 ? p.dispOrd() : i + 1;
            BusaDocDetl d = detlRepo.findById(new BusaDocDetlId(bizKey, docId, dispOrd))
                    .orElseGet(() -> {
                        BusaDocDetl fresh = new BusaDocDetl();
                        fresh.setBizKey(bizKey);
                        fresh.setDocId(docId);
                        fresh.setDispOrd(dispOrd);
                        fresh.setRegId(userId);
                        fresh.setRegDtm(regDtm);
                        return fresh;
                    });
            d.setUserId(userId);
            d.setItemCd(ITEM_CD_DEFAULT);
            d.setItemNm(clip(p.product(), 255));
            d.setItemSpec(clip(p.unit(), 100));
            d.setItemQunt(EInvoiceScrapeService.parseAmount(p.quantity()));
            d.setItemUnitAmt(EInvoiceScrapeService.parseAmount(p.unitPrice()));
            d.setSplyAmt(EInvoiceScrapeService.parseAmount(p.supplyAmt()));
            d.setDcAmt(EInvoiceScrapeService.parseAmount(p.discount()));
            d.setEinvProductname(clip(p.productName(), 500));
            d.setEinvTaxrate(clip(p.taxRate(), 10));
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
            final int dispOrd = f.dispOrd() > 0 ? f.dispOrd() : i + 1;
            EinvFeeDetl e = feeRepo.findById(new EinvFeeDetlId(bizKey, docId, dispOrd))
                    .orElseGet(() -> {
                        EinvFeeDetl fresh = new EinvFeeDetl();
                        fresh.setBizKey(bizKey);
                        fresh.setDocId(docId);
                        fresh.setDispOrd(dispOrd);
                        fresh.setRegId(userId);
                        fresh.setRegDtm(regDtm);
                        return fresh;
                    });
            e.setEinvFeename(clip(f.feeName(), 100));
            e.setEinvFee(EInvoiceScrapeService.parseAmount(f.fee()));
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
            final int dispOrd = t.dispOrd() > 0 ? t.dispOrd() : i + 1;
            EinvTaxDetl e = taxRepo.findById(new EinvTaxDetlId(bizKey, docId, dispOrd))
                    .orElseGet(() -> {
                        EinvTaxDetl fresh = new EinvTaxDetl();
                        fresh.setBizKey(bizKey);
                        fresh.setDocId(docId);
                        fresh.setDispOrd(dispOrd);
                        fresh.setRegId(userId);
                        fresh.setRegDtm(regDtm);
                        return fresh;
                    });
            e.setEinvTaxrate(clip(t.taxRate(), 50));
            e.setAmt(EInvoiceScrapeService.parseAmount(t.amtWithoutTax()));
            e.setTaxAmt(EInvoiceScrapeService.parseAmount(t.taxAmt()));
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

    /**
     * Stamp the counterparty ({@code TRCO_*}) business-address columns on the
     * master from the scraped counterparty address, reusing
     * {@link EInvoiceTrcoAutoRegistrar#parseBusinessAddress} so the master and
     * the {@code TRCO_INFM} contact carry an identical
     * {@code [st, city, postAdrs, dtlAdrs]} decomposition. Legacy parity:
     * {@code c002:625-630} ({@code setTrcoTaxInfo} copies the resolved
     * contact's {@code BsunAddr} onto {@code BUSA_DOC_MSTR.TRCO_BSUN_*}).
     *
     * <p>The decomposition reconstructs cleanly in the View-Details read path:
     * {@code EInvoiceDetailService} joins {@code [post, dtl, city, st]} with
     * {@code ", "}, which recomposes the original scraped address. Blank
     * address → columns left untouched (an empty detail never wipes a good
     * phase-1 value; matches the {@code setIf} guards). Package-private static
     * for unit testing (same pattern as {@link #applyDueDate}).
     */
    static void applyCounterpartyAddress(BusaDocMstr master, String addr) {
        if (!notBlank(addr)) return;
        String[] a = EInvoiceTrcoAutoRegistrar.parseBusinessAddress(addr);
        master.setTrcoBsunSt(clip(a[0], 200));
        master.setTrcoBsunCity(clip(a[1], 200));
        master.setTrcoBsunPostAdrs(clip(a[2], 200));
        master.setTrcoBsunDtlAdrs(clip(a[3], 200));
    }

    /**
     * D5.4 — payment-term due-date recompute (legacy {@code c002:691-711},
     * #40183). For a non-ETC counterparty, a provisioning status that is NOT
     * {@code PA}/{@code PP} (paid/partly-paid), and a valid {@code ISSU_DT}:
     * <ul>
     *   <li>term set → {@code DUE_DT = ISSU_DT + term days};</li>
     *   <li>term blank → {@code DUE_DT} cleared to {@code null}.</li>
     * </ul>
     * <b>Legacy parity (PO-confirmed 2026-07-14):</b> legacy overwrites /
     * NULLs {@code DUE_DT} on EVERY scrape — it does NOT preserve a
     * user-edited due date. This reproduces that exactly. Invalid term /
     * date is defensive-skipped (leaves {@code DUE_DT} untouched).
     */
    static void applyDueDate(BusaDocMstr master, String trcoKey, String paymTerm) {
        if (EInvoiceTrcoAutoRegistrar.ETC_TRCO_KEY.equals(trcoKey)) return;
        final String prov = trim(master.getProvStts());
        if ("PA".equals(prov) || "PP".equals(prov)) return;
        final String issu = trim(master.getIssuDt());
        if (issu.length() != 8) return;               // legacy: ISSU_DT != null
        if (isBlank(paymTerm)) {
            master.setDueDt(null);                    // legacy patch DUE_DT = null
            return;
        }
        try {
            final int days = Integer.parseInt(paymTerm.trim());
            master.setDueDt(LocalDate.parse(issu, DATE_8).plusDays(days).format(DATE_8));
        } catch (RuntimeException e) {
            // Non-numeric term or unparseable ISSU_DT — leave DUE_DT as-is.
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
