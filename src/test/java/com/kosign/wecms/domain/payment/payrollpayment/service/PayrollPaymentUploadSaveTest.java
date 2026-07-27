package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.tx.TransactionalRetry;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalAttachmentWriter;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxService;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalDisplayFlagsResolver;
import com.kosign.wecms.domain.payment.common.OutBankCdNormalizer;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.common.WithdrawalAccountListService;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineInput;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.service.ApprovalAttachmentLoader;
import com.kosign.wecms.domain.payment.internaltransfer.service.ApprovalContentLoader;
import com.kosign.wecms.domain.payment.internaltransfer.service.CutoffValidator;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferKeyGenerator;
import com.kosign.wecms.domain.payment.notification.service.ApprovalNotificationService;
import com.kosign.wecms.domain.payment.service.DocNoGenerator;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
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
import org.springframework.context.ApplicationEventPublisher;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Upload-save unit tests for {@link PayrollPaymentService#createDraft} — the
 * four Tier-1 audit fixes:
 * <ul>
 *   <li>G1 — Remarks persist to {@code WDRL_ACCT_RMRK}, not {@code TRAN_USAG}.</li>
 *   <li>G2 — {@code TAR_BANK_CD} resolved to the 6-digit legacy {@code BANK_CD}.</li>
 *   <li>G3 — {@code ANT_DIV_CD} forced to {@code CITAD}.</li>
 *   <li>G4 — VCB payroll prohibitions enforced at upload save.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollPaymentUploadSaveTest {

    private static final String TEST_BIZ_KEY = "VN_VNM0004_000001";
    private static final String NON_VCB_NAPAS = "970424";  // Shinhan
    private static final String VCB_NAPAS      = "970436";  // Vietcombank
    private static final String CITAD_SHINHAN     = "616";
    private static final String CITAD_SHINHAN_CD6 = "01616001";
    private static final String WDRL_BANK_CD      = "11704004";
    private static final String WDRL_ACCT_NO      = "123456789";
    /** Withdrawal account's own currency — deliberately ≠ the files' transfer currency. */
    private static final String WDRL_ACCOUNT_CCY  = "USD";

    @Mock ScdulPymnSumrRepository sumrRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ScdulPymnSumrDetlRepository detlRepository;
    @Mock WithdrawalAccountListService withdrawalAccountListService;
    @Mock CutoffValidator cutoffValidator;
    @Mock PayrollPaymentExcelParser excelParser;
    @Mock InternalTransferKeyGenerator keyGenerator;
    @Mock TransactionalRetry transactionalRetry;
    @Mock PayrollPaymentFileResolver fileResolver;
    @Mock ErpTrnsHistoryWriter erpHistoryWriter;
    @Mock AuditLogService auditLogService;
    @Mock PayrollDispatchExecutor payrollDispatchExecutor;
    @Mock ApprovalBoxService approvalBoxService;
    @Mock TransferLineDetailMapper transferLineDetailMapper;
    @Mock OutBankCdNormalizer outBankCdNormalizer;
    @Mock com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard blockedCharGuard;
    @Mock ApprovalDisplayFlagsResolver displayFlagsResolver;
    @Mock ApprovalAttachmentWriter approvalAttachmentWriter;
    @Mock ApprovalBoxFanoutService approvalBoxFanoutService;
    @Mock ApprovalNotificationService approvalNotificationService;
    @Mock Clock clock;
    @Mock ExptResolMstrRepository exptResolMstrRepository;
    @Mock ExptResolApprvrInfmRepository exptResolApprvrInfmRepository;
    @Mock ApprovalAttachmentLoader approvalAttachmentLoader;
    @Mock ApprovalContentLoader approvalContentLoader;
    @Mock DocNoGenerator docNoGenerator;
    @Mock BusiInfmRepository busiInfmRepository;
    @Mock LegacyFunctionRepository legacyFunctions;
    @Mock TbFirmBankCodeRepository firmBankCodeRepo;
    @Mock com.kosign.wecms.domain.banking.repository.AcctMstrRepository acctMstrRepository;

    /** Storage root for the mandatory uploaded workbook (legacy requires the file
     *  at save — {@code PaymentUploadFileGuard}). */
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path storageRoot;

    @InjectMocks PayrollPaymentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("testuser", "testuser", TEST_BIZ_KEY, LangCd.EN);
        when(clock.instant()).thenReturn(Instant.parse("2026-06-29T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        // Run the unit of work inline (no real transaction/retry).
        when(transactionalRetry.runOnce(any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(keyGenerator.nextScdulPymnNo(anyString())).thenReturn("PAY00001");
        when(legacyFunctions.nextScdulPymnDetlNo(anyString())).thenReturn("1");
        // Default: non-VCB withdrawal bank (VCB tests override).
        when(outBankCdNormalizer.normalize(anyString())).thenReturn(NON_VCB_NAPAS);
        // CITAD 616 → 6-digit BANK_CD 01616001 (legacy getBankCode CITAD branch).
        when(firmBankCodeRepo.findFirstByAntDivCdAndCitadBankCd("CITAD", CITAD_SHINHAN))
                .thenReturn(Optional.of(citad(CITAD_SHINHAN_CD6)));
        // The withdrawal account pays in USD while the test files transfer VND —
        // the exact case where WDRL_CCY_CD must NOT be the transfer currency
        // (legacy pymn_0005_002_c001_act.jsp:84 reads ACCT_MSTR.CCY_CD).
        when(acctMstrRepository.findByBizKeyAndBankCdAndAcctNo(
                TEST_BIZ_KEY, WDRL_BANK_CD, WDRL_ACCT_NO))
                .thenReturn(Optional.of(acct(WDRL_ACCOUNT_CCY)));
        when(fileResolver.resolve(anyString(), anyString()))
                .thenAnswer(inv -> storageRoot.resolve((String) inv.getArgument(1)));
    }

    /** The uploaded workbook is mandatory at save (legacy
     *  {@code pymn_0005_002_c001} {@code validateRequired(FILE_DATA)}). */
    private static org.springframework.mock.web.MockMultipartFile xlsx() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file_data", "Payroll_Dec.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] { 1, 2, 3 });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- G1 + G2 + G3 — column mapping on save ---------------------------

    @Test
    void persists_remarks_to_wdrl_acct_rmrk_forces_citad_and_resolves_bank_code() {
        service.createDraft(req("VND", CITAD_SHINHAN, "Webcash VN salary"), xlsx());

        ScdulPymnSumrDetl saved = captureSavedDetail();
        // G1 — Remarks land in WDRL_ACCT_RMRK; TRAN_USAG stays null.
        assertThat(saved.getWdrlActRmrk()).isEqualTo("Webcash VN salary");
        assertThat(saved.getTranUsag()).isNull();
        // G3 — network forced to CITAD.
        assertThat(saved.getAntDivCd()).isEqualTo("CITAD");
        // G2 — typed CITAD code resolved to the 6-digit BANK_CD.
        assertThat(saved.getTarBankCd()).isEqualTo(CITAD_SHINHAN_CD6);
    }

    /**
     * Legacy parity (pymn_0005_002_c001_act.jsp:78,84): {@code CCY_CD} is the
     * TRANSFER currency, {@code WDRL_CCY_CD} is the WITHDRAWAL ACCOUNT's own
     * currency from {@code ACCT_MSTR}. The payroll frontend sends
     * {@code ccy_cd = trnf_ccy_cd}, so the two columns must still differ when the
     * account pays in another currency.
     */
    @Test
    void wdrl_ccy_cd_is_the_withdrawal_account_currency_not_the_transfer_currency() {
        service.createDraft(req("VND", CITAD_SHINHAN, null), xlsx());

        ArgumentCaptor<com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr> captor =
                ArgumentCaptor.forClass(
                        com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr.class);
        verify(sumrRepository).saveAndFlush(captor.capture());
        com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr master = captor.getValue();

        assertThat(master.getCcyCd()).isEqualTo("VND");                 // transfer currency
        assertThat(master.getWdrlCcyCd()).isEqualTo(WDRL_ACCOUNT_CCY);  // ACCT_MSTR.CCY_CD
    }

    /** Defensive fallback: unresolvable account → keep the transfer currency. */
    @Test
    void wdrl_ccy_cd_falls_back_to_transfer_currency_when_account_not_resolved() {
        when(acctMstrRepository.findByBizKeyAndBankCdAndAcctNo(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        service.createDraft(req("VND", CITAD_SHINHAN, null), xlsx());

        ArgumentCaptor<com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr> captor =
                ArgumentCaptor.forClass(
                        com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr.class);
        verify(sumrRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getWdrlCcyCd()).isEqualTo("VND");
    }

    // ---- G4 — VCB prohibitions ------------------------------------------

    @Test
    void blocks_vcb_vnd_payroll() {
        when(outBankCdNormalizer.normalize(anyString())).thenReturn(VCB_NAPAS);

        assertThatThrownBy(() -> service.createDraft(req("VND", CITAD_SHINHAN, null), xlsx()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("WBE_PAYROLL_VCB_VND_NOT_SUPPORTED");
    }

    @Test
    void blocks_vcb_usd_to_non_vcb_target() {
        when(outBankCdNormalizer.normalize(anyString())).thenReturn(VCB_NAPAS);
        // CITAD 616 resolves to 01616001 (≠ VCB) → blocked.
        assertThatThrownBy(() -> service.createDraft(req("USD", CITAD_SHINHAN, null), xlsx()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("WBE_PAYROLL_VCB_USD_OTHER_BANK");
    }

    @Test
    void allows_vcb_usd_to_vcb_target() {
        when(outBankCdNormalizer.normalize(anyString())).thenReturn(VCB_NAPAS);
        // Typed VCB code: CITAD lookup misses → resolver keeps "970436" = VCB → allowed.
        when(firmBankCodeRepo.findFirstByAntDivCdAndCitadBankCd("CITAD", VCB_NAPAS))
                .thenReturn(Optional.empty());

        service.createDraft(req("USD", VCB_NAPAS, null), xlsx());

        ScdulPymnSumrDetl saved = captureSavedDetail();
        assertThat(saved.getTarBankCd()).isEqualTo(VCB_NAPAS);
    }

    /* ----------------------------------------------------------------
     *  Helpers
     * ---------------------------------------------------------------- */

    private ScdulPymnSumrDetl captureSavedDetail() {
        ArgumentCaptor<ScdulPymnSumrDetl> captor = ArgumentCaptor.forClass(ScdulPymnSumrDetl.class);
        verify(detlRepository).save(captor.capture());
        return captor.getValue();
    }

    private static com.kosign.wecms.domain.banking.entity.AcctMstr acct(String ccyCd) {
        com.kosign.wecms.domain.banking.entity.AcctMstr a =
                new com.kosign.wecms.domain.banking.entity.AcctMstr();
        a.setBizKey(TEST_BIZ_KEY);
        a.setBankCd(WDRL_BANK_CD);
        a.setAcctNo(WDRL_ACCT_NO);
        a.setCcyCd(ccyCd);
        return a;
    }

    private static TbFirmBankCode citad(String bankCd6) {
        TbFirmBankCode b = new TbFirmBankCode();
        b.setBankCd(bankCd6);
        b.setCitadBankCd(CITAD_SHINHAN);
        b.setAntDivCd("CITAD");
        return b;
    }

    /** One-line payroll draft request. Remarks ride in {@code tranUsag} on
     *  the shared wire DTO (the save maps it to WDRL_ACCT_RMRK). */
    private static CreateScheduledTransferRequest req(String ccy, String tarBankCd, String remark) {
        TransferLineInput line = new TransferLineInput(
                new BigDecimal("100"),  // tranAmt
                "NAPAS",                // antDivCd (parsed; save forces CITAD)
                "NNT dang hoat dong",   // rcptNm
                tarBankCd,              // tarBankCd (typed)
                "700071152854",         // fincMdaNo
                remark,                 // tranUsag (= Remarks on the wire)
                null, null, null, null, // trcoKey, handTrcoKey, docNo, note
                null, null, null,       // citadBankCd, trcoCmnm, trcoCmenm
                null, null,             // chartAcctSeqNo, chartAcctCd
                ccy,                    // trnfCcyCd
                null);                  // depoCcyCd
        return new CreateScheduledTransferRequest(
                "Payroll Dec", WDRL_BANK_CD, WDRL_ACCT_NO, ccy, ccy, null, List.of(line));
    }
}
