package com.kosign.wecms.domain.payment.internaltransfer.service;

/**
 * Context for a single dispatch cut-off-time evaluation. Carries the
 * extra signals legacy {@code PymnHelper.validateTransferTiming} reads
 * beyond {@code (bank, network)}:
 *
 * <ul>
 *   <li>{@code apiTransport} — whether the batch dispatches via the
 *       real-time API path ({@code TB_PAY_TARGET}) rather than the
 *       File-SFTP path ({@code TB_PAY_FILE_*}). Computed by the caller
 *       from its own routing rule (Internal Transfer reuses
 *       {@code InternalTransferDispatchExecutor.usesApiPath}; Vendor /
 *       Expense use {@code TRNF_CCY_CD == "VND"}; Payroll is always
 *       file; Overseas is SWIFT). <b>Retained for context/logging but no
 *       longer selects the cut-off window</b> — the Shinhan 16:30 window
 *       is keyed on {@code vndToVnd} (below), matching the currency-based
 *       legacy rule (transport-independent). Updated 2026-06-29.</li>
 *   <li>{@code vndToVnd} — transfer-currency AND withdrawal-currency are
 *       both VND. Drives the Shinhan CITAD <b>16:30</b> window (legacy
 *       {@code PymnHelper.java:3257-3263}, currency-based) and the Shinhan
 *       past-cut-off hard-block: Shinhan past cut-off is allowed (logged)
 *       EXCEPT {@code CITAD} VND→VND, which hard-blocks at 16:30 (req.
 *       2026-04-14). Every other Shinhan past-cut-off case logs and is allowed.</li>
 *   <li>{@code hasTxnAtOrAbove500M} — any line's {@code TRAN_AMT} is
 *       ≥ 500,000,000 VND. Bumps the VCB CITAD cut-off from 15:30 to
 *       16:30 (legacy {@code PymnHelper.java:3238-3255}, per-transaction
 *       tier).</li>
 * </ul>
 *
 * <p>The {@link CutoffValidator#validate(String, TransferNetwork)}
 * two-arg overload builds a context with all three flags {@code false}
 * (File-SFTP baseline) — used by the Overseas SWIFT path where none of
 * them apply.
 */
public record CutoffCheck(
        String outBankCd,
        TransferNetwork network,
        boolean apiTransport,
        boolean vndToVnd,
        boolean hasTxnAtOrAbove500M) {

    // NOTE: the VCB CITAD ≥500M tier threshold is now config-driven
    // (wecms.internal-transfer.cutoff.large-txn-threshold → CutoffConfigSnapshot)
    // and read via CutoffValidator.largeTxnThreshold(). The former hard-coded
    // LARGE_TXN_THRESHOLD constant was removed 2026-07-09 per the PO directive
    // that no cut-off value is hard-coded.
}
