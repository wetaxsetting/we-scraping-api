package com.kosign.wecms.domain.payment.dispatch.callback;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Inbound EFS-callback access model (01301 / 01302). Bound from
 * {@code wecms.dispatch.callback.*}.
 *
 * <h2>Legacy behaviour</h2>
 * The legacy callbacks ({@code FILE_DEPOSIT_TRANSFER_API1/API2_act.jsp}) are JEX
 * WSVC endpoints with <b>no session / JWT</b> — the gateway authenticates by the
 * shared per-VAN secret: every payload carries {@code VV} (HMAC-SHA256) + {@code EV}
 * (AES) verified against {@code TB_VAN_API_INFO.API_KEY}. Network reachability was
 * additionally constrained by a source-IP allow-list at the firewall / Coocon
 * smart-gateway ({@code x-coocon-accessIp}).
 *
 * <h2>Refactor model</h2>
 * The callback endpoints are <b>permitted</b> in the security chain (a JWT cannot be
 * presented by the gateway). Authentication is the {@code VV} verification performed
 * inside the callback services ({@code EfsCryptoService.verifyVv}); this class adds
 * the network-layer defence — an optional source-IP allow-list enforced by
 * {@link CallbackIpWhitelistFilter}.
 *
 * <p>{@code allowedIps} empty (default) → no IP restriction (R&D / before the
 * gateway IPs are known); the {@code VV} crypto check is then the sole gate. Set the
 * gateway IP(s) in a real environment to fail-close at the network layer (B7).
 */
@Component
@ConfigurationProperties(prefix = "wecms.dispatch.callback")
@Getter
@Setter
public class CallbackProperties {

    /** Path prefix the inbound EFS callbacks are mounted under. */
    private String pathPrefix = "/api/v1/payment/dispatch/callbacks";

    /** Allowed gateway source IPs. Empty → no IP restriction (rely on VV crypto). */
    private List<String> allowedIps = new ArrayList<>();

    /** Trust the first {@code X-Forwarded-For} hop (when behind a proxy / load balancer). */
    private boolean trustForwardedFor = true;
}
