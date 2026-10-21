package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request;

/**
 * Request body for {@code POST /api/v1/banking/own-account-transfer/nxisas/decrypt}
 * (and {@code /nxisas/encrypt}). Refactor port of legacy JEX
 * {@code nxisas_decrypt} / {@code nxisas_encrypt} actions
 * ({@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_decrypt_act.jsp}).
 *
 * <p>Runs the Coocon SEED-CBC cipher ({@code ISASSeedCBC}, bundled as
 * {@code libs/isas1.0.jar}) over every engine {@code Output.Result} in the
 * posted job array, keyed by the session {@code NXISAS_UID} /
 * {@code NXISAS_ACTION} — exactly as legacy does. The engine encrypts
 * {@code Output.Result} on every response, including {@code ErrorCode
 * "00000000"}, so this endpoint is on the happy path, not an error path.
 *
 * <p><b>Wire names are lowercase snake_case</b> — {@code nxisas_data} /
 * {@code nxisas_uid} / {@code nxisas_action} — produced by the global
 * {@code PropertyNamingStrategies.SNAKE_CASE} strategy ({@code JacksonConfig:25}).
 * The only caller is the refactor's own {@code decryptNxisasResult}
 * ({@code wecms-web/lib/api/banking.ts}); the vendor bundle's own
 * {@code nxisas_decrypt} call is intercepted client-side by the JEX adapter
 * ({@code wecms-web/lib/nxisas/jexjs-adapter.ts}) and never reaches this
 * endpoint, so no UPPER_SNAKE compatibility surface is required here.
 *
 * <p>Corrected 2026-08-18: the components were previously declared
 * UPPER_SNAKE with no {@code @JsonProperty}, so the naming strategy exposed
 * them as snake_case while the caller sent the UPPER form — every field bound
 * {@code null} and the endpoint answered HTTP 200 with a null payload. The
 * Javadoc's claim that "the endpoint accepts the UPPER_SNAKE form verbatim"
 * was never implemented and has been removed.
 */
public record NxisasDecryptRequest(
        String nxisasData,
        String nxisasUid,
        String nxisasAction
) { }
