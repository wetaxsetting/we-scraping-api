package com.kosign.wecms.common.lock;

/**
 * Canonical ShedLock lock-name constants for every locked job body in the
 * application.
 *
 * <p><b>Why these are constants and not inline strings.</b> A lock name is a
 * <em>lock identity</em>, not a label. Two code paths that execute the same
 * business body MUST present the same identity or ShedLock will happily let
 * them run concurrently — that was scheduler-legacy-parity-audit finding F-9 /
 * scheduler-audit finding F-8, where {@code StrandedRecoveryService} delegated
 * into the result-check body while holding {@code StrandedRecoveryJob.run}
 * instead of the result-check lock. Centralising the names here makes the
 * shared-identity requirement visible at every call site and lets
 * {@code @SchedulerLock(name = …)} (which needs a compile-time constant) and
 * {@link JobLockGuard} (runtime) reference one source of truth.
 *
 * <p><b>Names are wire-compatible with the existing {@code shedlock} table.</b>
 * The values below deliberately reproduce the lock names already present in
 * deployed {@code shedlock} rows. Renaming one would silently create a second
 * row and drop mutual exclusion against a node still running the old build, so
 * do not "tidy" these strings.
 *
 * @see JobLockGuard
 */
public final class JobLockNames {

    private JobLockNames() {
    }

    // ── Exchange-rate collection (legacy EXCH_0001/0010/0020_001) ─────────────
    // Shared by ExchangeRateBatchScheduler (scheduled) and
    // ExchangeRateBatchService.runOnDemandGuarded() (user-triggered catch-up).

    /** Legacy {@code EXCH_0001_001} — Shinhan first-time. */
    public static final String EXCH_SHINHAN_FIRST_TIME = "exch-shinhan-first-time";
    /** Legacy {@code EXCH_0010_001} — Shinhan last-time. */
    public static final String EXCH_SHINHAN_LAST_TIME = "exch-shinhan-last-time";
    /** Legacy {@code EXCH_0020_001} — Woori. */
    public static final String EXCH_WOORI = "exch-woori";

    // ── Operation-status inquiry (legacy OPRT_0001_001 + OPRT_0010_001) ───────

    /**
     * Single lock covering the seed→refresh pair. The two legacy programs are
     * driven by ONE refactor method under ONE lock so their order is
     * deterministic — see {@code OperationStatusBatchScheduler}.
     */
    public static final String OPRT_STATUS_BATCH = "oprt-status-batch";

    // ── Firm-banking dispatch (legacy WECMS_BATCH DEV) ───────────────────────

    /**
     * The result-check processing body (legacy {@code PROCESSING_RESULT_CHECK_BATCH}).
     * Held by {@code ResultCheckJob.run} AND acquired programmatically by
     * {@code StrandedRecoveryService} before it delegates into the same body.
     * Value preserved from the pre-existing {@code @SchedulerLock} name.
     */
    public static final String DISPATCH_RESULT_CHECK = "ResultCheckJob.run";

    /** R-1 stranded-record recovery sweep. */
    public static final String DISPATCH_STRANDED_RECOVERY = "StrandedRecoveryJob.run";

    /** Read-only stuck-pay-file monitor. */
    public static final String DISPATCH_STUCK_PAY_FILE_MONITOR = "StuckPayFileMonitorJob.run";

    // A `DISPATCH_BULK_EXECUTION_RETRY` lock existed here briefly (2026-07-29) for a scheduled
    // 00302 retry sweep. Both the lock and the sweep were REMOVED the same day: the sweep was
    // K9 Option A ("Recovery Scheduler (reconciliation)"), which Mr. Kang was offered and did not
    // select — the approved TO-BE replaces legacy BATCH2 with inline 00302 from the 01301 callback
    // and leaves the residual edge to monitoring + manual recovery. Do not re-add a lock name for a
    // bulk-execution retry job; adding one would mean the sweep is back.
    // See docs/audits/final-four-to-one-batch-architecture-correction.md.

    // ── HAENGSUNG Oracle sync (legacy ERP_0100_001/002/003) ───────────────────

    /**
     * Legacy {@code ERP_0100_002} — un-received Oracle payment import. Shared by
     * the scheduled job and the interactive Refresh endpoint, which execute the
     * same {@code ErpPaymentRefreshService} body.
     */
    public static final String HAENGSUNG_PAYMENT_IMPORT = "HaengsungScheduledPaymentImportJob.run";

    /** Legacy {@code ERP_0100_001} — exchange-rate push to ERP. */
    public static final String HAENGSUNG_EXCHANGE_RATE_SYNC = "HaengsungExchangeRateSyncJob.run";

    /** Legacy {@code ERP_0100_003} — account-history + card-approval push to ERP. */
    public static final String HAENGSUNG_CARD_ACCOUNT_SYNC = "HaengsungCardAccountSyncJob.run";

    // ── Zalo OA (refactor-native — no legacy JEX counterpart) ─────────────────

    /**
     * Keep-alive sweep for Zalo's rotating OAuth refresh token
     * ({@code ZaloTokenRefreshScheduler}).
     *
     * <p>The lock is not a nicety here. Zalo issues a new refresh token on every
     * refresh and invalidates the previous one, so two instances sweeping the same
     * connection produce two rotations where the second retires the first — leaving
     * a stored token Zalo no longer accepts and a connection only a human can
     * repair by redoing the OAuth consent flow.
     */
    public static final String ZALO_TOKEN_REFRESH = "ZaloTokenRefreshJob.run";
}
