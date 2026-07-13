package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.tx.TransactionalRetry;
import com.kosign.wecms.domain.payment.common.OutBankCdNormalizer;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineInput;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Draft-save persistence parity for {@link ExpensePaymentService#createDraft}
 * — audit G1 (contact identity persisted), G4 (WDRL_CCY_CD = withdrawal
 * account currency, distinct from the transfer currency), and G5
 * (save-side contact re-derivation from HAND_TRCO_KEY).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpensePaymentCreateDraftTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";

    @Mock private ScdulPymnSumrRepository sumrRepository;
    @Mock private ScdulPymnSumrDetlRepository detlRepository;
    @Mock private com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferKeyGenerator keyGenerator;
    @Mock private TransactionalRetry transactionalRetry;
    @Mock private LegacyFunctionRepository legacyFunctions;
    @Mock private AuditLogService auditLogService;
    @Mock private OutBankCdNormalizer outBankCdNormalizer;
    @Mock private com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard blockedCharGuard;
    @Mock private TrcoInfmRepository contactRepo;
    @Mock private Clock clock;

    @InjectMocks private ExpensePaymentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER_ID, "Tester", BIZ_KEY, LangCd.EN);
        when(transactionalRetry.runOnce(any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(clock.instant()).thenReturn(Instant.parse("2026-06-29T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(keyGenerator.nextScdulPymnNo(BIZ_KEY)).thenReturn("20260629000001");
        when(legacyFunctions.nextScdulPymnDetlNo(BIZ_KEY)).thenReturn("1");
        when(outBankCdNormalizer.normalize("11704004")).thenReturn("970424");
        when(sumrRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createDraft_persistsContactIdentity_andWithdrawalCurrency() {
        // Request carries the full contact identity (the post-G1 frontend
        // forwards all four) AND a distinct withdrawal-account currency.
        TransferLineInput line = line(
                "TK001", "C001", "Expense Co VN", "Expense Co EN");

        CreateScheduledTransferRequest req = new CreateScheduledTransferRequest(
                "Expense_260629",            // titl
                "11704004",                  // bank_cd (internal → NAPAS)
                "001234567",                 // acct_no
                "VND",                       // ccy_cd  (transfer currency)
                "VND",                       // trnf_ccy_cd
                "USD",                       // wdrl_ccy_cd (account currency — distinct)
                List.of(line));

        CreateScheduledTransferResponse resp = service.createDraft(req, null);
        assertThat(resp.scdulPymnNo()).isEqualTo("20260629000001");

        ScdulPymnSumr master = captureMaster();
        assertThat(master.getScdulPymnTypeCd()).isEqualTo("02");   // EXPENSE
        assertThat(master.getCcyCd()).isEqualTo("VND");
        assertThat(master.getWdrlCcyCd()).isEqualTo("USD");        // G4

        ScdulPymnSumrDetl detl = captureDetail();
        // G1 — all four contact-identity columns persisted from the request.
        assertThat(detl.getTrcoKey()).isEqualTo("TK001");
        assertThat(detl.getHandTrcoKey()).isEqualTo("C001");
        assertThat(detl.getTrcoCmnm()).isEqualTo("Expense Co VN");
        assertThat(detl.getTrcoCmenm()).isEqualTo("Expense Co EN");
    }

    @Test
    void createDraft_fallsBackToCcyCd_whenWdrlCcyCdMissing() {
        // 6-arg constructor (no wdrl_ccy_cd) → WDRL_CCY_CD falls back to ccy_cd.
        CreateScheduledTransferRequest req = new CreateScheduledTransferRequest(
                "Expense_260629", "11704004", "001234567", "VND", "VND",
                List.of(line("TK001", "C001", "Expense Co VN", "Expense Co EN")));

        service.createDraft(req, null);

        assertThat(captureMaster().getWdrlCcyCd()).isEqualTo("VND");
    }

    @Test
    void createDraft_reDerivesContactIdentity_whenRequestDropsIt() {
        // G5 — stale/crafted request carries only HAND_TRCO_KEY; the service
        // re-derives TRCO_KEY / TRCO_CMNM / TRCO_CMENM from TRCO_INFM.
        when(contactRepo.findActiveByBizKeyAndHandTrcoKey(BIZ_KEY, "C001"))
                .thenReturn(Optional.of(contact("TK999", "Re-derived VN", "Re-derived EN")));

        CreateScheduledTransferRequest req = new CreateScheduledTransferRequest(
                "Expense_260629", "11704004", "001234567", "VND", "VND", "VND",
                List.of(line(null, "C001", null, null)));

        service.createDraft(req, null);

        ScdulPymnSumrDetl detl = captureDetail();
        assertThat(detl.getTrcoKey()).isEqualTo("TK999");
        assertThat(detl.getTrcoCmnm()).isEqualTo("Re-derived VN");
        assertThat(detl.getTrcoCmenm()).isEqualTo("Re-derived EN");
    }

    @Test
    void createDraft_rejects_whenContactCodeUnresolvable() {
        // G5 — HAND_TRCO_KEY present but unknown → reject rather than save a
        // null contact identity.
        when(contactRepo.findActiveByBizKeyAndHandTrcoKey(BIZ_KEY, "C999"))
                .thenReturn(Optional.empty());

        CreateScheduledTransferRequest req = new CreateScheduledTransferRequest(
                "Expense_260629", "11704004", "001234567", "VND", "VND", "VND",
                List.of(line(null, "C999", null, null)));

        assertThatThrownBy(() -> service.createDraft(req, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_VALIDATION");
    }

    /* ---------------------------------------------------------------- */

    private ScdulPymnSumr captureMaster() {
        ArgumentCaptor<ScdulPymnSumr> c = ArgumentCaptor.forClass(ScdulPymnSumr.class);
        verify(sumrRepository).saveAndFlush(c.capture());
        return c.getValue();
    }

    @SuppressWarnings("unchecked")
    private ScdulPymnSumrDetl captureDetail() {
        ArgumentCaptor<List<ScdulPymnSumrDetl>> c = ArgumentCaptor.forClass(List.class);
        verify(detlRepository).saveAll(c.capture());
        assertThat(c.getValue()).hasSize(1);
        return c.getValue().get(0);
    }

    private static TransferLineInput line(String trcoKey, String handTrcoKey,
                                          String cmnm, String cmenm) {
        return new TransferLineInput(
                new BigDecimal("100000"),    // tranAmt
                "NAPAS",                      // antDivCd
                "Recipient",                  // rcptNm
                "970424",                     // tarBankCd
                "700071152854",               // fincMdaNo
                "Expense payment",            // tranUsag
                trcoKey,                      // trcoKey
                handTrcoKey,                  // handTrcoKey
                "DOC-1",                      // docNo
                "note",                       // note
                "000",                        // citadBankCd
                cmnm,                         // trcoCmnm
                cmenm,                        // trcoCmenm
                null,                         // chartAcctSeqNo
                null,                         // chartAcctCd
                "VND",                        // trnfCcyCd
                "VND");                       // depoCcyCd
    }

    private static TrcoInfm contact(String trcoKey, String cmnm, String cmenm) {
        TrcoInfm c = new TrcoInfm();
        c.setTrcoKey(trcoKey);
        c.setTrcoCmnm(cmnm);
        c.setTrcoCmenm(cmenm);
        return c;
    }
}
