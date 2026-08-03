package com.kosign.wecms.domain.payment.overseas.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.common.PaymentWireWidthGuard;
import com.kosign.wecms.domain.payment.common.WireField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * C-14 / FIX B — guard for {@code SENDER_NAME}, the one fixed-length wire field with <b>no entry
 * surface</b>.
 *
 * <h2>Why this field is special</h2>
 * The other three free-text slots ({@code receiverName}, and the two memos) are typed by a user, so the
 * C-14 entry guard sees them at upload and at save. {@code senderName} is not: it is derived
 * server-side at dispatch from {@code ACCT_MSTR.ACCT_NM}
 * ({@link OverseasDispatchExecutor} {@code resolveSenderNm}), so no upload cell, no request field and no
 * bean-validation annotation ever inspects it. Nothing else in the tree validates it either:
 * <ul>
 *   <li>{@code RegisterAccountsRequest.acctNm} / {@code BankAccountRegisterRequest.acctNm} carry
 *       {@code @Size(max = 100)} — a <b>character</b> cap mirroring the column. 100 Hangul characters
 *       satisfy it and occupy <b>300 UTF-8 bytes</b> against a 150-byte slot;</li>
 *   <li>legacy sanitises only Unicode space separators
 *       ({@code wabooks.commons.StringUtils.sanitize:118-124}) — no width, no charset.</li>
 * </ul>
 * Pure-ASCII overflow is impossible (100 characters ≤ 150 bytes), so the reachable failure is
 * multibyte — roughly 51 Hangul/CJK characters. Vietnamese is not exposed: {@code senderName} is
 * tone-stripped at {@code BulkReceptionService:563}/{@code :595} and the 134-entry table covers it.
 * Korean/CJK passes through at full width, and the reference tenant is a Korean parent company.
 *
 * <h2>Scope: Overseas only</h2>
 * {@code setSenderNm} has exactly two callers, both in {@link OverseasDispatchExecutor}
 * ({@code :271} D-mode, {@code :395} M-mode). The four domestic executors leave the column null, so
 * there is nothing to guard on those rails.
 *
 * <h2>Bank-code contract — read before calling</h2>
 * This guard takes the <b>{@code OUT_BANK_CD} (NAPAS) form</b> and applies the executor's own
 * {@code napasCodeForOutBank} mapping to reach {@code ACCT_MSTR}:
 * {@code 970424 → 11704088} (Shinhan), {@code 970457 → 11704020} (Woori). Callers holding the raw
 * internal {@code BANK_CD} must fold it through {@code OutBankCdNormalizer.normalize(...)} first — the
 * same call that stamps {@code OVRS_PYMN_SUMR.OUT_BANK_CD}. Keeping the mapping in one place is the
 * point: the guard must read the row the executor will read, or it guards nothing.
 *
 * <p>A bank outside that pair resolves {@code senderNm} to null in the executor, so it is not an error
 * here — there is no value to send. Likewise a missing account row or a blank {@code ACCT_NM}: the
 * executor sends null and the codec pads the slot with spaces, which is legacy-identical.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverseasSenderNameGuard {

    /** Legacy {@code OUT_BANK_CD} → internal {@code ACCT_MSTR.BANK_CD}, verbatim from
     *  {@link OverseasDispatchExecutor} {@code napasCodeForOutBank}. */
    private static final String OUT_BANK_CD_SHINHAN = "970424";
    private static final String OUT_BANK_CD_WOORI = "970457";
    private static final String ACCT_BANK_CD_SHINHAN = "11704088";
    private static final String ACCT_BANK_CD_WOORI = "11704020";

    private final AcctMstrRepository acctMstrRepository;
    private final PaymentWireWidthGuard wireWidthGuard;

    /**
     * Throw {@code WBE_PAYMENT_SENDER_NAME_UNSENDABLE} when the withdrawal account's
     * {@code ACCT_NM} cannot be carried by the 150-byte {@code senderName} slot.
     *
     * <p>A dedicated code rather than the generic {@code WBE_PAYMENT_WIRE_FIELD}: the user cannot fix
     * this by editing a cell. {@code ACCT_NM} is bank-supplied, so the actionable remedy is a different
     * withdrawal account (or a correction at the bank), and the message says so.
     *
     * @param outBankCd the {@code OUT_BANK_CD} / NAPAS form — see the class Javadoc
     */
    public void assertSendable(String bizKey, String outBankCd, String acctNo) {
        resolveAcctNm(bizKey, outBankCd, acctNo).ifPresent(acctNm ->
                wireWidthGuard.check(WireField.SENDER_NAME, acctNm).ifPresent(msg -> {
                    log.warn("feature=overseas-payment ACCT_NM cannot be sent as SENDER_NM — rejecting. "
                                    + "bizKey={} outBankCd={} acctNo={} reason={}",
                            bizKey, outBankCd, acctNo, msg);
                    throw new BusinessException("WBE_PAYMENT_SENDER_NAME_UNSENDABLE", acctNo, msg);
                }));
    }

    /**
     * Non-throwing variant for the registration paths: log a WARN and report whether the name is
     * unsendable, without blocking the write.
     *
     * <p>Registration must not hard-reject — {@code ACCT_NM} comes from the bank adapter and the user
     * cannot edit it, so refusing the registration would block a legitimate account over a field only
     * the Overseas rail consumes. The WARN exists so the problem is visible <i>before</i> someone
     * attempts a payment, rather than surfacing as a dispatch rejection weeks later.
     *
     * @return the reason the name is unsendable, or empty when it is fine
     */
    public Optional<String> warnIfUnsendable(String bankCd, String acctNo, String acctNm) {
        if (acctNm == null || acctNm.isBlank()) {
            return Optional.empty();
        }
        Optional<String> reason = wireWidthGuard.check(WireField.SENDER_NAME, acctNm);
        reason.ifPresent(msg -> log.warn(
                "feature=account-registration ACCT_NM cannot be carried by the firm-banking "
                        + "SENDER_NAME field (150 bytes). The account is still registered — DOMESTIC "
                        + "payments are unaffected — but an OVERSEAS payment from it will be rejected "
                        + "at creation/dispatch. bankCd={} acctNo={} reason={}",
                bankCd, acctNo, msg));
        return reason;
    }

    /** The {@code ACCT_NM} the dispatch executor will read, or empty when it would resolve null. */
    private Optional<String> resolveAcctNm(String bizKey, String outBankCd, String acctNo) {
        String acctBankCd = acctBankCdForOutBank(outBankCd);
        if (bizKey == null || acctBankCd == null || acctNo == null || acctNo.isBlank()) {
            return Optional.empty();
        }
        return acctMstrRepository
                .findByBizKeyAndBankCdAndAcctNo(bizKey, acctBankCd, acctNo.trim())
                .map(AcctMstr::getAcctNm)
                .filter(nm -> nm != null && !nm.isBlank());
    }

    /** Mirrors {@link OverseasDispatchExecutor} {@code napasCodeForOutBank} — keep the two in step. */
    private static String acctBankCdForOutBank(String outBankCd) {
        if (outBankCd == null) {
            return null;
        }
        String cd = outBankCd.trim();
        if (OUT_BANK_CD_SHINHAN.equals(cd)) return ACCT_BANK_CD_SHINHAN;
        if (OUT_BANK_CD_WOORI.equals(cd)) return ACCT_BANK_CD_WOORI;
        // Also accept the internal form, so a caller that has not normalised still resolves the same
        // row rather than silently skipping the check.
        if (ACCT_BANK_CD_SHINHAN.equals(cd) || ACCT_BANK_CD_WOORI.equals(cd)) return cd;
        return null;
    }
}
