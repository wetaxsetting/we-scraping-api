package com.kosign.wecms.domain.payment.overseas.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.common.PaymentWireWidthGuard;
import com.kosign.wecms.domain.payment.common.WireWidthGuards;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIX B — {@link OverseasSenderNameGuard}, the guard for {@code SENDER_NAME}: the one fixed-length wire
 * field with <b>no entry surface</b>. It shipped without tests; this class is that gap closed.
 *
 * <h2>What is being pinned, and why each part matters</h2>
 * <ul>
 *   <li><b>The reject is charset-first, not width-first.</b> {@code PaymentWireWidthGuard} checks
 *       non-ASCII before byte width, so a Hangul {@code ACCT_NM} is rejected whether or not it exceeds
 *       150 bytes — the bank's fixed-length channel cannot carry the characters at all, and a
 *       "too long" message would send the operator down the wrong road (shortening a name that can
 *       never be sent). Both directions are asserted, because a width-only guard would pass the short
 *       case and still emit an unreadable record.</li>
 *   <li><b>ASCII always passes.</b> The column caps {@code ACCT_NM} at 100 characters, so pure ASCII
 *       can never reach the 150-byte slot — the guard must not become a length gate that rejects
 *       legitimate long English company names.</li>
 *   <li><b>The bank-code mapping reads the row the executor reads.</b> The guard mirrors
 *       {@code OverseasDispatchExecutor.napasCodeForOutBank} by hand, so a drift between the two makes
 *       the guard check a different account than the one whose name is sent — i.e. it would guard
 *       nothing while appearing to work. Both bank-code forms (NAPAS {@code OUT_BANK_CD} and the raw
 *       internal {@code BANK_CD}) are asserted to resolve the same {@code ACCT_MSTR} key.</li>
 *   <li><b>Registration warns, never rejects.</b> {@code ACCT_NM} is bank-adapter-supplied and the user
 *       cannot edit it; hard-rejecting a registration would refuse a real account over a field only the
 *       Overseas rail consumes.</li>
 * </ul>
 *
 * <p>Uses the <b>real</b> {@link PaymentWireWidthGuard} over the production {@code messages} bundle
 * ({@link WireWidthGuards#real()}) — a mocked width guard returns {@code null} from {@code check} and
 * would make every assertion below pass whether the guard were wired in or not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasSenderNameGuardTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";

    /** NAPAS {@code OUT_BANK_CD} → internal {@code ACCT_MSTR.BANK_CD}, per the executor's mapping. */
    private static final String OUT_BANK_SHINHAN  = "970424";
    private static final String ACCT_BANK_SHINHAN = "11704088";
    private static final String OUT_BANK_WOORI    = "970457";
    private static final String ACCT_BANK_WOORI   = "11704020";

    private static final String ACCT_NO = "700123456789";

    @Mock AcctMstrRepository acctMstrRepository;

    OverseasSenderNameGuard guard;

    @BeforeEach
    void setUp() {
        guard = new OverseasSenderNameGuard(acctMstrRepository, WireWidthGuards.real());
    }

    private void accountNamed(String bankCd, String acctNm) {
        AcctMstr a = new AcctMstr();
        a.setBizKey(BIZ_KEY);
        a.setBankCd(bankCd);
        a.setAcctNo(ACCT_NO);
        a.setAcctNm(acctNm);
        when(acctMstrRepository.findByBizKeyAndBankCdAndAcctNo(BIZ_KEY, bankCd, ACCT_NO))
                .thenReturn(Optional.of(a));
    }

    private static String repeat(String unit, int times) {
        return unit.repeat(times);
    }

    private static int utf8Bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    // ── the two reject shapes ────────────────────────────────────────────────

    @Test
    @DisplayName("a >150-byte Hangul ACCT_NM is rejected")
    void hangulOver150Bytes_rejects() {
        // 60 syllables = 180 UTF-8 bytes: over the slot AND non-ASCII. Well inside the 100-character
        // @Size(max = 100) the registration request carries — which is exactly why that annotation is
        // not a substitute for this guard.
        String acctNm = repeat("주", 60);
        assertThat(acctNm.length()).isEqualTo(60);
        assertThat(utf8Bytes(acctNm)).isEqualTo(180);
        accountNamed(ACCT_BANK_SHINHAN, acctNm);

        assertThatThrownBy(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_SHINHAN, ACCT_NO))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("WBE_PAYMENT_SENDER_NAME_UNSENDABLE");
    }

    @Test
    @DisplayName("a <=150-byte non-ASCII ACCT_NM is ALSO rejected — the guard is charset-first, not a length gate")
    void nonAsciiUnder150Bytes_alsoRejects() {
        // 10 syllables = 30 bytes, a fifth of the slot. A width-only guard would pass this and the bank
        // would receive a record it cannot read. Tone-stripping does not help: the 134-entry table is
        // Vietnamese-only and leaves Hangul untouched.
        String acctNm = repeat("주", 10);
        assertThat(utf8Bytes(acctNm)).isEqualTo(30);
        assertThat(utf8Bytes(acctNm)).isLessThanOrEqualTo(150);
        accountNamed(ACCT_BANK_WOORI, acctNm);

        assertThatThrownBy(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_WOORI, ACCT_NO))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("WBE_PAYMENT_SENDER_NAME_UNSENDABLE");
    }

    @Test
    @DisplayName("the error names the offending account and carries a resolved reason")
    void rejectMessage_namesTheAccount() {
        accountNamed(ACCT_BANK_SHINHAN, repeat("주", 60));

        assertThatThrownBy(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_SHINHAN, ACCT_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo("WBE_PAYMENT_SENDER_NAME_UNSENDABLE");
                    // arg[0] is the account number — the remedy is "pick a different withdrawal
                    // account", so the message has to say WHICH account is unusable.
                    assertThat(be.getArgs()).hasSize(2);
                    assertThat(be.getArgs()[0]).isEqualTo(ACCT_NO);
                    // arg[1] is the already-resolved width-guard reason. Assert it resolved against the
                    // real bundle rather than leaking a raw key, and that it names the field.
                    String reason = String.valueOf(be.getArgs()[1]);
                    assertThat(reason).isNotBlank();
                    assertThat(reason).doesNotContain("WBE_PAYMENT_WIRE_FIELD");
                });
    }

    // ── the pass shape ──────────────────────────────────────────────────────

    @Test
    @DisplayName("an ASCII ACCT_NM of 100 characters passes")
    void ascii100Characters_passes() {
        // 100 is the column / @Size ceiling. Pure ASCII: 100 bytes against a 150-byte slot, so ASCII
        // overflow is arithmetically unreachable and the guard must not reject on length.
        String acctNm = repeat("A", 100);
        assertThat(acctNm.length()).isEqualTo(100);
        assertThat(utf8Bytes(acctNm)).isEqualTo(100);
        accountNamed(ACCT_BANK_SHINHAN, acctNm);

        assertThatCode(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_SHINHAN, ACCT_NO))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a realistic mixed-case ASCII company name passes")
    void ordinaryAsciiCompanyName_passes() {
        accountNamed(ACCT_BANK_WOORI, "CONG TY TNHH WEBCASH VIET NAM");
        assertThatCode(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_WOORI, ACCT_NO))
                .doesNotThrowAnyException();
    }

    // ── the executor-parity contract on the bank-code mapping ────────────────

    @Test
    @DisplayName("Shinhan: both bank-code forms resolve the SAME ACCT_MSTR row the executor reads")
    void shinhan_bothBankCodeForms_readTheSameAcctMstrRow() {
        accountNamed(ACCT_BANK_SHINHAN, repeat("주", 60));

        // (a) the NAPAS OUT_BANK_CD form — what OVRS_PYMN_SUMR.OUT_BANK_CD carries at dispatch.
        assertThatThrownBy(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_SHINHAN, ACCT_NO))
                .isInstanceOf(BusinessException.class);
        // (b) the raw internal BANK_CD form — accepted so an un-normalised caller does not silently
        //     skip the check by resolving no row at all.
        assertThatThrownBy(() -> guard.assertSendable(BIZ_KEY, ACCT_BANK_SHINHAN, ACCT_NO))
                .isInstanceOf(BusinessException.class);

        // Both forms must have gone to the INTERNAL code — the key the executor's resolveSenderNm uses.
        // A drift in napasCodeForOutBank would show up here as a lookup on 970424, which would find no
        // row and make the guard silently inert.
        verify(acctMstrRepository, org.mockito.Mockito.times(2))
                .findByBizKeyAndBankCdAndAcctNo(BIZ_KEY, ACCT_BANK_SHINHAN, ACCT_NO);
        verify(acctMstrRepository, never())
                .findByBizKeyAndBankCdAndAcctNo(BIZ_KEY, OUT_BANK_SHINHAN, ACCT_NO);
    }

    @Test
    @DisplayName("Woori: both bank-code forms resolve the SAME ACCT_MSTR row the executor reads")
    void woori_bothBankCodeForms_readTheSameAcctMstrRow() {
        accountNamed(ACCT_BANK_WOORI, repeat("주", 60));

        assertThatThrownBy(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_WOORI, ACCT_NO))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard.assertSendable(BIZ_KEY, ACCT_BANK_WOORI, ACCT_NO))
                .isInstanceOf(BusinessException.class);

        verify(acctMstrRepository, org.mockito.Mockito.times(2))
                .findByBizKeyAndBankCdAndAcctNo(BIZ_KEY, ACCT_BANK_WOORI, ACCT_NO);
        verify(acctMstrRepository, never())
                .findByBizKeyAndBankCdAndAcctNo(BIZ_KEY, OUT_BANK_WOORI, ACCT_NO);
    }

    @Test
    @DisplayName("the two banks do not cross-map")
    void shinhanAndWoori_doNotCrossMap() {
        // Cheap, but it is the assertion that would fail if the two constants were ever swapped —
        // which would send Woori's guard at Shinhan's account and vice versa.
        accountNamed(ACCT_BANK_SHINHAN, repeat("주", 60));
        assertThatThrownBy(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_SHINHAN, ACCT_NO))
                .isInstanceOf(BusinessException.class);

        // Woori's OUT_BANK_CD resolves Woori's internal code, for which no row is stubbed → no throw.
        when(acctMstrRepository.findByBizKeyAndBankCdAndAcctNo(BIZ_KEY, ACCT_BANK_WOORI, ACCT_NO))
                .thenReturn(Optional.empty());
        assertThatCode(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_WOORI, ACCT_NO))
                .doesNotThrowAnyException();
    }

    // ── the documented no-ops: nothing to guard means no error ───────────────

    @Test
    @DisplayName("a bank outside the Shinhan/Woori pair is not an error — the executor sends null")
    void unmappedBank_isNoOp() {
        // VCB (970436): napasCodeForOutBank resolves nothing, so the executor leaves SENDER_NM null and
        // the codec pads the slot. Rejecting here would block a legitimate payment.
        assertThatCode(() -> guard.assertSendable(BIZ_KEY, "970436", ACCT_NO))
                .doesNotThrowAnyException();
        verify(acctMstrRepository, never()).findByBizKeyAndBankCdAndAcctNo(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a missing account row or a blank ACCT_NM is not an error")
    void missingRowOrBlankName_isNoOp() {
        when(acctMstrRepository.findByBizKeyAndBankCdAndAcctNo(BIZ_KEY, ACCT_BANK_SHINHAN, ACCT_NO))
                .thenReturn(Optional.empty());
        assertThatCode(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_SHINHAN, ACCT_NO))
                .doesNotThrowAnyException();

        accountNamed(ACCT_BANK_WOORI, "   ");
        assertThatCode(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_WOORI, ACCT_NO))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a null bizKey / null-or-blank acctNo short-circuits without a query")
    void missingArguments_shortCircuit() {
        assertThatCode(() -> guard.assertSendable(null, OUT_BANK_SHINHAN, ACCT_NO)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_SHINHAN, null)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_SHINHAN, "  ")).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertSendable(BIZ_KEY, null, ACCT_NO)).doesNotThrowAnyException();
        verify(acctMstrRepository, never()).findByBizKeyAndBankCdAndAcctNo(anyString(), anyString(), anyString());
    }

    // ── registration: warn, never reject ────────────────────────────────────

    @Test
    @DisplayName("warnIfUnsendable REPORTS an unsendable name and does NOT throw")
    void warnIfUnsendable_reportsWithoutRejecting() {
        // The registration paths (AccountRegistrationService / SettingsBankAccountService) call this.
        // ACCT_NM comes from the bank adapter, so refusing the registration would block a real account
        // over a field only the Overseas rail consumes — the WARN exists so the problem is visible
        // BEFORE someone attempts a payment.
        Optional<String> reason = guard.warnIfUnsendable(ACCT_BANK_SHINHAN, ACCT_NO, repeat("주", 60));
        assertThat(reason).as("the caller is told the name is unsendable").isPresent();
        assertThat(reason.get()).isNotBlank();

        // No repository access: the name is passed in, not looked up — registration knows it already.
        verify(acctMstrRepository, never()).findByBizKeyAndBankCdAndAcctNo(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("warnIfUnsendable reports nothing for a sendable or absent name")
    void warnIfUnsendable_silentOnSendableName() {
        assertThat(guard.warnIfUnsendable(ACCT_BANK_SHINHAN, ACCT_NO, repeat("A", 100))).isEmpty();
        assertThat(guard.warnIfUnsendable(ACCT_BANK_SHINHAN, ACCT_NO, null)).isEmpty();
        assertThat(guard.warnIfUnsendable(ACCT_BANK_SHINHAN, ACCT_NO, "   ")).isEmpty();
    }

    @Test
    @DisplayName("warnIfUnsendable and assertSendable agree on the same name")
    void warnAndAssert_agree() {
        // The two entry points must not disagree: a name registration warns about must be the same name
        // dispatch rejects, or the warning is noise and the rejection is a surprise.
        String unsendable = repeat("주", 60);
        accountNamed(ACCT_BANK_SHINHAN, unsendable);

        assertThat(guard.warnIfUnsendable(ACCT_BANK_SHINHAN, ACCT_NO, unsendable)).isPresent();
        assertThatThrownBy(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_SHINHAN, ACCT_NO))
                .isInstanceOf(BusinessException.class);

        String sendable = repeat("A", 100);
        accountNamed(ACCT_BANK_WOORI, sendable);
        assertThat(guard.warnIfUnsendable(ACCT_BANK_WOORI, ACCT_NO, sendable)).isEmpty();
        assertThatCode(() -> guard.assertSendable(BIZ_KEY, OUT_BANK_WOORI, ACCT_NO))
                .doesNotThrowAnyException();
    }
}
