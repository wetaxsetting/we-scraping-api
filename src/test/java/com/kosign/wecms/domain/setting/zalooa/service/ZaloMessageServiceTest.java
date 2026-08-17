package com.kosign.wecms.domain.setting.zalooa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloApiClient;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloApiException;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloSendCommand;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloSendOutcome;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloSendRequest;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloSendResponse;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloOaInfm;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloSendHis;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloTmplInfm;
import com.kosign.wecms.domain.setting.zalooa.repository.ZaloOaInfmRepository;
import com.kosign.wecms.domain.setting.zalooa.repository.ZaloTmplInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the one behaviour a Zalo send path must never get wrong: <b>an attempt that
 * fails at or after the vendor call is still recorded</b>.
 *
 * <p>Why it matters more than a usual log assertion: a message that reached Zalo was
 * billed to the tenant's wallet and may have been delivered. If the failure path
 * skipped the {@code ZALO_SEND_HIS} write, support would be asked "did my customer
 * get this, and why was I charged?" with nothing to look at. The reference
 * implementation has a related defect — it assigns the recipient number <em>after</em>
 * the call, so its failure rows have no recipient
 * ({@code webill365-vn-api/docs/features/zalo-oa-integration.md} §10.5).
 *
 * <p>The mirror-image rule is also asserted: a failure <em>before</em> the call
 * (no active OA, no template) throws and writes nothing, because nothing was
 * attempted and there is nothing to reconcile.
 */
@ExtendWith(MockitoExtension.class)
class ZaloMessageServiceTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String OA_ID = "1234567890";
    private static final String TMPL_ID = "358731";
    private static final String PHONE = "84912345678";

    @Mock
    private ZaloOaInfmRepository oaRepo;

    @Mock
    private ZaloTmplInfmRepository tmplRepo;

    @Mock
    private ZaloApiClient zaloApiClient;

    @Mock
    private ZaloTokenService tokenService;

    @Mock
    private ZaloSendHistoryService historyService;

    private ZaloMessageService service;

    @BeforeEach
    void setUp() {
        service = new ZaloMessageService(oaRepo, tmplRepo, zaloApiClient, tokenService,
                historyService, new ObjectMapper());
        TenantContext.set("tester", "Tester", BIZ_KEY, LangCd.VI);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ZaloSendRequest request() {
        return new ZaloSendRequest(PHONE, "NOTICE", "VI", Map.of("customer_name", "Nguyen Van A"));
    }

    private void givenActiveOaWithTemplate() {
        ZaloOaInfm oa = new ZaloOaInfm();
        oa.setBizKey(BIZ_KEY);
        oa.setOaId(OA_ID);
        oa.setRefreshToken("refresh-1");
        oa.setUseYn("Y");
        oa.setDelYn("N");
        when(oaRepo.findByBizKeyAndUseYnAndDelYn(BIZ_KEY, "Y", "N")).thenReturn(Optional.of(oa));

        ZaloTmplInfm tmpl = new ZaloTmplInfm();
        tmpl.setBizKey(BIZ_KEY);
        tmpl.setOaId(OA_ID);
        tmpl.setTmplType("NOTICE");
        tmpl.setLangCd("VI");
        tmpl.setTmplId(TMPL_ID);
        tmpl.setUnitPrc(new BigDecimal("210"));
        tmpl.setUseYn("Y");
        when(tmplRepo.findByBizKeyAndOaIdAndTmplTypeAndLangCdAndUseYn(
                BIZ_KEY, OA_ID, "NOTICE", "VI", "Y")).thenReturn(Optional.of(tmpl));

        when(tokenService.accessTokenFor(BIZ_KEY, OA_ID)).thenReturn("live-token");
    }

    private void stubHistoryReturningSeq(long seq) {
        ZaloSendHis saved = new ZaloSendHis();
        saved.setSendSeq(BigDecimal.valueOf(seq));
        when(historyService.record(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);
    }

    @Test
    void successfulSendIsLoggedAndReported() {
        givenActiveOaWithTemplate();
        stubHistoryReturningSeq(1L);
        when(zaloApiClient.sendTemplateMessage(anyString(), any(ZaloSendCommand.class)))
                .thenReturn(new ZaloSendOutcome(0, "Success", "{\"error\":0}"));

        ZaloSendResponse response = service.send(request());

        assertThat(response.success()).isTrue();
        assertThat(response.rsltCd()).isEqualTo("0");
        assertThat(response.trackingId()).isNotBlank();
        verify(historyService).record(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void zaloRejectionIsLoggedAndReturnedAsAnUnsuccessfulOutcomeRatherThanThrown() {
        givenActiveOaWithTemplate();
        stubHistoryReturningSeq(2L);
        // -108: the recipient has not interacted with the OA. A business fact, and a
        // billed attempt — it must be visible in the history, not raised as a fault.
        when(zaloApiClient.sendTemplateMessage(anyString(), any(ZaloSendCommand.class)))
                .thenReturn(new ZaloSendOutcome(-108, "User has not followed OA", "{\"error\":-108}"));

        ZaloSendResponse response = service.send(request());

        assertThat(response.success()).isFalse();
        assertThat(response.rsltCd()).isEqualTo("-108");
        assertThat(response.sendSeq()).isEqualTo(BigDecimal.valueOf(2L));

        ArgumentCaptor<ZaloSendOutcome> outcome = ArgumentCaptor.forClass(ZaloSendOutcome.class);
        verify(historyService).record(anyString(), any(), any(), any(), any(), any(), any(), any(), any(),
                outcome.capture());
        assertThat(outcome.getValue().errorCode()).isEqualTo(-108);
    }

    @Test
    void transportFaultStillWritesAHistoryRowWithTheRecipient() {
        givenActiveOaWithTemplate();
        stubHistoryReturningSeq(3L);
        when(zaloApiClient.sendTemplateMessage(anyString(), any(ZaloSendCommand.class)))
                .thenThrow(new ZaloApiException("read timed out"));

        ZaloSendResponse response = service.send(request());

        assertThat(response.success()).isFalse();
        assertThat(response.sendSeq()).isEqualTo(BigDecimal.valueOf(3L));

        // Zalo may or may not have received this one, so the row is the only record
        // that the tenant was potentially charged — and it must carry who it was for.
        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        verify(historyService).record(anyString(), any(), any(), any(), any(),
                recipient.capture(), any(), any(), any(), any());
        assertThat(recipient.getValue()).isEqualTo(PHONE);
    }

    @Test
    void noActiveOaThrowsAndWritesNothing() {
        when(oaRepo.findByBizKeyAndUseYnAndDelYn(BIZ_KEY, "Y", "N")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("WBE_ZALO_NO_ACTIVE_OA");

        // Nothing was attempted — a history row here would be noise implying a send.
        verify(historyService, never()).record(anyString(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(zaloApiClient, never()).sendTemplateMessage(anyString(), any());
    }

    @Test
    void unregisteredTemplateThrowsBeforeAnyTokenIsFetched() {
        ZaloOaInfm oa = new ZaloOaInfm();
        oa.setBizKey(BIZ_KEY);
        oa.setOaId(OA_ID);
        oa.setRefreshToken("refresh-1");
        when(oaRepo.findByBizKeyAndUseYnAndDelYn(BIZ_KEY, "Y", "N")).thenReturn(Optional.of(oa));
        when(tmplRepo.findByBizKeyAndOaIdAndTmplTypeAndLangCdAndUseYn(
                BIZ_KEY, OA_ID, "NOTICE", "VI", "Y")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("WBE_ZALO_TEMPLATE_NOT_FOUND");

        // Fetching a token first would risk spending a refresh-token rotation on a
        // send that was never going to happen.
        verify(tokenService, never()).accessTokenFor(anyString(), anyString());
    }

    @Test
    void disabledZaloIsReportedAsItsOwnBusinessCodeNotAsAServerFault() {
        givenActiveOaWithTemplate();
        // MockZaloApiClient throws this when wecms.zalo.enabled=false.
        when(zaloApiClient.sendTemplateMessage(anyString(), any(ZaloSendCommand.class)))
                .thenThrow(new UnsupportedOperationException("Live Zalo is disabled"));

        assertThatThrownBy(() -> service.send(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("WBE_ZALO_DISABLED");

        // Nothing left the process, so there is nothing to reconcile and no row.
        verify(historyService, never()).record(anyString(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void templateResolutionUsesTheRequestedLanguageExactly() {
        givenActiveOaWithTemplate();
        stubHistoryReturningSeq(4L);
        when(zaloApiClient.sendTemplateMessage(anyString(), any(ZaloSendCommand.class)))
                .thenReturn(new ZaloSendOutcome(0, "Success", "{\"error\":0}"));

        service.send(request());

        // LANG_CD is part of the template primary key precisely so this is an exact
        // lookup. The reference implementation filters on type then takes findFirst(),
        // which can send the wrong locale when a tenant registers both (§10.6).
        verify(tmplRepo).findByBizKeyAndOaIdAndTmplTypeAndLangCdAndUseYn(
                BIZ_KEY, OA_ID, "NOTICE", "VI", "Y");

        ArgumentCaptor<ZaloSendCommand> sent = ArgumentCaptor.forClass(ZaloSendCommand.class);
        verify(zaloApiClient).sendTemplateMessage(anyString(), sent.capture());
        assertThat(sent.getValue().templateId()).isEqualTo(TMPL_ID);
        assertThat(sent.getValue().phone()).isEqualTo(PHONE);
        assertThat(sent.getValue().trackingId()).isNotBlank();
    }

    @Test
    void tokenExpiryIsNotSwallowed() {
        ZaloOaInfm oa = new ZaloOaInfm();
        oa.setBizKey(BIZ_KEY);
        oa.setOaId(OA_ID);
        oa.setRefreshToken("refresh-1");
        oa.setTokenExpiDtm(OffsetDateTime.now().minusDays(1));
        when(oaRepo.findByBizKeyAndUseYnAndDelYn(BIZ_KEY, "Y", "N")).thenReturn(Optional.of(oa));

        ZaloTmplInfm tmpl = new ZaloTmplInfm();
        tmpl.setTmplId(TMPL_ID);
        tmpl.setTmplType("NOTICE");
        tmpl.setLangCd("VI");
        when(tmplRepo.findByBizKeyAndOaIdAndTmplTypeAndLangCdAndUseYn(
                BIZ_KEY, OA_ID, "NOTICE", "VI", "Y")).thenReturn(Optional.of(tmpl));
        when(tokenService.accessTokenFor(BIZ_KEY, OA_ID))
                .thenThrow(new BusinessException("WBE_ZALO_TOKEN_REFRESH_FAILED"));

        assertThatThrownBy(() -> service.send(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("WBE_ZALO_TOKEN_REFRESH_FAILED");
        verify(zaloApiClient, never()).sendTemplateMessage(anyString(), any());
    }
}
