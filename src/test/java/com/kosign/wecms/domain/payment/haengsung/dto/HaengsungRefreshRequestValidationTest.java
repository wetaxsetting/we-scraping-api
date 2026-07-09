package com.kosign.wecms.domain.payment.haengsung.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Bean-validation rules for the Refresh request (task item: request validation). */
class HaengsungRefreshRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void init() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll  static void close() { factory.close(); }

    @Test void validSingleDate_passes() {
        assertThat(validator.validate(new HaengsungRefreshRequest("VENDOR", "20260628"))).isEmpty();
    }

    @Test void validRange_passes() {
        assertThat(validator.validate(new HaengsungRefreshRequest("ALL", "20260601-20260628"))).isEmpty();
    }

    @Test void lowercaseType_passesPattern() {
        // case-insensitive pattern; service parses with fromWire
        assertThat(validator.validate(new HaengsungRefreshRequest("overseas", "20260628"))).isEmpty();
    }

    @Test void unknownType_fails() {
        assertThat(validator.validate(new HaengsungRefreshRequest("CARD", "20260628"))).isNotEmpty();
    }

    @Test void blankType_fails() {
        assertThat(validator.validate(new HaengsungRefreshRequest("", "20260628"))).isNotEmpty();
    }

    @Test void badDate_fails() {
        assertThat(validator.validate(new HaengsungRefreshRequest("VENDOR", "2026-06-28"))).isNotEmpty();
        assertThat(validator.validate(new HaengsungRefreshRequest("VENDOR", "abc"))).isNotEmpty();
        assertThat(validator.validate(new HaengsungRefreshRequest("VENDOR", "202606"))).isNotEmpty();
    }
}
