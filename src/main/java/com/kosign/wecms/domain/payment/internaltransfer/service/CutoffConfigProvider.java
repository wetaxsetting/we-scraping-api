package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.payment.internaltransfer.entity.CutoffConfig;
import com.kosign.wecms.domain.payment.internaltransfer.entity.CutoffParam;
import com.kosign.wecms.domain.payment.internaltransfer.repository.CutoffConfigRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.CutoffParamRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single source of the cut-off configuration for the backend gate
 * ({@link CutoffValidator}) and the FE config API ({@link CutoffConfigService}).
 *
 * <h2>DB-only architecture (2026-07-09)</h2>
 *
 * <p>The configuration is loaded <b>entirely from the database</b>
 * ({@code TB_CUTOFF_CONFIG} + {@code TB_CUTOFF_PARAM}). There is <b>no
 * {@code application.yml} fallback</b> — the DB is the single source of truth.
 *
 * <h2>Fail-safe by fail-fast (never fail open)</h2>
 * <ul>
 *   <li><b>Startup ({@link #init()}):</b> loads + validates the DB config. If the
 *       table is empty, a <b>required</b> {@code (bank, network)} window is
 *       missing/disabled/malformed, or {@code large-txn-threshold} is
 *       missing/invalid, it throws → the {@code @PostConstruct} fails → the
 *       application <b>refuses to start</b> with a clear error. There is no
 *       silent bypass caused by missing configuration.</li>
 *   <li><b>Refresh ({@link #refresh()}, scheduled):</b> rebuilds + revalidates.
 *       On any failure it <b>keeps the last valid snapshot</b> and logs at
 *       {@code ERROR} — a bad live edit never degrades a running instance.</li>
 *   <li><b>Non-null guarantee:</b> once started, {@link #current()} returns a
 *       complete snapshot; every <i>required</i> window and the threshold are
 *       non-null, so {@link CutoffValidator} never sees a missing required
 *       window or a null threshold.</li>
 * </ul>
 *
 * <p>The <b>required</b> rails (below) are exactly the real configured windows.
 * A rail a bank does not offer (e.g. VCB SWIFT) is intentionally absent → its
 * {@code NetworkWindows} is {@code null} → {@link CutoffValidator} allows it
 * (legacy parity: "no config for this rail → allow"). That is a deliberate
 * "no such rail", NOT a configuration gap — required rails are enforced here.
 */
@Slf4j
@Component
public class CutoffConfigProvider {

    private static final String THRESHOLD_PARAM_KEY = "large-txn-threshold";
    private static final String ENABLED = "Y";

    /** The window rows that MUST exist (enabled + valid) for the app to start.
     *  Matches the approved seed exactly. A bank omits rails it does not offer
     *  (Woori/VCB: no CITAD_API; Shinhan/Woori: no CITAD_LARGE; VCB: no SWIFT). */
    private static final Map<String, List<String>> REQUIRED_RAILS = new LinkedHashMap<>();
    static {
        REQUIRED_RAILS.put("SHINHAN", List.of("NAPAS", "CITAD", "CITAD_API", "SWIFT"));
        REQUIRED_RAILS.put("WOORI", List.of("NAPAS", "CITAD", "SWIFT"));
        REQUIRED_RAILS.put("VCB", List.of("NAPAS", "CITAD", "CITAD_LARGE"));
    }

    private final CutoffConfigRepository configRepo;
    private final CutoffParamRepository paramRepo;

    /** The current snapshot. {@code null} only before {@link #init()} runs; a
     *  successfully-started context always has a non-null, complete snapshot. */
    private volatile CutoffConfigSnapshot snapshot;

    public CutoffConfigProvider(CutoffConfigRepository configRepo, CutoffParamRepository paramRepo) {
        this.configRepo = configRepo;
        this.paramRepo = paramRepo;
    }

    /**
     * Load + validate the DB config at startup. Throws (failing context
     * startup) if the configuration is missing/incomplete/invalid — fail-fast,
     * never fail-open. No fallback.
     */
    @PostConstruct
    void init() {
        this.snapshot = loadOrThrow();
        log.info("feature=internal-transfer cutoff-config loaded from DB (DB-only, no YAML fallback)");
    }

    /**
     * The current effective configuration (never {@code null} after startup).
     * Lock-free read of the latest immutable snapshot.
     */
    public CutoffConfigSnapshot current() {
        CutoffConfigSnapshot s = snapshot;
        if (s == null) {
            // Should be unreachable: init() runs at @PostConstruct and throws on
            // failure, so the bean never becomes usable with a null snapshot.
            throw new IllegalStateException("cut-off configuration not initialised");
        }
        return s;
    }

    /**
     * Rebuild + revalidate from the DB. On any failure the last valid snapshot
     * is retained and an ERROR is logged (never fail-open on a live edit).
     *
     * @return {@code true} if the snapshot was refreshed; {@code false} if the
     *         reload was invalid/failed and the last-good snapshot was kept.
     */
    public synchronized boolean refresh() {
        try {
            this.snapshot = loadOrThrow();
            log.debug("feature=internal-transfer cutoff-config refreshed from DB");
            return true;
        } catch (Exception ex) {
            log.error("feature=internal-transfer cutoff-config refresh REJECTED — keeping last valid snapshot. "
                    + "Fix TB_CUTOFF_CONFIG / TB_CUTOFF_PARAM: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * TTL backstop so a direct DBA SQL edit propagates without an app action.
     * Interval configurable; default 5 min. Uses the fail-safe {@link #refresh()}
     * (keeps last-good on invalid config).
     */
    @Scheduled(fixedDelayString = "${wecms.internal-transfer.cutoff.refresh-interval-ms:300000}",
            initialDelayString = "${wecms.internal-transfer.cutoff.refresh-interval-ms:300000}")
    void scheduledRefresh() {
        refresh();
    }

    /* ─────────────────── load + validate (DB-only) ─────────────────── */

    /** Load the DB config, validate completeness, and build the snapshot.
     *  Throws {@link IllegalStateException} with a clear, aggregated message
     *  when the configuration is empty/incomplete/invalid. */
    private CutoffConfigSnapshot loadOrThrow() {
        List<CutoffConfig> rows = configRepo.findAll();

        Map<String, CutoffConfig> enabled = new HashMap<>();
        for (CutoffConfig r : rows) {
            if (isEnabled(r.getEnableYn())) {
                enabled.put(key(r.getBankKey(), r.getNetwork()), r);
            }
        }

        List<String> problems = new ArrayList<>();
        if (rows.isEmpty()) {
            problems.add("TB_CUTOFF_CONFIG is empty");
        }

        // 1. Every required rail must have an enabled, valid row.
        REQUIRED_RAILS.forEach((bank, nets) -> {
            for (String net : nets) {
                CutoffConfig r = enabled.get(key(bank, net));
                if (r == null) {
                    problems.add("missing enabled row: " + bank + "/" + net);
                    continue;
                }
                validateTimes(bank, net, r, problems);
            }
        });

        // 2. Threshold must exist, be enabled, numeric and positive.
        BigDecimal threshold = resolveThresholdOrRecord(problems);

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Cut-off configuration is incomplete/invalid (DB-only — no application.yml fallback). "
                            + "The application will not start with an unsafe cut-off config. Problems: " + problems);
        }

        return new CutoffConfigSnapshot(
                buildBank("SHINHAN", enabled),
                buildBank("WOORI", enabled),
                buildBank("VCB", enabled),
                threshold);
    }

    private BigDecimal resolveThresholdOrRecord(List<String> problems) {
        Optional<CutoffParam> p = paramRepo.findById(THRESHOLD_PARAM_KEY)
                .filter(x -> isEnabled(x.getEnableYn()));
        if (p.isEmpty()) {
            problems.add("missing enabled TB_CUTOFF_PARAM row: " + THRESHOLD_PARAM_KEY);
            return null;
        }
        String raw = p.get().getParamValue();
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() <= 0) {
                problems.add(THRESHOLD_PARAM_KEY + " must be > 0 (was '" + raw + "')");
                return null;
            }
            return value;
        } catch (NumberFormatException | NullPointerException ex) {
            problems.add(THRESHOLD_PARAM_KEY + " is not a number (was '" + raw + "')");
            return null;
        }
    }

    private void validateTimes(String bank, String net, CutoffConfig r, List<String> problems) {
        LocalTime start = parseOrRecord(bank, net, "START_TIME", r.getStartTime(), problems);
        LocalTime end = parseOrRecord(bank, net, "END_TIME", r.getEndTime(), problems);
        if (start != null && end != null && !start.isBefore(end)) {
            problems.add(bank + "/" + net + ": START_TIME (" + r.getStartTime()
                    + ") must be before END_TIME (" + r.getEndTime() + ")");
        }
        // warning is optional; validate only if present.
        if (r.getWarningTime() != null && !r.getWarningTime().isBlank()) {
            parseOrRecord(bank, net, "WARNING_TIME", r.getWarningTime(), problems);
        }
    }

    private LocalTime parseOrRecord(String bank, String net, String col, String hhmm, List<String> problems) {
        if (hhmm == null || hhmm.isBlank()) {
            problems.add(bank + "/" + net + ": " + col + " is blank");
            return null;
        }
        try {
            return LocalTime.parse(hhmm.trim());
        } catch (DateTimeParseException ex) {
            problems.add(bank + "/" + net + ": " + col + " is not HH:mm (was '" + hhmm + "')");
            return null;
        }
    }

    /** Build a bank's windows from the enabled DB rows. Required rails are
     *  guaranteed present (validated above); non-offered rails stay null. */
    private CutoffConfigSnapshot.BankWindows buildBank(String bank, Map<String, CutoffConfig> enabled) {
        return new CutoffConfigSnapshot.BankWindows(
                window(bank, "NAPAS", enabled),
                window(bank, "CITAD", enabled),
                window(bank, "CITAD_API", enabled),
                window(bank, "SWIFT", enabled),
                window(bank, "CITAD_LARGE", enabled));
    }

    private CutoffConfigSnapshot.NetworkWindows window(String bank, String net, Map<String, CutoffConfig> enabled) {
        CutoffConfig r = enabled.get(key(bank, net));
        if (r == null) {
            return null; // rail not offered by this bank (intentional)
        }
        return new CutoffConfigSnapshot.NetworkWindows(
                LocalTime.parse(r.getStartTime().trim()),
                LocalTime.parse(r.getEndTime().trim()),
                parseNullable(r.getWarningTime()));
    }

    private static LocalTime parseNullable(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) {
            return null;
        }
        return LocalTime.parse(hhmm.trim());
    }

    private static boolean isEnabled(String enableYn) {
        return enableYn != null && ENABLED.equalsIgnoreCase(enableYn.trim());
    }

    private static String key(String bankKey, String network) {
        return (bankKey == null ? "" : bankKey.trim().toUpperCase())
                + "|" + (network == null ? "" : network.trim().toUpperCase());
    }
}
