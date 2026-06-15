package com.kosign.wecms.domain.payment.dispatch.file;

/**
 * Detail ('D') record of the firm-banking fixed-length file — refactor of legacy
 * {@code data.Deposit_Data}. One per beneficiary line.
 *
 * <p><b>Fields are raw strings in wire order</b> (see {@link DepositHeader} for the
 * rationale). In particular {@code transferAmount} is the verbatim on-wire string
 * (the UAT samples carry both {@code "…0014.00"} and integer {@code "…0000015"} for
 * the same 23-byte field) — it is NOT re-derived from a {@code BigDecimal}, so the
 * exact bytes survive a {@code decode → encode} round trip. {@link FixedLengthCodec}
 * owns padding, the constant identifier byte, and the {@code \r\n} terminator.
 *
 * <p><b>Vietnamese tone-stripping</b> ({@code StrUtil.replaceTone} on
 * {@code senderName}/{@code receiverName}/memos) is an <i>upstream</i> concern of the
 * bulk-reception orchestrator (legacy {@code FILE_DEPOSIT_TRANSFER_BATCH1}), NOT of
 * this codec — out of K3 scope. Callers pass already-normalised values.
 *
 * <p>Byte layout (998 content bytes + {@code \r\n} = 1000), verified against the UAT
 * samples on 2026-06-12 and matching legacy {@code Deposit_Data.getData()}. Note the
 * version-added fields: {@code receiptBankCountryCode}/{@code remittanceCurrencyCode}
 * (2025.07.07), {@code feeBrdnClsCd} (2025.10.29, written <b>before</b>
 * {@code rcptUseAddr}); the legacy {@code filler(183)} was removed 2025.10.29, so the
 * record ends at {@code rcptUseAddr}:
 * <pre>
 *   off  len  pad        field
 *     0    1  right ' '  identifier (constant 'D')           — owned by codec
 *     1    4  left  '0'  seq
 *     5   30  right ' '  depositAccountNumber
 *    35    3  right ' '  currencyCode
 *    38   23  left  '0'  transferAmount (verbatim wire string)
 *    61    1  right ' '  sameBankFlag
 *    62    1  right ' '  otherBankNetworkCode
 *    63    1  right ' '  cardAccountType
 *    64   15  right ' '  depositBankCode
 *    79  150  right ' '  senderName
 *   229  150  right ' '  receiverName
 *   379  210  right ' '  depositAccountMemo
 *   589  210  right ' '  withdrawalAccountMemo
 *   799    1  right ' '  processingResult   (result file only — 'N' = unprocessed)
 *   800   10  right ' '  responseCode       (result file only — e.g. 0999)
 *   810    2  right ' '  receiptBankCountryCode
 *   812    3  right ' '  remittanceCurrencyCode
 *   815    3  right ' '  feeBrdnClsCd
 *   818  180  right ' '  rcptUseAddr
 * </pre>
 */
public record DepositData(
        String seq,
        String depositAccountNumber,
        String currencyCode,
        String transferAmount,
        String sameBankFlag,
        String otherBankNetworkCode,
        String cardAccountType,
        String depositBankCode,
        String senderName,
        String receiverName,
        String depositAccountMemo,
        String withdrawalAccountMemo,
        String processingResult,
        String responseCode,
        String receiptBankCountryCode,
        String remittanceCurrencyCode,
        String feeBrdnClsCd,
        String rcptUseAddr
) {
}
