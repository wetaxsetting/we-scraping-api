package com.kosign.wecms.config;

import com.kosign.wecms.common.lock.JobLockNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural guard for Mr. Kang's approved <b>four-legacy-batches → one scheduled replacement</b>
 * architecture.
 *
 * <p>The mapping this test protects:
 * <table border="1">
 *   <caption>Approved 4→1 mapping</caption>
 *   <tr><th>Legacy scheduled batch</th><th>Approved Spring replacement</th></tr>
 *   <tr><td>{@code NAPAS_REAL_TIME_BATCH}</td><td>after-commit real-time dispatch (not scheduled)</td></tr>
 *   <tr><td>{@code FILE_DEPOSIT_TRANSFER_BATCH1}</td><td>request-time reception → SFTP → {@code 00301} (not scheduled)</td></tr>
 *   <tr><td>{@code FILE_DEPOSIT_TRANSFER_BATCH2}</td><td>removed — successful {@code 01301} triggers inline {@code 00302}</td></tr>
 *   <tr><td>{@code PROCESSING_RESULT_CHECK_BATCH}</td><td><b>{@code ResultCheckJob}</b> — the only retained scheduled replacement</td></tr>
 * </table>
 *
 * <h2>Why a source-scanning test rather than a Spring context test</h2>
 * The thing being asserted is that a class <b>does not exist</b> and that a property name appears
 * <b>nowhere</b> in configuration. A context test can only observe beans that were defined; it cannot
 * prove the absence of a file or a YAML key, and it would pass just as happily with a
 * {@code @ConditionalOnProperty}-gated retry bean sitting dormant in the tree. Scanning the source and
 * resource trees is the only check that actually fails when the sweep comes back.
 *
 * <p>This test is deliberately cheap and dependency-free so it runs in the normal unit suite.
 */
class FourToOneBatchArchitectureTest {

    private static final Path MAIN_JAVA = Paths.get("src/main/java");
    private static final Path RESOURCES = Paths.get("src/main/resources");
    private static final Path TEST_JAVA = Paths.get("src/test/java");

    /**
     * Every {@code @Scheduled} class that is a replacement for one of the four legacy payment batches.
     * A monitoring-only job does not count — it must not mutate dispatch state or call the gateway,
     * which {@code StuckFirmFileMonitorTest} / {@code StuckPayFileMonitorServiceTest} pin separately.
     */
    private static final String PAYMENT_BATCH_REPLACEMENT = "ResultCheckJob";

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    // ── the removed sweep must not come back ────────────────────────────────

    @Test
    @DisplayName("no BulkExecutionRetry* class exists in main or test sources")
    void noBulkExecutionRetryClassExists() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : List.of(MAIN_JAVA, TEST_JAVA)) {
            for (Path f : javaFiles(root)) {
                String name = f.getFileName().toString();
                if (name.startsWith("BulkExecutionRetry")) {
                    offenders.add(f.toString());
                }
            }
        }
        assertThat(offenders)
                .as("BulkExecutionRetry* was K9 Option A (\"Recovery Scheduler\"), which Mr. Kang was "
                        + "offered and did not select. Re-adding it reverses the approved architecture — "
                        + "see docs/audits/final-four-to-one-batch-architecture-correction.md")
                .isEmpty();
    }

    @Test
    @DisplayName("no bulk-execution-retry property survives in any profile YAML")
    void noBulkExecutionRetryPropertyRemains() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> s = Files.walk(RESOURCES)) {
            for (Path f : s.filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml")).toList()) {
                String body = read(f);
                if (body.contains("bulk-execution-retry") || body.contains("BULK_EXECUTION_RETRY")) {
                    offenders.add(f.getFileName().toString());
                }
            }
        }
        assertThat(offenders).as("a retry property implies the sweep is back").isEmpty();
    }

    @Test
    @DisplayName("no ShedLock identity exists for a bulk-execution retry job")
    void noRetryLockNameRemains() {
        List<String> retryLocks = new ArrayList<>();
        for (Field f : JobLockNames.class.getDeclaredFields()) {
            String n = f.getName();
            if (n.contains("BULK_EXECUTION") || n.contains("RETRY")) {
                retryLocks.add(n);
            }
        }
        assertThat(retryLocks)
                .as("a lock identity is only needed by a scheduled body; one here means a retry job exists")
                .isEmpty();
    }

    // ── exactly one scheduled payment-batch replacement ─────────────────────

    @Test
    @DisplayName("ResultCheckJob is the ONLY scheduled replacement of the four legacy payment batches")
    void resultCheckJobIsTheOnlyPaymentBatchReplacement() throws IOException {
        // Scheduled classes under the dispatch package, excluding monitoring-only jobs and the
        // refactor-only stranded-recovery safety net (which has no legacy twin — it is not a
        // replacement for any of the four batches).
        List<String> monitoringOrNonReplacement =
                List.of("StuckPayFileMonitorJob", "StrandedRecoveryJob");

        List<String> scheduledDispatchJobs = new ArrayList<>();
        for (Path f : javaFiles(MAIN_JAVA)) {
            if (!f.toString().replace('\\', '/').contains("/payment/dispatch/")) {
                continue;
            }
            if (!read(f).contains("@Scheduled")) {
                continue;
            }
            String cls = f.getFileName().toString().replace(".java", "");
            if (!monitoringOrNonReplacement.contains(cls)) {
                scheduledDispatchJobs.add(cls);
            }
        }

        assertThat(scheduledDispatchJobs)
                .as("only ResultCheckJob may replace a legacy payment batch (PROCESSING_RESULT_CHECK_BATCH); "
                        + "the other three legacy batches are replaced by request-time / after-commit / "
                        + "callback processing, not by schedulers")
                .containsExactly(PAYMENT_BATCH_REPLACEMENT);
    }

    @Test
    @DisplayName("no scheduled class outside the callback path issues 00301 / 00302 / 202")
    void noSchedulerIssuesPaymentExecution() throws IOException {
        // A scheduled class must not reference the outbound execution legs. ResultCheckJob is allowed
        // to exist but delegates to the 203 inquiry only; it must not name the execution endpoints.
        List<String> offenders = new ArrayList<>();
        for (Path f : javaFiles(MAIN_JAVA)) {
            String body = read(f);
            if (!body.contains("@Scheduled")) {
                continue;
            }
            if (body.contains("EFS_301") || body.contains("EFS_302") || body.contains("EFS_202")) {
                offenders.add(f.getFileName().toString());
            }
        }
        assertThat(offenders)
                .as("00301 / 00302 / 202 are issued from request-time, after-commit and callback paths — "
                        + "never from a scheduler")
                .isEmpty();
    }

    // ── the monitor stays read-only ─────────────────────────────────────────

    @Test
    @DisplayName("the stuck-file monitor never references the 00302 endpoint")
    void monitorDoesNotReferenceExecutionEndpoint() throws IOException {
        Path monitor = MAIN_JAVA.resolve(
                "com/kosign/wecms/domain/payment/dispatch/monitor/StuckPayFileMonitorService.java");
        assertThat(Files.exists(monitor)).isTrue();

        String body = read(monitor);
        assertThat(body)
                .as("the monitor must observe, never execute")
                .doesNotContain("EFS_302")
                .doesNotContain("issueExecution");
    }

    @Test
    @DisplayName("the retained scheduled replacement really is the result-check job")
    void resultCheckJobExistsAndIsScheduled() throws IOException {
        Path job = MAIN_JAVA.resolve(
                "com/kosign/wecms/domain/payment/dispatch/scheduler/ResultCheckJob.java");
        assertThat(Files.exists(job)).isTrue();

        String body = read(job);
        assertThat(body).contains("@Scheduled");
        // Never-fire default is what keeps it inert until an environment owns the 202 traffic.
        assertThat(body).contains("wecms.dispatch.result-check.cron:-");
        assertThat(body).contains(JobLockNames.class.getSimpleName());
    }
}
