package com.kosign.wecms.domain.payment.dispatch.van.client;

import com.kosign.wecms.domain.payment.dispatch.repository.TbVanApiInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the per-VAN AES/HMAC {@code API_KEY} by org code — refactor of legacy
 * {@code util.CommUtil.getApiKey(van_org_cd)} (which ran
 * {@code SELECT API_KEY FROM TB_VAN_API_INFO WHERE VAN_ORG_CD = ?} via IDO
 * {@code TB_VAN_API_INFO_R001}). The key feeds
 * {@link com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService}
 * to build {@code EV} / verify {@code VV}.
 *
 * <p>This is a read-only DB lookup — <b>not</b> a live EFS call. It is
 * foundation-safe to wire now (B3 applied {@code TB_VAN_API_INFO} via V248).
 *
 * <p><b>TODO(B2):</b> the table ships empty (V248 = schema only, no secret
 * seeded). Until the per-VAN key is provisioned out-of-band, {@link #getApiKey}
 * throws — by design, so no crypto can run against a money-moving path with a
 * missing key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VanApiKeyService {

    private final TbVanApiInfoRepository tbVanApiInfoRepository;

    /**
     * Return the {@code API_KEY} for a VAN org code.
     *
     * @throws IllegalStateException when no credential row exists for the org
     *         code (table not yet provisioned — TODO(B2)).
     */
    @Transactional(readOnly = true)
    public String getApiKey(String vanOrgCd) {
        return tbVanApiInfoRepository.findByVanOrgCd(vanOrgCd)
                .map(com.kosign.wecms.domain.payment.dispatch.entity.TbVanApiInfo::getApiKey)
                .orElseThrow(() -> new IllegalStateException(
                        "No TB_VAN_API_INFO.API_KEY provisioned for VAN_ORG_CD=" + vanOrgCd
                                + " — blocked on B2 (secure-channel key provisioning)."));
    }
}
