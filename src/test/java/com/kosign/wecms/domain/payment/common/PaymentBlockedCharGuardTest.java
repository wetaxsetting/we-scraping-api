package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.validation.FieldCd;
import com.kosign.wecms.domain.payment.validation.PymnType;
import com.kosign.wecms.domain.payment.validation.SpecialCharValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentBlockedCharGuard} (audit task-44508 P0) — the
 * authoritative save-path + dispatch blocked-char enforcement. Verifies it
 * throws {@code WBE_PAYMENT_BLOCKED_CHARS} on the first offending field and
 * passes clean input, and that the dispatch variant uses
 * {@code validateAnyType}.
 */
class PaymentBlockedCharGuardTest {

    private static final String BANK = "11704088";

    private SpecialCharValidator validator;
    private PaymentBlockedCharGuard guard;

    @BeforeEach
    void setUp() {
        validator = mock(SpecialCharValidator.class);
        guard = new PaymentBlockedCharGuard(validator);
    }

    @Test
    void assertClean_throwsBlockedCharsCode_onFirstViolation() {
        when(validator.validate(eq(BANK), eq(PymnType.VENDOR_PAYMENT), eq(FieldCd.REMARKS), any()))
                .thenReturn(Optional.of("Remark must not contain: @ $ &"));

        assertThatThrownBy(() -> guard.assertClean(BANK, PymnType.VENDOR_PAYMENT,
                List.of(PaymentBlockedCharGuard.field(FieldCd.REMARKS, "a@b"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("WBE_PAYMENT_BLOCKED_CHARS"));
    }

    @Test
    void assertClean_passes_whenNoFieldBlocked() {
        when(validator.validate(any(), any(), any(), any())).thenReturn(Optional.empty());
        assertThatCode(() -> guard.assertClean(BANK, PymnType.VENDOR_PAYMENT,
                List.of(
                        PaymentBlockedCharGuard.field(FieldCd.REMARKS, "clean"),
                        PaymentBlockedCharGuard.field(FieldCd.RECIPIENT_NAME, "John Micah"))))
                .doesNotThrowAnyException();
    }

    @Test
    void assertClean_nullFieldList_passes() {
        assertThatCode(() -> guard.assertClean(BANK, PymnType.VENDOR_PAYMENT, null))
                .doesNotThrowAnyException();
    }

    @Test
    void assertCleanForDispatch_usesValidateAnyType_andThrowsOnHit() {
        when(validator.validateAnyType(eq(BANK), eq(FieldCd.REMARKS), any()))
                .thenReturn(Optional.of("Remark must not contain: @"));

        assertThatThrownBy(() -> guard.assertCleanForDispatch(BANK,
                List.of(PaymentBlockedCharGuard.field(FieldCd.REMARKS, "a@b"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("WBE_PAYMENT_BLOCKED_CHARS"));
    }

    @Test
    void assertCleanForDispatch_passes_whenClean() {
        when(validator.validateAnyType(any(), any(), any())).thenReturn(Optional.empty());
        assertThatCode(() -> guard.assertCleanForDispatch(BANK,
                List.of(PaymentBlockedCharGuard.field(FieldCd.REMARKS, "clean"))))
                .doesNotThrowAnyException();
    }
}
