package com.kosign.wecms.domain.payment.validation;

import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.domain.payment.validation.entity.BlockedCharsRule;
import com.kosign.wecms.domain.payment.validation.repository.BlockedCharsRuleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpecialCharValidator} (audit task-44508). The validator
 * delegates status + effective-date filtering to
 * {@link BlockedCharsRuleRepository#findActiveRules(String)} (JPQL WHERE), so
 * these tests mock the repository to return the "already-active" set (its
 * contract) and exercise the validator's own logic: per-character membership,
 * union + dedup across rules, ORDER-preserving FULL-set message (not
 * found-only), case sensitivity, blank handling, HTML-sensitive chars, and the
 * dispatch-time {@code validateAnyType} union across payment types.
 *
 * <p>Message assertions run against the REAL {@code messages.properties} bundle
 * ({@code payment.upload.error.blocked_chars = "{0} must not contain: {1}"},
 * {@code payment.upload.field.RMKS = "Remark"} …) so the wire message format is
 * verified end-to-end, not stubbed.
 */
class SpecialCharValidatorTest {

    private static final String BANK = "11704088"; // Shinhan

    private BlockedCharsRuleRepository repo;
    private SpecialCharValidator validator;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        repo = mock(BlockedCharsRuleRepository.class);
        ResourceBundleMessageSource src = new ResourceBundleMessageSource();
        src.setBasename("messages");
        src.setDefaultEncoding("UTF-8");
        validator = new SpecialCharValidator(repo, new MessageResolver(src));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static BlockedCharsRule rule(String bank, String pymnType, String field, String chars) {
        BlockedCharsRule r = new BlockedCharsRule();
        r.setBankCd(bank);
        r.setPymnType(pymnType);
        r.setFieldCd(field);
        r.setBlockedChars(chars);
        r.setStatus("Y");
        return r;
    }

    private void activeRules(BlockedCharsRule... rules) {
        when(repo.findActiveRules(anyString())).thenReturn(List.of(rules));
    }

    @Test
    void noRuleForTuple_passes() {
        when(repo.findActiveRules(anyString())).thenReturn(List.of());
        assertThat(validator.validate(BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "a@b"))
                .isEmpty();
    }

    @Test
    void blankValue_passes() {
        activeRules(rule(BANK, "INTR", "RMKS", "@$&"));
        assertThat(validator.validate(BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, ""))
                .isEmpty();
        assertThat(validator.validate(BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, null))
                .isEmpty();
    }

    @Test
    void blankBankCd_passes() {
        activeRules(rule(BANK, "INTR", "RMKS", "@$&"));
        assertThat(validator.validate("", PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "a@b"))
                .isEmpty();
        assertThat(validator.validate(null, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "a@b"))
                .isEmpty();
    }

    @Test
    void oneBlockedCharFound_reportsError() {
        activeRules(rule(BANK, "INTR", "RMKS", "@$&"));
        Optional<String> msg = validator.validate(
                BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "pay@now");
        assertThat(msg).isPresent();
        assertThat(msg.get()).isEqualTo("Remark must not contain: @ $ &");
    }

    @Test
    void noBlockedCharFound_passes() {
        activeRules(rule(BANK, "INTR", "RMKS", "@$&"));
        assertThat(validator.validate(BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "clean text"))
                .isEmpty();
    }

    @Test
    void message_showsFullConfiguredSet_notOnlyFoundChars() {
        // Value contains only '@', but the message must list the ENTIRE set.
        activeRules(rule(BANK, "INTR", "RMKS", "@$&"));
        Optional<String> msg = validator.validate(
                BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "a@@@b");
        assertThat(msg).isPresent();
        assertThat(msg.get()).endsWith(": @ $ &");   // full set, not "@" only
    }

    @Test
    void message_preservesConfiguredOrder() {
        activeRules(rule(BANK, "INTR", "RMKS", "<>@"));
        Optional<String> msg = validator.validate(
                BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "x<y");
        assertThat(msg).isPresent();
        assertThat(msg.get()).endsWith(": < > @");
    }

    @Test
    void multipleRulesSameKey_unionedAndDeduped_orderPreserved() {
        activeRules(
                rule(BANK, "INTR", "RMKS", "@$"),
                rule(BANK, "INTR", "RMKS", "$&"));   // '$' duplicate across rules
        Optional<String> msg = validator.validate(
                BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "a&b");
        assertThat(msg).isPresent();
        assertThat(msg.get()).endsWith(": @ $ &");   // deduped, first-seen order
    }

    @Test
    void matching_isCaseSensitive() {
        activeRules(rule(BANK, "INTR", "RMKS", "A"));
        assertThat(validator.validate(BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "abc"))
                .isEmpty();                                  // lowercase 'a' not blocked
        assertThat(validator.validate(BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "Abc"))
                .isPresent();                                // uppercase 'A' blocked
    }

    @Test
    void htmlSensitiveChars_renderAsPlainText() {
        activeRules(rule(BANK, "INTR", "RMKS", "<>&\"'"));
        Optional<String> msg = validator.validate(
                BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "a<b");
        assertThat(msg).isPresent();
        // Chars appear verbatim (no HTML escaping) space-separated.
        assertThat(msg.get()).endsWith(": < > & \" '");
    }

    @Test
    void differentPymnType_isolatesRules() {
        activeRules(rule(BANK, "VEND", "RMKS", "@"));   // rule only for VEND
        assertThat(validator.validate(BANK, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, "a@b"))
                .isEmpty();                                  // INTR has no rule
        assertThat(validator.validate(BANK, PymnType.VENDOR_PAYMENT, FieldCd.REMARKS, "a@b"))
                .isPresent();
    }

    @Test
    void validateAnyType_unionsAcrossPaymentTypes_forDispatch() {
        // '@' blocked under INTR, '$' blocked under VEND — same bank + field.
        activeRules(
                rule(BANK, "INTR", "RMKS", "@"),
                rule(BANK, "VEND", "RMKS", "$"));
        // A value containing only '$' still trips the dispatch-time union check.
        Optional<String> msg = validator.validateAnyType(BANK, FieldCd.REMARKS, "a$b");
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("@").contains("$");
    }

    @Test
    void validateAnyType_blankOrNoRule_passes() {
        when(repo.findActiveRules(anyString())).thenReturn(List.of());
        assertThat(validator.validateAnyType(BANK, FieldCd.REMARKS, "a@b")).isEmpty();
        activeRules(rule(BANK, "INTR", "RMKS", "@"));
        assertThat(validator.validateAnyType(BANK, FieldCd.REMARKS, "")).isEmpty();
        assertThat(validator.validateAnyType("", FieldCd.REMARKS, "a@b")).isEmpty();
    }
}
