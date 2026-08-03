package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.OutBankCdNormalizer;
import com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard;
import com.kosign.wecms.domain.payment.common.PaymentWireWidthGuard;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelDetailRow;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelUploadRequest;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.service.CutoffValidator;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnMstrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.service.OverseasDispatchExecutor;
import com.kosign.wecms.domain.payment.overseas.service.OverseasPaymentKeyGenerator;
import com.kosign.wecms.domain.payment.overseas.service.OverseasSenderNameGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIX B — the {@code SENDER_NAME} reject is enforced at <b>both</b> Overseas surfaces.
 *
 * <p>{@link OverseasSenderNameGuardTest} pins what the guard decides. This class pins that the guard is
 * actually consulted, at both points, and that a rejection <b>stops the operation</b> rather than being
 * logged and walked past:
 * <ol>
 *   <li><b>Upload</b> — {@code OverseasPaymentExcelService.doSubmit}: the earliest point at which the
 *       withdrawal account is known AND the user can still pick a different one. A reject here must
 *       happen <i>before</i> any {@code OVRS_PYMN_*} row is written.</li>
 *   <li><b>Dispatch</b> — {@code OverseasPaymentService.dispatch}: {@code ACCT_NM} is refreshed from the
 *       bank adapter on every account re-registration, so it can change between upload and dispatch —
 *       and batches uploaded before the guard existed were never checked at all. A reject here must
 *       happen before {@code OverseasDispatchExecutor.execute} writes any dispatch row, so that an
 *       opaque mid-dispatch codec failure becomes a named business error with nothing sent.</li>
 * </ol>
 *
 * <p>The guard itself is mocked here <b>on purpose</b>: this class asserts wiring and fail-closed
 * ordering, not width arithmetic. Its companion asserts the arithmetic against the real
 * {@link PaymentWireWidthGuard}. Together they cover "the right decision" and "the decision is reached
 * and obeyed" — either alone would let the feature ship broken.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasSenderNameEnforcementTest {

    private static final String BIZ_KEY  = "VN_VNM0004_000001";
    private static final String USER_ID  = "tester";
    private static final String PYMN_NO  = "OVRS20260730001";
    /** Raw internal withdrawal {@code BANK_CD} as the upload request carries it. */
    private static final String WDRL_BANK_CD = "11704088";
    /** Its NAPAS {@code OUT_BANK_CD} form — what {@code OVRS_PYMN_SUMR.OUT_BANK_CD} stores. */
    private static final String OUT_BANK_CD  = "970424";
    private static final String ACCT_NO  = "700123456789";

    /** The exception the guard raises; both surfaces must let it through untouched. */
    private static BusinessException unsendable() {
        return new BusinessException("WBE_PAYMENT_SENDER_NAME_UNSENDABLE", ACCT_NO,
                "Sender Name contains a character the bank cannot carry.");
    }

    @BeforeEach
    void bindTenant() {
        TenantContext.set(USER_ID, "Tester", BIZ_KEY, LangCd.EN);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ─────────────────────────── 1. upload ───────────────────────────────────

    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    static class AtUpload {

        @Mock OverseasPaymentKeyGenerator keyGenerator;
        @Mock OutBankCdNormalizer         outBankCdNormalizer;
        @Mock PaymentBlockedCharGuard     blockedCharGuard;
        @Mock PaymentWireWidthGuard       wireWidthGuard;
        @Mock OverseasSenderNameGuard     senderNameGuard;
        @Mock OvrsPymnSumrRepository      sumrRepository;
        @Mock OvrsPymnDtlsRepository      dtlsRepository;

        @InjectMocks OverseasPaymentExcelService service;

        @BeforeEach
        void bindTenant() {
            TenantContext.set(USER_ID, "Tester", BIZ_KEY, LangCd.EN);
            when(keyGenerator.nextOvrsPymnNo(anyString())).thenReturn(PYMN_NO);
            when(outBankCdNormalizer.normalize(WDRL_BANK_CD)).thenReturn(OUT_BANK_CD);
        }

        @AfterEach
        void clearTenant() {
            TenantContext.clear();
        }

        private static OverseasPaymentExcelUploadRequest request() {
            OverseasPaymentExcelDetailRow row = new OverseasPaymentExcelDetailRow(
                    1, "BNF001", "USD", new BigDecimal("1000.00"), "B", "invoice 2026-07",
                    null, "ACME TRADING CO LTD", "SOME BANK", "SOMEBICX", "123456789",
                    "1 SOME STREET", "SOMETOWN");
            return new OverseasPaymentExcelUploadRequest(
                    "overseas.xlsx", "USD", new BigDecimal("1000.00"), 1,
                    WDRL_BANK_CD, "Shinhan VN", ACCT_NO, List.of(row));
        }

        @Test
        @DisplayName("upload rejects with WBE_PAYMENT_SENDER_NAME_UNSENDABLE and persists nothing")
        void uploadRejects_andPersistsNothing() {
            doThrow(unsendable()).when(senderNameGuard)
                    .assertSendable(BIZ_KEY, OUT_BANK_CD, ACCT_NO);

            assertThatThrownBy(() -> service.doSubmit(request(), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo("WBE_PAYMENT_SENDER_NAME_UNSENDABLE");

            // Fail-closed ordering: the guard runs before the summary / detail writes, so a rejected
            // upload leaves no half-created batch for the user to trip over.
            verify(sumrRepository, never()).save(any());
            verify(dtlsRepository, never()).save(any());
        }

        @Test
        @DisplayName("upload passes the NORMALISED OUT_BANK_CD, so the guard reads the executor's row")
        void uploadNormalisesTheBankCodeBeforeGuarding() {
            // The request carries the raw internal BANK_CD; the guard's contract is the NAPAS form. If
            // this normalisation were dropped the guard would still "work" for the internal form by its
            // own tolerance branch — so assert the intended call explicitly rather than relying on that.
            doThrow(unsendable()).when(senderNameGuard).assertSendable(anyString(), anyString(), anyString());

            assertThatThrownBy(() -> service.doSubmit(request(), null))
                    .isInstanceOf(BusinessException.class);

            verify(senderNameGuard).assertSendable(BIZ_KEY, OUT_BANK_CD, ACCT_NO);
        }

        @Test
        @DisplayName("upload consults the guard even when every other entry guard passes")
        void uploadAlwaysConsultsTheGuard() {
            // blockedCharGuard / wireWidthGuard are no-op mocks here: SENDER_NAME has no upload cell, so
            // no amount of cell validation can stand in for this check. This is the whole reason FIX B
            // exists as a separate guard.
            doThrow(unsendable()).when(senderNameGuard).assertSendable(anyString(), anyString(), anyString());
            assertThatThrownBy(() -> service.doSubmit(request(), null))
                    .isInstanceOf(BusinessException.class);
            verify(senderNameGuard).assertSendable(BIZ_KEY, OUT_BANK_CD, ACCT_NO);
        }
    }

    // ────────────────────────── 2. dispatch ──────────────────────────────────

    @Mock OvrsPymnSumrRepository     sumrRepository;
    @Mock OvrsPymnMstrRepository     ovrsPymnMstrRepository;
    @Mock OvrsPymnDtlsRepository     dtlsRepository;
    @Mock ExptResolMstrRepository    exptResolMstrRepository;
    @Mock CutoffValidator            cutoffValidator;
    @Mock OverseasDispatchExecutor   dispatchExecutor;
    @Mock OverseasSenderNameGuard    senderNameGuard;
    /** Needed only by the negative control, which runs dispatch to completion. */
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock com.kosign.wecms.common.auditlog.service.AuditLogService auditLogService;

    @InjectMocks OverseasPaymentService service;

    /** An approved, not-yet-dispatched S-mode batch — every precondition ahead of the guard satisfied. */
    private void approvedUndispatchedBatch() {
        OvrsPymnSumr sumr = new OvrsPymnSumr();
        sumr.setBizKey(BIZ_KEY);
        sumr.setOvrsPymnNo(PYMN_NO);
        sumr.setTrnfYn("N");
        sumr.setApprSeqNo("APPR001");
        sumr.setOutBankCd(OUT_BANK_CD);
        sumr.setWdrlActNo(ACCT_NO);

        ExptResolMstr appr = new ExptResolMstr();
        appr.setApprTypeCd("01");            // → derived APPR_STTS 'C' (approved)

        when(ovrsPymnMstrRepository.findByBizKeyAndOvrsPymnNo(BIZ_KEY, PYMN_NO)).thenReturn(Optional.empty());
        when(sumrRepository.findByBizKeyAndOvrsPymnNo(BIZ_KEY, PYMN_NO)).thenReturn(Optional.of(sumr));
        when(exptResolMstrRepository.findById(any())).thenReturn(Optional.of(appr));
        when(dtlsRepository.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(BIZ_KEY, PYMN_NO))
                .thenReturn(List.of(new com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl()));
    }

    @Test
    @DisplayName("dispatch rejects with WBE_PAYMENT_SENDER_NAME_UNSENDABLE and sends nothing")
    void dispatchRejects_andSendsNothing() {
        approvedUndispatchedBatch();
        doThrow(unsendable()).when(senderNameGuard).assertSendable(BIZ_KEY, OUT_BANK_CD, ACCT_NO);

        assertThatThrownBy(() -> service.dispatch(PYMN_NO))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("WBE_PAYMENT_SENDER_NAME_UNSENDABLE");

        // Nothing has left the building: no TB_PAY_FILE_MST / TB_PAY_FILE_DATA rows, no TRNF_YN flip.
        // This is the point of re-checking at dispatch — the alternative is an IllegalArgumentException
        // out of FixedLengthCodec after the pay-file rows are already written.
        verify(dispatchExecutor, never()).execute(any(), any());
        verify(sumrRepository, never()).save(any());
    }

    @Test
    @DisplayName("dispatch guards AFTER the cutoff gate and BEFORE the executor")
    void dispatchGuardOrdering() {
        approvedUndispatchedBatch();
        doThrow(unsendable()).when(senderNameGuard).assertSendable(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.dispatch(PYMN_NO)).isInstanceOf(BusinessException.class);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(cutoffValidator, senderNameGuard, dispatchExecutor);
        order.verify(cutoffValidator).validate(any(), any());
        order.verify(senderNameGuard).assertSendable(BIZ_KEY, OUT_BANK_CD, ACCT_NO);
        order.verify(dispatchExecutor, never()).execute(any(), any());
    }

    @Test
    @DisplayName("dispatch passes OVRS_PYMN_SUMR.OUT_BANK_CD verbatim — it is already the NAPAS form")
    void dispatchPassesTheStoredOutBankCd() {
        approvedUndispatchedBatch();
        doThrow(unsendable()).when(senderNameGuard).assertSendable(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.dispatch(PYMN_NO)).isInstanceOf(BusinessException.class);

        // No second normalisation: OUT_BANK_CD was normalised once at upload. Re-normalising a NAPAS
        // code is a no-op today, but asserting the verbatim pass keeps the two surfaces' contracts
        // distinct and documented.
        verify(senderNameGuard).assertSendable(BIZ_KEY, OUT_BANK_CD, ACCT_NO);
    }

    @Test
    @DisplayName("a sendable name lets dispatch proceed to the executor")
    void sendableName_reachesTheExecutor() {
        // The negative control. Without it, a guard that threw unconditionally — or a dispatch path that
        // aborted for an unrelated reason — would satisfy every assertion above.
        approvedUndispatchedBatch();
        // senderNameGuard is a no-op mock: the name is sendable.

        service.dispatch(PYMN_NO);

        verify(senderNameGuard).assertSendable(BIZ_KEY, OUT_BANK_CD, ACCT_NO);
        verify(dispatchExecutor).execute(any(), any());
        verify(sumrRepository).save(any());
    }
}
