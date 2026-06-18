package com.kosign.wecms.domain.banking.nxisas;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the shared NXISAS result-code policy. Pins the legacy
 * {@code nxisas_api.js isSuccess()} whitelist (00000000 / 42110000 /
 * 42120011 / 42120111) and the gateway-envelope success forms.
 */
class NxisasResultCodesTest {

    @Test
    void bankAdapterWhitelistMatchesLegacyIsSuccess() {
        assertThat(NxisasResultCodes.isBankAdapterSuccess("00000000")).isTrue();
        assertThat(NxisasResultCodes.isBankAdapterSuccess("42110000")).isTrue();
        // redmine #36034 + sibling — non-fatal in legacy nxisas_api.js.
        assertThat(NxisasResultCodes.isBankAdapterSuccess("42120011")).isTrue();
        assertThat(NxisasResultCodes.isBankAdapterSuccess("42120111")).isTrue();
    }

    @Test
    void bankAdapterTreatsNullOrBlankAsSuccess() {
        // No adapter verdict to act on → success (matches the prior inline
        // `code != null && !code.isBlank()` guard).
        assertThat(NxisasResultCodes.isBankAdapterSuccess(null)).isTrue();
        assertThat(NxisasResultCodes.isBankAdapterSuccess("")).isTrue();
        assertThat(NxisasResultCodes.isBankAdapterSuccess("   ")).isTrue();
    }

    @Test
    void bankAdapterRejectsUnknownCodes() {
        assertThat(NxisasResultCodes.isBankAdapterSuccess("80010011")).isFalse(); // wrong login
        assertThat(NxisasResultCodes.isBankAdapterSuccess("42120999")).isFalse(); // not whitelisted
        assertThat(NxisasResultCodes.isBankAdapterSuccess("0000")).isFalse();     // gateway short-form, not a bank code
    }

    @Test
    void gatewayErrorAcceptsSuccessAndShortForms() {
        assertThat(NxisasResultCodes.isGatewayError("00000000")).isFalse();
        assertThat(NxisasResultCodes.isGatewayError("0000")).isFalse();
        assertThat(NxisasResultCodes.isGatewayError("OK")).isFalse();
        assertThat(NxisasResultCodes.isGatewayError(null)).isFalse();
        assertThat(NxisasResultCodes.isGatewayError("")).isFalse();
        assertThat(NxisasResultCodes.isGatewayError("90000001")).isTrue();
    }
}
