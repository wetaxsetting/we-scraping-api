package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only projection of the bank cut-off configuration
 * ({@code wecms.internal-transfer.cutoff}) for the frontend. Served by
 * {@code GET /api/v1/payment/cutoff-config} so the Next.js pre-flight check
 * ({@code lib/payment/validateBankingTime.ts}) consumes the same windows +
 * ≥500M threshold the backend {@code CutoffValidator} enforces, instead of a
 * duplicated hard-coded table (2026-07-09, PO "no hard-coding" directive).
 *
 * <p>The backend {@code CutoffValidator} stays the authoritative gate — this
 * payload is a UX hint. Window times are Vietnam-local {@code "HH:mm"}.
 *
 * <p><b>Server time (2026-07-10).</b> {@code serverDatetime} + {@code timezone}
 * carry the authoritative Vietnam server clock so the frontend evaluates the
 * cut-off against server time — not the browser clock — regardless of the
 * user's country / OS timezone / a mis-set client clock (legacy parity with
 * the {@code comm_1006_001} server-time fetch). The frontend derives a
 * server↔client offset from {@code serverDatetime} and advances it by the
 * locally-elapsed time.
 */
public record CutoffConfigResponse(
        /** Authoritative current Vietnam server datetime, ISO-8601 with the
         *  {@code +07:00} offset (e.g. {@code "2026-07-10T15:25:30+07:00"}). */
        String serverDatetime,
        /** {@code "Asia/Ho_Chi_Minh"} — the zone {@code serverDatetime} is in. */
        String timezone,
        /** VCB CITAD ≥500M tier threshold (VND). */
        BigDecimal largeTxnThreshold,
        List<BankCutoff> banks) {

    /** One bank's window set. {@code bankKey} is the brand the FE keys on
     *  ({@code SHINHAN} / {@code WOORI} / {@code VCB}). */
    public record BankCutoff(
            String bankKey,
            List<NetworkCutoff> networks) { }

    /** One {@code (network, start, end, warning)} window.
     *  {@code network} ∈ {@code NAPAS | CITAD | CITAD_API | SWIFT | CITAD_LARGE}.
     *  {@code warning} is the FE soft-confirm zone start; may be {@code null}. */
    public record NetworkCutoff(
            String network,
            String start,
            String end,
            String warning) { }
}
