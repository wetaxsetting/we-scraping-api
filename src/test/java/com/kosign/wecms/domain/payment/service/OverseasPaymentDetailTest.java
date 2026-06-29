package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentDetailResponse;
import com.kosign.wecms.domain.payment.entity.OvrsPymnMstr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;
import com.kosign.wecms.domain.payment.internaltransfer.service.ApprovalAttachmentLoader;
import com.kosign.wecms.domain.payment.internaltransfer.service.ApprovalContentLoader;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnMstrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import com.kosign.wecms.domain.payment.repository.OverseasPaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * GAP-OVS-ATT + GAP-OVS-MODE — {@code OverseasPaymentService.getDetail}.
 *
 * <ul>
 *   <li>S-mode (OVRS_PYMN_SUMR) detail now carries approval attachments and
 *       otherwise renders unchanged.</li>
 *   <li>A legacy M-mode approval (OVRS_PYMN_MSTR only) no longer throws
 *       {@code WBE2045}; it degrades to a graceful header-only response.</li>
 *   <li>A truly-missing id still throws {@code WBE2045}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasPaymentDetailTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";

    @Mock private OvrsPymnSumrRepository       sumrRepository;
    @Mock private OvrsPymnDtlsRepository       dtlsRepository;
    @Mock private OvrsPymnMstrRepository       ovrsPymnMstrRepository;
    @Mock private ExptResolMstrRepository      exptResolMstrRepository;
    @Mock private DispatchResultResolver       dispatchResultResolver;
    @Mock private ApprovalAttachmentLoader     approvalAttachmentLoader;
    @Mock private ApprovalContentLoader        approvalContentLoader;
    @Mock private OverseasPaymentRepository    paymentRepo;

    @InjectMocks private OverseasPaymentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER_ID, "Tester", BIZ_KEY, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ----- S-mode -----------------------------------------------------------

    @Test
    void getDetail_sMode_returnsAttachments_andIsNotLegacyMmode() {
        OvrsPymnSumr sumr = new OvrsPymnSumr();
        sumr.setBizKey(BIZ_KEY);
        sumr.setOvrsPymnNo("OV-S-1");
        sumr.setApprSeqNo("200");
        sumr.setCcyCd("USD");
        sumr.setTotalTranAmt(new BigDecimal("2500"));

        when(sumrRepository.findByBizKeyAndOvrsPymnNo(BIZ_KEY, "OV-S-1"))
                .thenReturn(Optional.of(sumr));
        when(dtlsRepository.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(BIZ_KEY, "OV-S-1"))
                .thenReturn(List.of());
        when(dispatchResultResolver.resolveOverseas(any(), any()))
                .thenReturn(Map.<String, DispatchResult>of());
        when(approvalAttachmentLoader.load(BIZ_KEY, "200"))
                .thenReturn(List.of(new ApprovalAttachmentRow(
                        "1", "evidence.jpg", "2048", "Payer", "20260102100000",
                        ApprovalAttachmentRow.TYPE_PAY_EVID_FILE)));

        OverseasPaymentDetailResponse resp = service.getDetail("OV-S-1");

        assertThat(resp.legacyMmode()).isFalse();
        assertThat(resp.ovrsPymnNo()).isEqualTo("OV-S-1");
        assertThat(resp.attachments()).singleElement()
                .satisfies(a -> {
                    assertThat(a.orgFileNm()).isEqualTo("evidence.jpg");
                    assertThat(a.fileType()).isEqualTo(ApprovalAttachmentRow.TYPE_PAY_EVID_FILE);
                });
    }

    // ----- M-mode (GAP-OVS-MODE) -------------------------------------------

    @Test
    void getDetail_legacyMmode_rendersRealRemittanceRow_notHeaderOnly() {
        OvrsPymnMstr mstrRow = new OvrsPymnMstr();
        mstrRow.setBizKey(BIZ_KEY);
        mstrRow.setOvrsPymnNo("OV-M-1");
        mstrRow.setApprSeqNo("300");
        mstrRow.setTrnfCcyCd("USD");
        mstrRow.setTrnfAmt(new BigDecimal("1500"));
        mstrRow.setRegId("drafter");
        mstrRow.setRegDtm("20250904035208");

        // Not in OVRS_PYMN_SUMR, and not resolvable as a DOC_NO.
        when(sumrRepository.findByBizKeyAndOvrsPymnNo(BIZ_KEY, "OV-M-1"))
                .thenReturn(Optional.empty());
        when(exptResolMstrRepository.findByBizKeyAndDocNoAndDelYn(BIZ_KEY, "OV-M-1", "N"))
                .thenReturn(Optional.empty());
        // Lives only in OVRS_PYMN_MSTR (legacy M-mode).
        when(ovrsPymnMstrRepository.findByBizKeyAndOvrsPymnNo(BIZ_KEY, "OV-M-1"))
                .thenReturn(Optional.of(mstrRow));
        when(exptResolMstrRepository.findById(any())).thenReturn(Optional.empty());
        when(approvalAttachmentLoader.load(BIZ_KEY, "300")).thenReturn(List.of());
        // F3 — the single M remittance line (R007 M branch projection).
        when(paymentRepo.findMmodeDetailRows(BIZ_KEY, "OV-M-1"))
                .thenReturn(java.util.Collections.singletonList(mmodeDetailRow("USD", "1500", "John Doe", "0", "0000")));

        OverseasPaymentDetailResponse resp = service.getDetail("OV-M-1");

        // F3 — M-mode now fully rendered (no header-only fallback).
        assertThat(resp.legacyMmode()).isFalse();
        assertThat(resp.legacyMmodeNotice()).isNull();
        assertThat(resp.ovrsPymnNo()).isEqualTo("OV-M-1");
        assertThat(resp.apprSeqNo()).isEqualTo("300");
        // Detail line table carries the one M remittance row from OVRS_PYMN_MSTR.
        assertThat(resp.rows()).singleElement().satisfies(r -> {
            assertThat(r.ovrsPymnDetlNo()).isNull();       // M-mode (R010 parity)
            assertThat(r.bnfcNm()).isEqualTo("John Doe");
            assertThat(r.procStat()).isEqualTo("0");
            assertThat(r.rsCd()).isEqualTo("0000");
        });
        assertThat(resp.totalTranAmt()).isEqualByComparingTo("1500");
    }

    @Test
    void getDetail_legacyMmode_resolvedViaDocNoFallback() {
        ExptResolMstr mstr = new ExptResolMstr();
        mstr.setBizKey(BIZ_KEY);
        mstr.setApprSeqNo("301");
        mstr.setDocNo("VO_250904_005");
        mstr.setApprTypeCd("01");

        OvrsPymnMstr mstrRow = new OvrsPymnMstr();
        mstrRow.setBizKey(BIZ_KEY);
        mstrRow.setOvrsPymnNo("OV-M-2");
        mstrRow.setApprSeqNo("301");
        mstrRow.setTrnfCcyCd("EUR");
        mstrRow.setTrnfAmt(new BigDecimal("900"));

        when(sumrRepository.findByBizKeyAndOvrsPymnNo(BIZ_KEY, "VO_250904_005"))
                .thenReturn(Optional.empty());
        when(exptResolMstrRepository.findByBizKeyAndDocNoAndDelYn(BIZ_KEY, "VO_250904_005", "N"))
                .thenReturn(Optional.of(mstr));
        // DOC_NO resolved an APPR_SEQ_NO but there's no SUMR row for it...
        when(sumrRepository.findByBizKeyAndApprSeqNo(BIZ_KEY, "301"))
                .thenReturn(Optional.empty());
        // ...the rows live in OVRS_PYMN_MSTR.
        when(ovrsPymnMstrRepository.findByBizKeyAndApprSeqNo(BIZ_KEY, "301"))
                .thenReturn(List.of(mstrRow));
        when(exptResolMstrRepository.findById(any())).thenReturn(Optional.of(mstr));
        when(approvalAttachmentLoader.load(BIZ_KEY, "301")).thenReturn(List.of());
        when(paymentRepo.findMmodeDetailRows(BIZ_KEY, "OV-M-2"))
                .thenReturn(java.util.Collections.singletonList(mmodeDetailRow("EUR", "900", "Acme GmbH", "0", "0000")));

        OverseasPaymentDetailResponse resp = service.getDetail("VO_250904_005");

        // F3 — resolved via DOC_NO fallback; M-mode now fully rendered.
        assertThat(resp.legacyMmode()).isFalse();
        assertThat(resp.apprSeqNo()).isEqualTo("301");
        assertThat(resp.docNo()).isEqualTo("VO_250904_005");
        assertThat(resp.rows()).hasSize(1);
    }

    /** Builds a {@code findMmodeDetailRows} projection row (17 cols, R007 M branch). */
    private static Object[] mmodeDetailRow(String ccy, String amt, String bnfcNm,
                                           String procStat, String rsCd) {
        Object[] m = new Object[17];
        m[0]  = "OV-M";                 // ovrs_pymn_no
        m[1]  = null;                   // hand_trco_key
        m[2]  = ccy;                    // trnf_ccy_cd
        m[3]  = new BigDecimal(amt);    // trnf_amt
        m[4]  = "B";                    // fee_payer_cd
        m[5]  = "memo";                 // memo
        m[6]  = null;                   // chart_acct_cd
        m[7]  = bnfcNm;                 // bnfc_nm
        m[8]  = "BANK OF X";            // bnfc_bank_name
        m[9]  = "BOFXUS";               // bnfc_bank_bic
        m[10] = "123456789";            // bnfc_acct_no
        m[11] = "1 Main St";            // bnfc_addr
        m[12] = "Y";                    // trnf_yn
        m[13] = procStat;               // proc_stat
        m[14] = "20250813102604";       // proc_dttm
        m[15] = rsCd;                   // rs_cd
        m[16] = null;                   // rs_msg
        return m;
    }

    // ----- genuinely not found ---------------------------------------------

    @Test
    void getDetail_notFoundAnywhere_stillThrowsWBE2045() {
        when(sumrRepository.findByBizKeyAndOvrsPymnNo(eq(BIZ_KEY), anyString()))
                .thenReturn(Optional.empty());
        when(exptResolMstrRepository.findByBizKeyAndDocNoAndDelYn(eq(BIZ_KEY), anyString(), eq("N")))
                .thenReturn(Optional.empty());
        when(ovrsPymnMstrRepository.findByBizKeyAndOvrsPymnNo(eq(BIZ_KEY), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail("DOES-NOT-EXIST"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("WBE2045"));
    }
}
