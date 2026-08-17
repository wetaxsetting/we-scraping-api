package com.kosign.wecms.domain.setting.zalooa.client;

/**
 * What Zalo's token endpoint returns, for both {@code authorization_code} and
 * {@code refresh_token} grants.
 *
 * <p>A client-port type, not a wire DTO — it never reaches a controller, so it
 * carries no Jackson annotations. {@link RestClientZaloApiClient} reads the
 * vendor JSON as a tree and builds this by hand, which keeps the global
 * {@code SNAKE_CASE} mapper configuration irrelevant to the vendor contract.
 *
 * <p><b>{@code refreshToken} is not optional to persist.</b> Zalo issues a fresh
 * refresh token on every grant and invalidates the one just used, so a caller
 * that stores {@code accessToken} but drops this field loses the connection at
 * the next refresh and a human has to redo the OAuth popup.
 *
 * @param accessToken      short-lived bearer value sent as the
 *                         {@code access_token} header on message calls
 * @param refreshToken     the <b>rotated</b> refresh token — always write it back
 * @param expiresInSeconds TTL Zalo reports for {@code accessToken}. Zalo sends
 *                         this as a JSON <i>string</i>, so the client parses it
 *                         rather than binding it as a number.
 */
public record ZaloTokenGrant(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}
