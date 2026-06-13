package com.kosign.wecms.domain.payment.dispatch.recipient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs201Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs201Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * {@code WGVN_EFS_201} — account-holder (depositor) name inquiry (REST API guide
 * ver 1.2.2 §5.3). Refactor of the legacy depositor inquiry (svc
 * {@code WB_00000100}), via the EFS REST gateway.
 *
 * <p>Flow: build {@link Efs201Request} → serialise (global SNAKE_CASE) → resolve
 * key by {@code vanOrgCd} ({@link VanApiKeyService}) → build {@code EV}/{@code VV}
 * ({@link EfsCryptoService}) → wrap in an {@link EfsEnvelope} → POST via
 * {@link EfsApiClient#send EfsApiClient.EFS_201} → on {@code RC=0000}, decrypt the
 * response {@code EV} and map {@code finc_mda_owner_nm} + {@code ant_div_cd}.
 *
 * <p><b>Integration point (NOT wired in D1):</b> the live path's per-tenant
 * context (VAN_ORG_CD / OUT_BANK_CD / BANK_PRTN_ID / WDRL_ACT_NO) is owned by
 * {@link com.kosign.wecms.common.firmbanking.FirmBankingInquiryService} (the
 * {@code TB_FIRM_CONF} gate + UAT overrides). That seam is the legacy
 * <i>socket</i> WB_00000100 variant returning {@code Optional<String>}; this is
 * the <i>REST</i> WGVN_EFS_201 variant returning owner + network. They are kept
 * separate here — a future MR may have {@code FirmBankingInquiryService}
 * delegate to this service once the socket-vs-REST decision is settled. This
 * service therefore takes its context as a {@link RecipientLookupCommand}
 * parameter and does not modify the existing seam.
 *
 * <p><b>Live calls are off by default</b> ({@code wecms.dispatch.efs.enabled=false}
 * ⇒ {@code MockEfsApiClient} throws). No real key is used outside tests (the key
 * comes from {@code TB_VAN_API_INFO} — B2). Logging never emits {@code API_KEY},
 * {@code EV}, {@code VV}, or the decrypted payload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipientLookupService {

    private static final DateTimeFormatter RQ_DTIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int TNO_MAX = 50;

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;

    /**
     * Resolve an account-holder name + network for the given firm-banking context.
     *
     * @return owner name + {@code ant_div_cd} on {@code RC=0000}
     * @throws IllegalArgumentException on missing required input
     * @throws IllegalStateException    when the API key is not provisioned (B2,
     *                                  surfaced from {@link VanApiKeyService})
     * @throws BusinessException        ({@code WBE_EFS_GATEWAY_ERROR}) on a
     *                                  non-{@code 0000} gateway result
     */
    public RecipientLookupResult lookup(RecipientLookupCommand cmd) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());

        Efs201Request request = new Efs201Request(
                cmd.bankCd(), cmd.prtnId(), cmd.tarBankCd(), cmd.tarFincMdaDiv(),
                cmd.tarFincMdaNo(), null, cmd.withdAcctNo());
        String businessJson = serialize(request);

        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String tno = newTno(rqDtime);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);
        String id = StringUtils.hasText(efsProperties.getPartnerId())
                ? efsProperties.getPartnerId() : cmd.vanOrgCd();

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_201, requestEnvelope);

        if (response == null || !FirmBankingResultCd.isSuccess(response.rc())) {
            // Do not swallow gateway errors; surface via the standard envelope.
            String rc = response == null ? null : response.rc();
            log.warn("EFS 201 lookup failed vanOrgCd={} tarBankCd={} acct={} rc={}",
                    cmd.vanOrgCd(), cmd.tarBankCd(), mask(cmd.tarFincMdaNo()), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        // Verify the response MAC before trusting / decrypting the EV — legacy
        // SecurityUtil.VerifyMac in WB_00000100:152 (throws "검증 오류" on mismatch).
        // G003 = 암복호화 검증오류 (encryption verification error).
        if (!efsCryptoService.verifyVv(response.ev(), response.vv(), apiKey)) {
            log.warn("EFS 201 VV verification failed vanOrgCd={} tarBankCd={} acct={}",
                    cmd.vanOrgCd(), cmd.tarBankCd(), mask(cmd.tarFincMdaNo()));
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", FirmBankingResultCd.ENC_VRFY_ERR);
        }

        String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
        Efs201Response parsed = deserialize(responseJson, Efs201Response.class);

        log.debug("EFS 201 lookup ok vanOrgCd={} tarBankCd={} acct={} rc={}",
                cmd.vanOrgCd(), cmd.tarBankCd(), mask(cmd.tarFincMdaNo()), response.rc());
        return new RecipientLookupResult(parsed.fincMdaOwnerNm(), parsed.antDivCd());
    }

    private void validate(RecipientLookupCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("RecipientLookupCommand is required");
        }
        if (!StringUtils.hasText(cmd.vanOrgCd())) {
            throw new IllegalArgumentException("vanOrgCd is required");
        }
        if (!StringUtils.hasText(cmd.tarBankCd())) {
            throw new IllegalArgumentException("tarBankCd is required");
        }
        if (!StringUtils.hasText(cmd.tarFincMdaNo())) {
            throw new IllegalArgumentException("tarFincMdaNo is required");
        }
    }

    private String newTno(String rqDtime) {
        String tno = rqDtime + UUID.randomUUID().toString().replace("-", "");
        return tno.length() > TNO_MAX ? tno.substring(0, TNO_MAX) : tno;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // Never include the payload in the message (it carries account data).
            throw new IllegalStateException("Failed to serialise EFS 201 request", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EFS 201 response", e);
        }
    }

    /** Mask all but the last 4 chars of an account/card number for logs. */
    private String mask(String acct) {
        if (acct == null || acct.length() <= 4) {
            return "****";
        }
        return "****" + acct.substring(acct.length() - 4);
    }
}
