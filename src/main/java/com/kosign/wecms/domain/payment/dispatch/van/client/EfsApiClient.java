package com.kosign.wecms.domain.payment.dispatch.van.client;

import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;

/**
 * Port for the EFS (전자금융서비스) smart-gateway HTTP client — the refactor seam
 * replacing the legacy {@code util.ConnectionUtil} REST calls. Transports the
 * encrypted {@link EfsEnvelope} (form-urlencoded POST) to a {@code WGVN_EFS_*.jct}
 * endpoint and returns the parsed response envelope.
 *
 * <p>This port carries the envelope only — it does <b>not</b> build {@code EV} /
 * verify {@code VV} (that is
 * {@link com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService},
 * applied by the service layer before/after the call) and does <b>not</b> know
 * the {@code API_KEY}.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link RestClientEfsApiClient} — real adapter, active when
 *       {@code wecms.dispatch.efs.enabled=true} (DEV/SIT, base URL configured).</li>
 *   <li>{@link MockEfsApiClient} — default/offline; throws on call so no live
 *       request can leave by accident.</li>
 * </ul>
 *
 * <p>Endpoint keys are resolved to vendor paths via {@code EfsProperties.endpoints}:
 * <pre>
 *   EFS_201 → /WGVN_EFS_201.jct   (depositor inquiry)
 *   EFS_202 → /WGVN_EFS_202.jct   (real-time transfer)
 *   EFS_203 → /WGVN_EFS_203.jct   (result inquiry)
 *   EFS_301 → /WGVN_EFS_301.jct   (bulk reception request)
 *   EFS_302 → /WGVN_EFS_302.jct   (bulk execution request)
 * </pre>
 *
 * <p><b>TODO(OQ-PDF-1):</b> {@code WGVN_EFS_204} is not present in REST API guide
 * ver 1.2.2 and is intentionally NOT mapped here. Confirm whether it exists in
 * another document/version or is retired before adding an {@code EFS_204} key.
 */
public interface EfsApiClient {

    String EFS_201 = "EFS_201";
    String EFS_202 = "EFS_202";
    String EFS_203 = "EFS_203";
    String EFS_301 = "EFS_301";
    String EFS_302 = "EFS_302";

    /**
     * POST the request envelope (form-urlencoded) to the endpoint resolved from
     * {@code endpointKey} and return the parsed response envelope.
     *
     * @param endpointKey one of {@link #EFS_201}…{@link #EFS_302}
     * @param request     the request-mode envelope (ID/RQ_DTIME/TNO/EV/VV/EM/VM)
     * @return the response-mode envelope (ID/RS_DTIME/TNO/EV/VV/EM/VM/RC/RM)
     */
    EfsEnvelope send(String endpointKey, EfsEnvelope request);
}
