package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Draft-save persistence parity for {@link InternalTransferService#createDraft}.
 *
 * <p>Asserts the master/detail column values legacy
 * {@code PymnHelper.uploadPaymentExcel} writes for {@code pymn_0009_002_c001}:
 * <ul>
 *   <li><b>G3</b> — {@code SCDUL_PYMN_SUMR.CCY_CD} = transfer currency
 *       ({@code trnf_ccy_cd}); {@code WDRL_CCY_CD} = withdrawal-account
 *       currency ({@code ccy_cd}). The two columns hold different values
 *       (legacy {@code :1317} / {@code :1320}).</li>
 *   <li><b>G1/G2</b> — the detail row persists the resolved {@code TAR_BANK_CD}
 *       and the {@code CITAD_BANK_CD} the parser supplied verbatim.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalTransferCreateDraftTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";

    @Mock private ScdulPymnSumrRepository sumrRepository;
    @Mock private ScdulPymnSumrDetlRepository detlRepository;
    @Mock private InternalTransferKeyGenerator keyGenerator;
    @Mock private TransactionalRetry transactionalRetry;
    @Mock private LegacyFunctionRepository legacyFunctions;
    @Mock private AuditLogService auditLogService;
    @Mock private OutBankCdNormalizer outBankCdNormalizer;
    @Mock private com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard blockedCharGuard;
    @Mock private Clock clock;
    @Mock private com.kosign.wecms.domain.payment.common.UploadConsistencyValidator uploadConsistencyValidator;
    @Mock private InternalTransferFileResolver fileResolver;

    /** Storage root for the mandatory uploaded workbook (legacy requires the file
     *  at save — {@code PaymentUploadFileGuard}). */
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path storageRoot;

    @InjectMocks private InternalTransferService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER_ID, "Tester", BIZ_KEY, LangCd.EN);
        when(fileResolver.resolve(org.mockito.ArgumentMatchers.anyString(),
                                  org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> storageRoot.resolve((String) inv.getArgument(1)));
        // Run the supplied action inline (no real REQUIRES_NEW boundary in a unit test).
        when(transactionalRetry.runOnce(any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(clock.instant()).thenReturn(Instant.parse("2026-06-29T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(keyGenerator.nextScdulPymnNo(BIZ_KEY)).thenReturn("20260629000001");
        when(legacyFunctions.nextScdulPymnDetlNo(BIZ_KEY)).thenReturn("1");
        // Internal BANK_CD 11704020 → NAPAS 970457 (Woori), legacy parity.
        when(outBankCdNormalizer.normalize("11704020")).thenReturn("970457");
        when(sumrRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createDraft_persistsMasterAndDetailColumns() {
        // ccy_cd (account currency) deliberately differs from trnf_ccy_cd
        // (transfer currency) so the G3 column mapping is unambiguous.
        TransferLineInput line = new TransferLineInput(
                new BigDecimal("100000"),   // tranAmt
                "CITAD",                     // antDivCd
                "STUB ACCOUNT",              // rcptNm
                "01202001",                  // tarBankCd (resolved 8-digit BANK_CD)
                "700071152854",              // fincMdaNo
                "Internal payment",          // tranUsag
                null,                        // trcoKey
                null,                        // handTrcoKey
                "DOC-1",                     // docNo
                "note",                      // note
                "202",                       // citadBankCd (3-digit)
                null,                        // trcoCmnm
                null,                        // trcoCmenm
                null,                        // chartAcctSeqNo
                null,                        // chartAcctCd
                "VND",                       // trnfCcyCd
                "VND");                      // depoCcyCd

        CreateScheduledTransferRequest req = new CreateScheduledTransferRequest(
                "Internal_Transfer_260629",  // titl
                "11704020",                  // bank_cd (internal code → NAPAS)
                "001234567",                 // acct_no
                "USD",                       // ccy_cd  → WDRL_CCY_CD
                "VND",                       // trnf_ccy_cd → CCY_CD
                List.of(line));

        CreateScheduledTransferResponse resp = service.createDraft(req, xlsx());
        assertThat(resp.scdulPymnNo()).isEqualTo("20260629000001");

        // Master row.
        ArgumentCaptor<ScdulPymnSumr> masterCaptor = ArgumentCaptor.forClass(ScdulPymnSumr.class);
        verify(sumrRepository).saveAndFlush(masterCaptor.capture());
        ScdulPymnSumr master = masterCaptor.getValue();

        assertThat(master.getScdulPymnTypeCd()).isEqualTo("03");
        assertThat(master.getOutBankCd()).isEqualTo("970457");
        assertThat(master.getWdrlActNo()).isEqualTo("001234567");
        // G3: CCY_CD = transfer currency; WDRL_CCY_CD = withdrawal-account currency.
        assertThat(master.getCcyCd()).isEqualTo("VND");
        assertThat(master.getWdrlCcyCd()).isEqualTo("USD");
        assertThat(master.getTitl()).isEqualTo("Internal_Transfer_260629");
        assertThat(master.getTotTrnsCnt()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(master.getTotalTranAmt()).isEqualByComparingTo("100000");
        assertThat(master.getTrnfYn()).isEqualTo("N");

        // Detail rows.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScdulPymnSumrDetl>> detlCaptor = ArgumentCaptor.forClass(List.class);
        verify(detlRepository).saveAll(detlCaptor.capture());
        List<ScdulPymnSumrDetl> detls = detlCaptor.getValue();
        assertThat(detls).hasSize(1);
        ScdulPymnSumrDetl detl = detls.get(0);

        assertThat(detl.getScdulPymnNo()).isEqualTo("20260629000001");
        assertThat(detl.getScdulPymnDetlNo()).isEqualTo("1");
        // G1/G2: resolved bank code + 3-digit CITAD code persisted verbatim.
        assertThat(detl.getTarBankCd()).isEqualTo("01202001");
        assertThat(detl.getCitadBankCd()).isEqualTo("202");
        assertThat(detl.getAntDivCd()).isEqualTo("CITAD");
        assertThat(detl.getTranAmt()).isEqualByComparingTo("100000");
        assertThat(detl.getTrnfCcyCd()).isEqualTo("VND");
        assertThat(detl.getFincMdaNo()).isEqualTo("700071152854");

        verify(outBankCdNormalizer).normalize("11704020");
    }

    /** The uploaded workbook is mandatory at save (legacy
     *  {@code pymn_0009_002_c001} {@code validateRequired(FILE_DATA)}). */
    private static org.springframework.mock.web.MockMultipartFile xlsx() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file_data", "Internal_Transfer_260629.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] { 1, 2, 3 });
    }
}
