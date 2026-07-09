package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.memo.MemoService;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionRow;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TH-01 — Vietcombank (BANK_CD '11704004') running-balance reconstruction.
 *
 * <p>Legacy source of truth: {@code IDO.ACCT_TRNS_HSTR_R016.xml:42-73}
 * (issue #42771). VCB's scrape feed leaves {@code ACCT_TRNS_HSTR.BAL} NULL on
 * ~99% of rows, so the displayed running balance is reconstructed from
 * {@code ACCT_MSTR.BAL} minus the signed sum of every later transaction. Stored
 * balances win when present (legacy COALESCE), and all non-VCB banks keep using
 * {@code ATH.BAL} directly.
 *
 * <p>The four required cases:
 * <ol>
 *   <li>VCB row with NULL BAL reconstructs from {@code ACCT_MSTR.BAL} − later signed txns.</li>
 *   <li>VCB row with BAL present uses the stored {@code ATH.BAL}.</li>
 *   <li>Non-VCB row uses {@code ATH.BAL} directly (no reconstruction).</li>
 *   <li>Same-date ordering uses {@code numeric(ACCT_SEQ_NO)}, not string comparison.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceVcbBalanceTest {

    @Mock AcctTrnsHstrRepository       txnRepo;
    @Mock AcctTrnsRcvPayInfmRepository rcvPayRepo;
    @Mock ChartOfAccountRepository     chartAcctRepo;
    @Mock PurposeSettingsRepository    purposeRepo;
    @Mock BusiVasAcctInfmRepository    vasAcctRepo;
    @Mock TrcoInfmRepository           contactRepo;
    @Mock AcctMstrRepository           acctRepo;
    @Mock BusiMatchLogRepository       matchLogRepo;
    @Mock ScdulPymnSumrRepository      scdulPymnSumrRepo;
    @Mock UserInfmRepository           userRepo;
    @Mock AccountAccessGuard           accountAccessGuard;
    @Mock LegacyFunctionRepository     legacyFunctions;
    @Mock MemoService                 memoService;

    TransactionService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String VCB  = "11704004";
    private static final String VCB_ACCT = "001065042693";

    @BeforeEach
    void setUp() {
        service = new TransactionService(
                txnRepo, rcvPayRepo, chartAcctRepo, purposeRepo, vasAcctRepo,
                contactRepo, acctRepo, matchLogRepo, scdulPymnSumrRepo, userRepo,
                accountAccessGuard, legacyFunctions, memoService);
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ---- Pure reconstruction kernel (deterministic, no mocks) ---------- */

    /** Case 1 — NULL BAL row = ACCT_MSTR.BAL minus the signed sum of later transactions. */
    @Test
    void kernel_nullBal_reconstructsFromMasterMinusLaterSignedTxns() {
        // base = 1000; one later deposit of +200 ⇒ target row = 1000 − 200 = 800.
        List<AcctTrnsHstr> ledger = List.of(
                txn("100", "20260103", "1", "200", null),   // latest deposit, no stored bal
                txn("10",  "20260101", "1", "500", null));   // target row, no stored bal

        Map<String, BigDecimal> bal =
                TransactionService.reconstructVcbBalances(ledger, new BigDecimal("1000"));

        assertThat(bal.get("100")).isEqualByComparingTo("1000"); // latest = master balance
        assertThat(bal.get("10")).isEqualByComparingTo("800");   // 1000 − 200 (later deposit)
    }

    /** Case 2 — a row that already carries a stored BAL keeps it (legacy COALESCE). */
    @Test
    void kernel_storedBal_isUsedVerbatim() {
        List<AcctTrnsHstr> ledger = List.of(
                txn("100", "20260103", "1", "200", null),          // later row, null bal
                txn("10",  "20260101", "1", "500", "12345.00"));    // target row WITH stored bal

        Map<String, BigDecimal> bal =
                TransactionService.reconstructVcbBalances(ledger, new BigDecimal("1000"));

        // Stored value wins; it is NOT overwritten by 1000 − 200 = 800.
        assertThat(bal.get("10")).isEqualByComparingTo("12345.00");
    }

    /** Case 4 — same TRSC_DT must order by numeric(ACCT_SEQ_NO), not string. */
    @Test
    void kernel_sameDate_ordersByNumericSeqNotString() {
        // seq "100" is numerically AFTER seq "9" (string-wise "100" < "9").
        // Both same date. base = 1000; later row "100" is a +50 deposit.
        List<AcctTrnsHstr> ledger = List.of(
                txn("9",   "20260101", "1", "300", null),   // target row
                txn("100", "20260101", "1", "50",  null));  // numerically later (+50)

        Map<String, BigDecimal> bal =
                TransactionService.reconstructVcbBalances(ledger, new BigDecimal("1000"));

        // Numeric ordering ⇒ "100" is later ⇒ row "9" = 1000 − 50 = 950.
        // String ordering would (wrongly) treat "100" as earlier ⇒ 1000.
        assertThat(bal.get("9")).isEqualByComparingTo("950");
        assertThat(bal.get("100")).isEqualByComparingTo("1000"); // numerically latest = master
    }

    /** Withdrawals subtract (INOUT_DV != '1' ⇒ −TRSC_AMT in the running sum). */
    @Test
    void kernel_withdrawLaterTxn_signedNegative() {
        // base = 1000; later withdraw 300 ⇒ target = 1000 − (−300) = 1300.
        List<AcctTrnsHstr> ledger = List.of(
                txn("20", "20260102", "2", "300", null),   // later WITHDRAW
                txn("10", "20260101", "1", "500", null));   // target row

        Map<String, BigDecimal> bal =
                TransactionService.reconstructVcbBalances(ledger, new BigDecimal("1000"));

        assertThat(bal.get("10")).isEqualByComparingTo("1300");
        assertThat(bal.get("20")).isEqualByComparingTo("1000");
    }

    /* ---- Service-level wiring (mocked repositories) -------------------- */

    /** Case 1 end-to-end — list() on a VCB account returns reconstructed balances. */
    @Test
    void list_vcbAccount_returnsReconstructedBalances() {
        stubAccount(VCB, VCB_ACCT, "VND", new BigDecimal("1000"));
        stubLedger(VCB, VCB_ACCT, List.of(
                txn("100", "20260103", "1", "200", null),
                txn("50",  "20260102", "2", "300", null),
                txn("10",  "20260101", "1", "500", null)));

        TransactionListResponse res = service.list(VCB + "_" + VCB_ACCT, null, null, null);

        Map<String, String> balBySeq = res.rows().stream()
                .collect(Collectors.toMap(TransactionRow::acctSeqNo, TransactionRow::bal));
        assertThat(balBySeq.get("100")).isEqualTo(AmountFormatter.wire(new BigDecimal("1000")));
        assertThat(balBySeq.get("50")).isEqualTo(AmountFormatter.wire(new BigDecimal("800")));
        assertThat(balBySeq.get("10")).isEqualTo(AmountFormatter.wire(new BigDecimal("1100")));
    }

    /** Case 3 — list() on a non-VCB account uses ATH.BAL directly (no reconstruction). */
    @Test
    void list_nonVcbAccount_usesStoredBalDirectly() {
        String bank = "11704088";
        String acct = "5280123456";
        // Master balance set to a value that would change results IF the
        // reconstruction (wrongly) applied — it must NOT for a non-VCB bank.
        stubAccount(bank, acct, "VND", new BigDecimal("999999"));
        stubLedger(bank, acct, List.of(
                txn("2", "20260103", "1", "200", "777.00"),
                txn("1", "20260101", "1", "500", "500.00")));

        TransactionListResponse res = service.list(bank + "_" + acct, null, null, null);

        Map<String, String> balBySeq = res.rows().stream()
                .collect(Collectors.toMap(TransactionRow::acctSeqNo, TransactionRow::bal));
        assertThat(balBySeq.get("2")).isEqualTo(AmountFormatter.wire(new BigDecimal("777.00")));
        assertThat(balBySeq.get("1")).isEqualTo(AmountFormatter.wire(new BigDecimal("500.00")));
    }

    /* ---- fixtures ------------------------------------------------------ */

    private void stubAccount(String bankCd, String acctNo, String ccyCd, BigDecimal masterBal) {
        AcctMstr m = new AcctMstr();
        m.setBizKey(BIZ);
        m.setBankCd(bankCd);
        m.setAcctNo(acctNo);
        m.setCcyCd(ccyCd);
        m.setBal(masterBal);
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, bankCd, acctNo))
                .thenReturn(Optional.of(m));
    }

    private void stubLedger(String bankCd, String acctNo, List<AcctTrnsHstr> rows) {
        when(txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(BIZ, bankCd, acctNo))
                .thenReturn(rows);
    }

    private static AcctTrnsHstr txn(String acctSeqNo, String trscDt, String inoutDv,
                                    String trscAmt, String storedBal) {
        AcctTrnsHstr h = new AcctTrnsHstr();
        h.setBizKey(BIZ);
        h.setBankCd(VCB);            // overwritten where the test needs another bank
        h.setAcctSeqNo(acctSeqNo);
        h.setTrscDt(trscDt);
        h.setTrscTm("120000");
        h.setCcyCd("VND");
        h.setInoutDv(inoutDv);
        h.setTrscAmt(new BigDecimal(trscAmt));
        h.setBal(storedBal == null ? null : new BigDecimal(storedBal));
        return h;
    }
}
