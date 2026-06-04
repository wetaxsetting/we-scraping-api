package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceDetailRow;
import com.kosign.wecms.domain.einvoice.entity.TrcoActvHstr;
import com.kosign.wecms.domain.einvoice.repository.TrcoActvHstrRepository;
import com.kosign.wecms.domain.setting.contacts.entity.PgroBsacLnknInfm;
import com.kosign.wecms.domain.setting.contacts.entity.PgroBsacLnknInfmId;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.repository.PgroBsacLnknInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoAcctInfoRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Counterparty auto-registration for the e-Invoice detail-save &mdash;
 * refactor port of {@code nxisas_einvoice_c002_act.jsp:333-535} (TRCO
 * master + group-link + bank-account upsert) and {@code :883-909}
 * (activity-history ledger).
 *
 * <p><b>What it does, per scraped invoice:</b>
 * <ol>
 *   <li>Resolves the counterparty from the invoice payload by type: for
 *       SALES it is the buyer, for EXPENSE the seller (legacy {@code :210-218}).</li>
 *   <li>If the counterparty carries a tax id and no contact yet exists for
 *       {@code (BIZ_KEY, TRCO_TAX_ID, TRSC_CCY_CD)}, creates a
 *       {@code TRCO_INFM} row (inquiry-flag {@code 'N'}, no
 *       {@code HAND_TRCO_KEY} &mdash; a system contact, not surfaced in the
 *       Contacts list). An existing contact is reused, never duplicated.</li>
 *   <li>Seeds the default group link {@code PGRO_BSAC_LNKN_INFM}
 *       ({@code USER_ID='ALL'}, {@code GRP_CD='S'} for SALES / {@code 'B'}
 *       for EXPENSE) if absent.</li>
 *   <li>Upserts the counterparty bank account(s) into {@code TRCO_ACCT_INFO}
 *       (one row per {@code ;}-separated account number, deduped on
 *       {@code (BIZ_KEY, TRCO_KEY, BANK_NM, ACCT_NO)}).</li>
 *   <li>Writes one {@code TRCO_ACTV_HSTR} row, deduped on
 *       {@code (BIZ_KEY, DV_CD, TASK_CD, API_APV_KEY=DOC_ID)}.</li>
 * </ol>
 * Returns the resolved {@code TRCO_KEY} so the caller can stamp it onto
 * {@code BUSA_DOC_MSTR.TRCO_KEY}.
 *
 * <p><b>No tax id → ETC.</b> When the counterparty has no tax id, legacy
 * treats it like a manually-entered contact and pins {@code TRCO_KEY} to
 * the ETC sentinel ({@code "99999999999999999999"}) without creating any
 * {@code TRCO_INFM} / {@code PGRO} / {@code ACCT} row (legacy {@code :536-622}).
 * The activity row is still written (with the ETC key) for parity.
 *
 * <p><b>Cross-domain dependency (deliberate):</b> this {@code einvoice}
 * service writes the {@code TRCO_*} master tables that the
 * {@code setting/contacts} domain owns, reusing that domain's entities +
 * repositories rather than duplicating the {@code @Table} mappings. The
 * coupling is intrinsic to the legacy behaviour &mdash; the e-Invoice flow
 * genuinely registers contacts &mdash; and reuse beats a second set of
 * {@code TRCO_INFM} / {@code TRCO_ACCT_INFO} / {@code PGRO_BSAC_LNKN_INFM}
 * entity mappings. {@code TRCO_ACTV_HSTR} has no Contacts consumer, so it
 * lives in this domain.
 *
 * <p><b>Out of scope (legacy does more):</b> the BI09 OSMC bridge; the
 * representative columns ({@code TRCO_RPPR_ENM} / {@code TRCO_RPPR_NM}) and
 * {@code TRCO_RSPB_INFM} (neither mapped nor in the PROD active list); the
 * {@code SYS_TRCO_INFM_DIS_NM} system-display-name nickname normalisation
 * (table not in the PROD active list) &mdash; the registrar keeps the
 * tax-id-suffix nickname fallback only; the payment-term overdue-days
 * recalculation ({@code :691-711}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EInvoiceTrcoAutoRegistrar {

    /** Legacy {@code GenericBean.ETC_TRCO_KEY} — pseudo contact for
     *  tax-id-less invoices. 20 nines (fits {@code VARCHAR(20)}). */
    static final String ETC_TRCO_KEY = "99999999999999999999";

    private final TrcoInfmRepository trcoInfmRepo;
    private final TrcoAcctInfoRepository trcoAcctRepo;
    private final PgroBsacLnknInfmRepository pgroRepo;
    private final TrcoActvHstrRepository actvHstrRepo;
    private final LegacyFunctionRepository legacyFn;

    /**
     * Auto-register (or reuse) the counterparty for one scraped invoice
     * and write its activity-history row. Runs inside the caller's
     * transaction (REQUIRED) so it commits / rolls back with the
     * detail-save.
     *
     * @return the resolved {@code TRCO_KEY} (real contact, existing match,
     *         or the ETC sentinel)
     */
    @Transactional
    public String registerCounterparty(boolean isSales, String bizKey, String userId,
                                        String regDtm, String docId,
                                        ScrapedEInvoiceDetailRow row, BigDecimal totalAmt) {
        // Counterparty resolution by invoice type (legacy c002:210-215).
        final String trcoTaxId = trim(isSales ? row.buyerTaxCode()   : row.sellerTaxCode());
        final String cmnm      = trim(isSales ? row.buyerName()      : row.sellerName());
        final String bankNm    = trim(isSales ? row.buyerBankName()  : row.sellerBankName());
        final String acctNo    = trim(isSales ? row.buyerBankAcc()   : row.sellerBankAcc());
        final String grpCd     = isSales ? "S" : "B";
        final String dvCd      = isSales ? "S" : "B";
        final String taskCd    = isSales ? "S003" : "B003";
        final String ccy       = trcoCcy(row.currencyCd());

        final String trcoKey;
        if (trcoTaxId.isEmpty()) {
            // ETC path — no tax id, no master/group/account rows.
            trcoKey = ETC_TRCO_KEY;
        } else {
            TrcoInfm contact = trcoInfmRepo
                    .findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(bizKey, trcoTaxId, ccy)
                    .orElse(null);
            if (contact == null) {
                contact = createContact(bizKey, userId, regDtm, trcoTaxId, cmnm, ccy);
            }
            trcoKey = contact.getTrcoKey();
            seedGroupLink(bizKey, grpCd, trcoKey, contact.getTaxId(), regDtm);
            upsertAccounts(bizKey, trcoKey, bankNm, acctNo, userId, regDtm);
        }

        writeActivityHistory(bizKey, userId, regDtm, docId, trcoKey, cmnm, ccy,
                dvCd, taskCd, totalAmt);
        return trcoKey;
    }

    /* ------------------------------------------------------------------ */

    /** Create a system contact (legacy {@code c002:350-480}, minus the
     *  out-of-scope representative / RSPB / SYS-display-name branches). */
    private TrcoInfm createContact(String bizKey, String userId, String regDtm,
                                   String trcoTaxId, String cmnm, String ccy) {
        final String trcoKey = legacyFn.nextTrcoKey(bizKey);
        final String name = cmnm.isEmpty() ? trcoTaxId : cmnm;

        TrcoInfm c = new TrcoInfm();
        c.setTrcoKey(trcoKey);
        c.setBizKey(bizKey);
        c.setTaxId(trcoTaxId);                 // TAX_ID — NOT NULL
        c.setTrcoCmnm(clip(name, 200));
        c.setTrcoCmenm(clip(name, 200));
        c.setTrcoTaxId(trcoTaxId);
        c.setTrscCcyCd(ccy);                   // CHECK IN ('VND','USD')
        c.setTrcoInqYn("N");                   // legacy YesOrNo.NO (system contact)
        c.setTrcoNickNm(uniqueNickNm(bizKey, name, trcoTaxId, trcoKey));
        c.setRegId(userId);
        c.setRegDtm(regDtm);
        c.setModId(userId);
        c.setModDtm(regDtm);
        // saveAndFlush so a second invoice in the same batch that resolves
        // the same contact finds it instead of re-creating it.
        return trcoInfmRepo.saveAndFlush(c);
    }

    /**
     * Build a nickname that satisfies {@code trco_infm_uk01}
     * UNIQUE({@code BIZ_KEY}, {@code TRCO_NICK_NM}). Mirrors legacy's
     * tax-id-suffix de-dup ({@code c002:451-467}); the legacy
     * {@code SYS_TRCO_INFM_DIS_NM} normalisation is out of scope.
     */
    private String uniqueNickNm(String bizKey, String name, String taxId, String trcoKey) {
        String base = clip(name, 200);
        if (!trcoInfmRepo.existsByBizKeyAndTrcoNickNm(bizKey, base)) {
            return base;
        }
        if (!taxId.isEmpty()) {
            String suffix = (taxId.length() >= 8 && taxId.length() > 10)
                    ? right(taxId, 8)
                    : (taxId.length() >= 4 && taxId.length() <= 10 ? right(taxId, 4) : null);
            if (suffix != null) {
                String withSuffix = clip(name + " " + suffix, 200);
                if (!trcoInfmRepo.existsByBizKeyAndTrcoNickNm(bizKey, withSuffix)) {
                    return withSuffix;
                }
            }
        }
        // Refactor hardening: the global TRCO_KEY guarantees uniqueness.
        // Lead with the key so it is never lost to the 200-char clip.
        return clip(trcoKey + " " + name, 200);
    }

    /** Seed the default group link if absent (legacy {@code c002:509-518}). */
    private void seedGroupLink(String bizKey, String grpCd, String trcoKey,
                               String taxId, String regDtm) {
        PgroBsacLnknInfmId id = new PgroBsacLnknInfmId(bizKey, "ALL", grpCd, trcoKey);
        if (pgroRepo.existsById(id)) {
            return;
        }
        PgroBsacLnknInfm link = new PgroBsacLnknInfm();
        link.setBizKey(bizKey);
        link.setUserId("ALL");
        link.setGrpCd(grpCd);
        link.setTrcoKey(trcoKey);
        link.setTaxId(taxId);
        link.setRegDtm(regDtm);
        pgroRepo.save(link);
    }

    /** Upsert counterparty bank accounts (legacy {@code c002:521-535}). */
    private void upsertAccounts(String bizKey, String trcoKey, String bankNm,
                                String acctNo, String userId, String regDtm) {
        if (bankNm.isEmpty() || acctNo.isEmpty()) {
            return;
        }
        for (String raw : acctNo.split(";")) {
            String norm = normalizeAcctNo(raw);
            if (norm.isEmpty()) {
                continue;
            }
            boolean exists = trcoAcctRepo
                    .findByBizKeyAndTrcoKeyAndAcctNo(bizKey, trcoKey, norm)
                    .stream()
                    .anyMatch(a -> bankNm.equals(a.getBankNm()));
            if (exists) {
                continue;
            }
            TrcoAcctInfo a = new TrcoAcctInfo();
            a.setBizKey(bizKey);
            a.setTrcoKey(trcoKey);
            a.setTrcoAcctKey(nextAcctKey(bizKey, trcoKey));
            a.setAcctNo(clip(norm, 100));
            a.setDispOrd(1);                   // legacy default
            a.setBankNm(clip(bankNm, 200));
            a.setRegId(userId);
            a.setRegDtm(regDtm);
            a.setModId(userId);
            a.setModDtm(regDtm);
            trcoAcctRepo.saveAndFlush(a);
        }
    }

    /** Per-{@code (BIZ_KEY, TRCO_KEY)} next {@code TRCO_ACCT_KEY} (MAX+1). */
    private String nextAcctKey(String bizKey, String trcoKey) {
        long max = trcoAcctRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey).stream()
                .map(TrcoAcctInfo::getTrcoAcctKey)
                .filter(k -> k != null && k.matches("\\d+"))
                .mapToLong(Long::parseLong)
                .max()
                .orElse(0L);
        return String.valueOf(max + 1);
    }

    /** Write the activity-history row, deduped per legacy {@code c002:895-907}. */
    private void writeActivityHistory(String bizKey, String userId, String regDtm,
                                      String docId, String trcoKey, String cmnm, String ccy,
                                      String dvCd, String taskCd, BigDecimal totalAmt) {
        String apiApvKey = trim(docId);
        if (apiApvKey.isEmpty()) {
            return;
        }
        if (actvHstrRepo.existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(bizKey, dvCd, taskCd, apiApvKey)) {
            return;
        }
        TrcoActvHstr h = new TrcoActvHstr();
        h.setBizKey(bizKey);
        h.setActvSeq(String.valueOf(actvHstrRepo.nextActvSeq(bizKey)));
        h.setUserId(userId);
        h.setTrcoKey(trcoKey);
        // TRCO_CMNM is NOT NULL — fall back to the key for tax-id-less ETC.
        h.setTrcoCmnm(cmnm.isEmpty() ? trcoKey : clip(cmnm, 200));
        h.setTrcoCmenm(cmnm.isEmpty() ? null : clip(cmnm, 200));
        h.setApiApvKey(clip(apiApvKey, 50));
        h.setDvCd(dvCd);
        h.setTaskCd(taskCd);
        h.setCcyCd(ccy);
        h.setActvAmt(totalAmt);
        h.setPcsnStts("U");        // legacy DAO default UPDATED
        h.setRsltCd("C");          // legacy DAO default CREATED
        h.setTrcoCnfmYn("1");      // legacy DAO default NO
        h.setRegDtm(regDtm);
        h.setModId(userId);
        h.setModDtm(regDtm);
        actvHstrRepo.saveAndFlush(h);
    }

    /* ------------------------------------------------------------------ */

    /** Constrain the GDT currency to the {@code TRSC_CCY_CD} CHECK domain
     *  ({@code 'VND'} / {@code 'USD'}). Legacy stores VND unless the
     *  invoice is explicitly USD (c002:414-419). */
    private static String trcoCcy(String raw) {
        return "USD".equals(EInvoiceDetailScrapeService.normalizeCcy(raw)) ? "USD" : "VND";
    }

    /** Strip currency labels / spaces / punctuation from a scraped account
     *  number (legacy {@code normalizeAcctNo}, c002:87-97). */
    static String normalizeAcctNo(String acctNo) {
        if (acctNo == null) {
            return "";
        }
        return acctNo.replace("VND", "")
                .replace("VNĐ", "")
                .replace("Số TK", "")
                .replace("SốTK", "")
                .replace(" ", "")
                .replace("(", "")
                .replace(")", "")
                .replace("-", "")
                .trim();
    }

    private static String right(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
    }

    private static String clip(String s, int max) {
        String v = trim(s);
        if (v.isEmpty()) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
