package com.kosign.wecms.domain.payment.common;

/**
 * The four free-text slots of the firm-banking fixed-length {@code D} record that carry
 * user-entered payment text, with their <b>wire</b> byte widths.
 *
 * <p><b>These are wire widths, not column widths — the distinction is the whole point of this
 * enum.</b> {@code TB_PAY_FILE_DATA} stores each of these values in a {@code varchar(150)} column
 * ({@code SENDER_NM} / {@code FINC_MDA_OWNER_NM} / {@code TAR_ACCT_RMRK} / {@code WDRL_ACCT_RMRK}),
 * but the record layout gives the two memo fields <b>210</b> bytes each
 * ({@code FixedLengthCodec.encodeData}, legacy {@code data.Deposit_Data:212-213}). So:
 * <ul>
 *   <li>for the two <b>name</b> slots the column (150 chars) and the wire (150 bytes) coincide, and
 *       pure-ASCII overflow is arithmetically impossible — the column gates first;</li>
 *   <li>for the two <b>memo</b> slots the wire is <i>wider</i> than the column, so ASCII overflow is
 *       even further out of reach.</li>
 * </ul>
 * The reachable overflow is therefore never "too many characters" — it is
 * <b>multibyte text surviving tone-stripping</b>, where 150 characters become up to 450 UTF-8 bytes.
 * Korean/CJK is the primary case (no entry in the Vietnamese tone table, so
 * {@link LegacyTextNormalizer#replaceTone} passes it through untouched); NFD-decomposed Vietnamese is
 * the secondary case (the tone table holds only precomposed code points, so a decomposed
 * {@code ế} keeps its two combining marks).
 *
 * <p>Validating against these widths at the entry layer is what lets the user fix the row. The
 * {@code FixedLengthCodec} throw stays as the last-resort backstop for anything that gets past here.
 *
 * @see PaymentWireWidthGuard
 * @see com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec
 */
public enum WireField {

    /**
     * {@code D} record offset 79, 150 bytes — originator name. Sourced server-side
     * ({@code ACCT_MSTR.ACCT_NM} / {@code OVRS_PYMN_SUMR.DBT_ACCT_HLDR}), never from an upload cell
     * or a request body, so no entry-layer surface exists for it today. Declared here so the width is
     * recorded in one place and a future user-editable originator name is guarded by construction.
     */
    SENDER_NAME("payment.wire_field.sender_name", 150),

    /** {@code D} record offset 229, 150 bytes — {@code FINC_MDA_OWNER_NM} (recipient / beneficiary name). */
    RECEIVER_NAME("payment.wire_field.receiver_name", 150),

    /** {@code D} record offset 379, 210 bytes — {@code TAR_ACCT_RMRK} (deposit-side remarks / memo). */
    DEPOSIT_ACCOUNT_MEMO("payment.wire_field.deposit_account_memo", 210),

    /** {@code D} record offset 589, 210 bytes — {@code WDRL_ACCT_RMRK} (withdrawal-side remarks / memo). */
    WITHDRAWAL_ACCOUNT_MEMO("payment.wire_field.withdrawal_account_memo", 210);

    private final String labelKey;
    private final int wireBytes;

    WireField(String labelKey, int wireBytes) {
        this.labelKey = labelKey;
        this.wireBytes = wireBytes;
    }

    /** i18n key for the user-facing field name used in the error message. */
    public String labelKey() {
        return labelKey;
    }

    /** Width of this field in the 998-byte {@code D} record, in bytes. */
    public int wireBytes() {
        return wireBytes;
    }
}
