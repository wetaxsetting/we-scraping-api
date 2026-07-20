package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceDetailRow;
import com.kosign.wecms.domain.einvoice.entity.TrcoActvHstr;
import com.kosign.wecms.domain.einvoice.entity.TrcoRspbInfm;
import com.kosign.wecms.domain.einvoice.repository.TrcoActvHstrRepository;
import com.kosign.wecms.domain.einvoice.repository.TrcoRspbInfmRepository;
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
 * <p><b>Implemented 2026-07-14 (legacy-parity D5, PO-approved):</b>
 * <ul>
 *   <li><b>D5.1</b> — counterparty business address parsed on create into
 *       {@code TRCO_BSUN_ST} / {@code TRCO_BSUN_CITY} /
 *       {@code TRCO_BSUN_POST_ADRS} (see {@link #parseBusinessAddress}).</li>
 *   <li><b>D5.2a/D5.2b</b> — nickname derivation: SYS display-name
 *       normalisation via the ported {@code wb_get_normalized_trco_nick_nm}
 *       function (see {@link #companyNickBySystem}), then the initial "-NNN"
 *       rule + {@code #38284} tax-id-suffix de-dup ({@link #baseNickName} /
 *       {@link #uniqueNickNm}).</li>
 *   <li><b>D5.3</b> — representative columns ({@code TRCO_RPPR_ENM} /
 *       {@code TRCO_RPPR_NM}) set on create + back-filled on an existing
 *       contact, and a {@code TRCO_RSPB_INFM} responsible-person row written
 *       (create + if-absent, {@link #createRspb}). The representative /
 *       person-in-charge values come from the D3 Expense
 *       {@code BUSSINESS_REGISTRATION_DETAIL} enrichment.</li>
 * </ul>
 *
 * <p><b>Out of scope:</b> the BI09 contact-operation-status bridge — legacy
 * {@code comm_0800_001_u001} is {@code PrdtType.C/D}-gated + calls the external
 * OSMC big-data API, so it is inert for WeCMS product-type-F tenants (D4:
 * refactor-does-nothing IS parity). The payment-term due-date recompute
 * (D5.4, c002:691-711) lives in {@code EInvoiceDetailScrapeService} (it needs
 * the master + the contact's {@code TRCO_PAYM_TERM}), not here.
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
    private final TrcoRspbInfmRepository rspbRepo;
    private final LegacyFunctionRepository legacyFn;

    /** Resolved counterparty for one scraped invoice — the {@code TRCO_KEY}
     *  to stamp on the master, plus the contact's {@code TRCO_PAYM_TERM}
     *  (null for the ETC sentinel) which the detail-save needs for the D5.4
     *  due-date recompute. */
    public record CounterpartyResult(String trcoKey, String paymTerm) { }

    /**
     * Auto-register (or reuse) the counterparty for one scraped invoice
     * and write its activity-history row. Runs inside the caller's
     * transaction (REQUIRED) so it commits / rolls back with the
     * detail-save.
     *
     * @return the resolved {@code TRCO_KEY} (real contact, existing match,
     *         or the ETC sentinel) + the contact's payment term (null for ETC)
     */
    @Transactional
    public CounterpartyResult registerCounterparty(boolean isSales, String bizKey, String userId,
                                        String regDtm, String docId,
                                        ScrapedEInvoiceDetailRow row, BigDecimal totalAmt) {
        // Counterparty resolution by invoice type (legacy c002:210-215).
        final String trcoTaxId = trim(isSales ? row.buyerTaxCode()   : row.sellerTaxCode());
        final String cmnm      = trim(isSales ? row.buyerName()      : row.sellerName());
        final String bankNm    = trim(isSales ? row.buyerBankName()  : row.sellerBankName());
        final String acctNo    = trim(isSales ? row.buyerBankAcc()   : row.sellerBankAcc());
        // Counterparty business address (legacy c002:212 `_addr`): buyer for
        // SALES, seller for EXPENSE. Parsed + stored on create only (D5.1).
        final String addr      = trim(isSales ? row.buyerAddr()      : row.sellerAddr());
        // Business-registration enrichment (D3/#40477 → D5.3). Legacy reads
        // these from the SELLER-side registration lookup regardless of type
        // (c002:219-221); null on Sales / when not enriched.
        final String rpprEnm   = trim(row.sellerRpprEnm());   // legacy _trcoRpprEnm
        final String picName   = trim(row.sellerPicName());   // legacy _trcoPicName
        final String regName   = trim(row.trcoName());        // legacy _trcoName
        final String grpCd     = isSales ? "S" : "B";
        final String dvCd      = isSales ? "S" : "B";
        final String taskCd    = isSales ? "S003" : "B003";
        final String ccy       = trcoCcy(row.currencyCd());

        final String trcoKey;
        final String paymTerm;
        if (trcoTaxId.isEmpty()) {
            // ETC path — no tax id, no master/group/account rows.
            trcoKey = ETC_TRCO_KEY;
            paymTerm = null;
        } else {
            TrcoInfm contact = trcoInfmRepo
                    .findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(bizKey, trcoTaxId, ccy)
                    .orElse(null);
            if (contact == null) {
                contact = createContact(bizKey, userId, regDtm, trcoTaxId, cmnm, ccy,
                        addr, rpprEnm, regName);
                // Responsible-person row on create (legacy c002:483-485, D5.3).
                if (!picName.isEmpty()) {
                    createRspb(bizKey, contact.getTrcoKey(), picName, userId, regDtm);
                }
            } else {
                // Existing contact — representative back-fill (legacy c002:489-497):
                // only when BOTH representative columns are still blank.
                if (isBlank(contact.getTrcoRpprEnm()) && isBlank(contact.getTrcoRpprNm())
                        && !rpprEnm.isEmpty()) {
                    contact.setTrcoRpprEnm(clip(rpprEnm, 100));
                    contact.setTrcoRpprNm(clip(rpprEnm, 100));
                    contact.setModId(userId);
                    contact.setModDtm(regDtm);
                    trcoInfmRepo.saveAndFlush(contact);
                }
                // Responsible-person row only if none exists yet (legacy c002:498-501).
                if (!picName.isEmpty()
                        && !rspbRepo.existsByBizKeyAndTrcoKey(bizKey, contact.getTrcoKey())) {
                    createRspb(bizKey, contact.getTrcoKey(), picName, userId, regDtm);
                }
            }
            trcoKey = contact.getTrcoKey();
            paymTerm = contact.getTrcoPaymTerm();
            seedGroupLink(bizKey, grpCd, trcoKey, contact.getTaxId(), regDtm);
            upsertAccounts(bizKey, trcoKey, bankNm, acctNo, userId, regDtm);
        }

        writeActivityHistory(bizKey, userId, regDtm, docId, trcoKey, cmnm, ccy,
                dvCd, taskCd, totalAmt);
        return new CounterpartyResult(trcoKey, paymTerm);
    }

    /* ------------------------------------------------------------------ */

    /** Create a system contact (legacy {@code c002:350-486}). */
    private TrcoInfm createContact(String bizKey, String userId, String regDtm,
                                   String trcoTaxId, String cmnm, String ccy, String addr,
                                   String rpprEnm, String regName) {
        final String trcoKey = legacyFn.nextTrcoKey(bizKey);
        // Display name: company name, else the registration company name
        // (legacy c002:476 trcoName fallback), else the tax id (NOT-NULL safety).
        final String display = !cmnm.isEmpty()
                ? cmnm
                : (!trim(regName).isEmpty() ? trim(regName) : trcoTaxId);

        TrcoInfm c = new TrcoInfm();
        c.setTrcoKey(trcoKey);
        c.setBizKey(bizKey);
        c.setTaxId(trcoTaxId);                 // TAX_ID — NOT NULL
        c.setTrcoCmnm(clip(display, 200));
        c.setTrcoCmenm(clip(display, 200));
        c.setTrcoTaxId(trcoTaxId);
        c.setTrscCcyCd(ccy);                   // CHECK IN ('VND','USD')
        c.setTrcoInqYn("N");                   // legacy YesOrNo.NO (system contact)
        // Nickname (D5.2b): SYS display-name normalisation (on the original
        // company name) first, else the "-NNN" + #38284 tax-id fallback.
        c.setTrcoNickNm(uniqueNickNm(bizKey, cmnm, display, trcoTaxId, trcoKey, ccy));
        // Representative (D5.3, legacy c002:469-473) — both cols = the
        // registration chairman name when the lookup provided one.
        if (!trim(rpprEnm).isEmpty()) {
            c.setTrcoRpprEnm(clip(rpprEnm, 100));
            c.setTrcoRpprNm(clip(rpprEnm, 100));
        }
        // Business address (D5.1) — parse the scraped comma-separated
        // address into the legacy WbAddr shape (st / city / postAdrs).
        final String[] a = parseBusinessAddress(addr);
        c.setTrcoBsunSt(clip(a[0], 200));
        c.setTrcoBsunCity(clip(a[1], 200));
        c.setTrcoBsunPostAdrs(clip(a[2], 400));
        c.setTrcoBsunDtlAdrs(clip(a[3], 400));
        c.setRegId(userId);
        c.setRegDtm(regDtm);
        c.setModId(userId);
        c.setModDtm(regDtm);
        // saveAndFlush so a second invoice in the same batch that resolves
        // the same contact finds it instead of re-creating it.
        return trcoInfmRepo.saveAndFlush(c);
    }

    /** Create a responsible-person row (legacy {@code createTrcoRspbInfm},
     *  c002:120-125): only {@code NAME} is set from the registration
     *  accountant (person-in-charge); every other field stays null. RSPB_KEY
     *  via the {@code wb_get_next_val('trco_rspb_infm','rspb_key')} funnel. */
    private void createRspb(String bizKey, String trcoKey, String picName,
                            String userId, String regDtm) {
        TrcoRspbInfm r = new TrcoRspbInfm();
        r.setRspbKey(legacyFn.nextRspbKey());
        r.setBizKey(bizKey);
        r.setTrcoKey(trcoKey);
        r.setName(clip(picName, 50));
        r.setRegId(userId);
        r.setRegDtm(regDtm);
        rspbRepo.saveAndFlush(r);
    }

    /**
     * SYS display-name normalisation (legacy {@code getCompanyNickNmBySystem},
     * c002:99-112) via the ported {@code wb_get_normalized_trco_nick_nm}
     * function (D5.2b). Returns a distinct normalised nickname (USD gets the
     * legacy {@code "[USD] "} prefix) or {@code null} when there is no company
     * name / the function returns the name unchanged, so the caller falls back
     * to the tax-id rule. The refactor clamps {@code ccy} to VND/USD before
     * this point, so legacy's {@code CcyCd.ETC} {@code "[ETC] "} branch is
     * unreachable here.
     */
    private String companyNickBySystem(String cmnm, String ccy) {
        final String name = trim(cmnm);
        if (name.isEmpty()) {
            return null;
        }
        final String norm = legacyFn.getNormalizedTrcoNickNm(name);
        if (norm == null || norm.equals(name)) {
            return null;
        }
        return "USD".equals(ccy) ? "[USD] " + norm : norm;
    }

    /**
     * Build a nickname that satisfies {@code trco_infm_uk01}
     * UNIQUE({@code BIZ_KEY}, {@code TRCO_NICK_NM}). Applies the legacy
     * {@code SYS_TRCO_INFM_DIS_NM} display-name normalisation first (D5.2b,
     * see {@link #companyNickBySystem}), then the initial "-NNN" rule
     * ({@link #baseNickName}) + the tax-id-suffix de-dup ({@code c002:451-467}).
     */
    private String uniqueNickNm(String bizKey, String cmnm, String display,
                                String taxId, String trcoKey, String ccy) {
        // D5.2b — SYS display-name normalisation first (legacy
        // getCompanyNickNmBySystem on the ORIGINAL company name); else the
        // initial "-NNN" branch-tax-id rule on the display name (legacy
        // c002:428-435).
        final String sysNick = companyNickBySystem(cmnm, ccy);
        String base = clip(sysNick != null ? sysNick : baseNickName(display, taxId), 200);
        if (!trcoInfmRepo.existsByBizKeyAndTrcoNickNm(bizKey, base)) {
            return base;
        }
        // Duplicate → tax-id-suffix de-dup (#38284, legacy c002:451-467),
        // appending to the CURRENT nickname (base, incl. any "-NNN" suffix).
        if (!taxId.isEmpty()) {
            String suffix = (taxId.length() >= 8 && taxId.length() > 10)
                    ? right(taxId, 8)
                    : (taxId.length() >= 4 && taxId.length() <= 10 ? right(taxId, 4) : null);
            if (suffix != null) {
                String withSuffix = clip(base + " " + suffix, 200);
                if (!trcoInfmRepo.existsByBizKeyAndTrcoNickNm(bizKey, withSuffix)) {
                    return withSuffix;
                }
            }
        }
        // Refactor hardening: the global TRCO_KEY guarantees uniqueness.
        // Lead with the key so it is never lost to the 200-char clip.
        return clip(trcoKey + " " + display, 200);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Legacy initial nickname derivation (c002:428-435): a branch tax id
     * whose last four characters are a dash + three digits ({@code "-NNN"})
     * appends that 3-digit branch suffix to the company name; otherwise the
     * plain company name. This is the fallback used only when the
     * {@code SYS_TRCO_INFM_DIS_NM} normalisation ({@link #companyNickBySystem},
     * D5.2b) yields nothing. Package-private for unit testing.
     */
    static String baseNickName(String name, String taxId) {
        final String n = trim(name);
        final String t = trim(taxId);
        if (!t.isEmpty() && t.length() >= 4 && right(t, 4).matches("-[0-9]{3}")) {
            return n + " " + right(t, 3);
        }
        return n;
    }

    /**
     * Parse a scraped comma-separated business address into the legacy
     * {@code WbAddr} shape {@code [st, city, postAdrs, dtlAdrs]}
     * (legacy c002:361-411, counterparty create branch). Rules:
     * <ul>
     *   <li>Blank input → all {@code null} (mirrors the legacy #39847
     *       empty-address guard, c002:716-731 — no misleading "Việt Nam"
     *       stamped on a contact with no address).</li>
     *   <li>&ge; 5 comma parts → {@code st="Việt Nam"}; when the last part
     *       is {@code "Việt Nam"} the city = {@code parts[n-3] + ", " +
     *       parts[n-2]} and the postal line = {@code parts[0..n-4]}, else
     *       city = {@code parts[n-2] + ", " + parts[n-1]} and postal line =
     *       {@code parts[0..n-3]}; {@code dtlAdrs} stays empty (→ null).</li>
     *   <li>&lt; 5 parts → {@code st="Việt Nam"}, whole string as the
     *       postal line, no city.</li>
     * </ul>
     * Package-private for unit testing. Never returns {@code "null"} /
     * {@code "undefined"} literals; whitespace is normalised.
     */
    static String[] parseBusinessAddress(String addr) {
        final String v = trim(addr);
        if (v.isEmpty()) {
            return new String[] { null, null, null, null };
        }
        final String[] parts = v.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        final String st = "Việt Nam";
        String city = null;
        String postAdrs;
        if (parts.length >= 5) {
            final int n = parts.length;
            final boolean vnTrailer = "Việt Nam".equals(parts[n - 1]);
            final int cityLo = vnTrailer ? n - 3 : n - 2;
            final int cityHi = vnTrailer ? n - 2 : n - 1;
            city = parts[cityLo] + ", " + parts[cityHi];
            final int postEnd = vnTrailer ? n - 3 : n - 2; // exclusive
            final StringBuilder sb = new StringBuilder(parts[0]);
            for (int i = 1; i < postEnd; i++) {
                sb.append(", ").append(parts[i]);
            }
            postAdrs = sb.toString();
        } else {
            postAdrs = String.join(", ", parts);
        }
        return new String[] { st, emptyToNull(city), emptyToNull(postAdrs), null };
    }

    private static String emptyToNull(String s) {
        final String v = trim(s);
        return v.isEmpty() ? null : v;
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

    /** Per-{@code (BIZ_KEY, TRCO_KEY)} next {@code TRCO_ACCT_KEY} via the single
     *  wb_get_next_val funnel (V140 #31 — per (biz_key, trco_key) MAX+1, verbatim
     *  from the live JEX function) so it never collides with the live JEX /
     *  WABOOKS systems that draw the same key from the same function. */
    private String nextAcctKey(String bizKey, String trcoKey) {
        return legacyFn.nextTrcoAcctKey(bizKey, trcoKey);
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
        // ACTV_SEQ via the single wb_get_next_val funnel (V140 #6 — JEX backs
        // this with the GLOBAL sq_actv_seq sequence).
        h.setActvSeq(legacyFn.nextTrcoActvSeq());
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
