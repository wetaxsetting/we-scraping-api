package com.kosign.wecms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.support.CronExpression;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Deployment-safety contract for every scheduler-related property, asserted against the real
 * {@code application*.yml} files <b>and</b> the matching {@code .env.*.example} templates.
 *
 * <h2>History — F-6 is still closed, by a different mechanism (2026-07-30)</h2>
 * This class was {@code SchedulerConfigurationDefaultsTest}, and it asserted the opposite of what it
 * asserts now. Its original contract came from scheduler-legacy-parity-audit finding <b>F-6</b>
 * ("missing env var = startup failure"): every scheduler flag and cron carried a safe <i>inline</i>
 * default ({@code ${VAR:false}} / {@code ${VAR:-}}), so a host that had exported none of the optional
 * scheduler variables still booted with everything switched off. The test loaded each profile YAML
 * with no environment contributed and asserted that every such property resolved, and resolved to the
 * inert value.
 *
 * <p><b>What changed.</b> The 2026-07-30 bare-placeholder migration removed every inline default from
 * all five config files (169 placeholders). Values live in {@code .env} only; the YAML declares shape.
 *
 * <p><b>Why F-6's cost argument no longer applies.</b> F-6 objected that inline defaults were needed
 * because otherwise "a deploy depends on ~12 variables being present just to start with everything
 * switched off". After the migration a SIT deploy depends on <b>72</b> variables being present, so the
 * twelve scheduler ones add nothing to that burden — the burden is now the whole configuration, and it
 * is met by copying the profile's {@code .env.*.example}. The original cost/benefit is void.
 *
 * <p><b>Why F-6's safety intent is preserved, and strengthened.</b> F-6 protected one guarantee: a
 * forgotten variable must never silently enable a money-moving job. Under inline defaults that was
 * enforced by the default being inert — but the same mechanism made a <i>misspelled</i> variable name
 * indistinguishable from "deliberately not configured": the app booted on the hidden value and ops
 * believed the {@code .env} was in force. Under bare placeholders the app refuses to start until
 * somebody states the value. The guarantee holds; enforcement moves from the YAML to a complete
 * {@code .env}, and this class is what makes that enforceable in CI.
 *
 * <h2>The contract now asserted, per profile</h2>
 * <ol>
 *   <li><b>Bare in YAML</b> — every scheduler flag/cron is {@code ${VAR}} with no {@code :default}.
 *       This is the inverse of the old assertion, and it is what fails the build if someone
 *       reintroduces an inline default on a scheduler property.</li>
 *   <li><b>Present in the example</b> — the variable has a line in the matching
 *       {@code .env.*.example}. The old test could not check this side at all: it verified the YAML
 *       was safe when the {@code .env} was <i>absent</i>, a state that is no longer reachable.</li>
 *   <li><b>Inert where it matters</b> — the committed value for a mutating scheduler is the inert one
 *       ({@code false} for a flag, {@code -} for a cron), so "no scheduler may be enabled by committed
 *       configuration" is now enforced on the {@code .env} side.</li>
 *   <li><b>Valid where it fires</b> — any cron committed as something other than {@code -} parses as a
 *       Spring cron expression.</li>
 *   <li><b>Completeness</b> — bare in YAML <i>and</i> present in the example. Neither alone passes:
 *       a bare placeholder with no example line is a variable nobody knows to set, and an example line
 *       with no YAML placeholder is a value nothing reads.</li>
 * </ol>
 *
 * <p>Still a configuration test, not a context test: no database, no ShedLock table, no secrets, so it
 * runs on every commit.
 */
class SchedulerConfigurationContractTest {

    private static final YamlPropertySourceLoader LOADER = new YamlPropertySourceLoader();

    /** A bare placeholder: {@code ${VAR}}, optionally quoted, with no {@code :default} part. */
    private static final Pattern BARE_PLACEHOLDER = Pattern.compile("^\"?\\$\\{([A-Z][A-Z0-9_]*)}\"?$");

    /** Every mutating scheduler's enable flag. Committed value must be {@code false}. */
    private static final List<String> MUTATING_ENABLE_FLAGS = List.of(
            "wecms.exchange-rate.batch.enabled",
            "wecms.operation-status.batch.enabled",
            "haengsung.oracle.enabled",
            "haengsung.scheduled-payment-import.enabled",
            "haengsung.exchange-rate-sync.enabled",
            "haengsung.card-account-sync.enabled");

    /** Mutating-job crons. Committed value must be the never-fire marker. */
    private static final List<String> MUTATING_CRONS = List.of(
            "wecms.operation-status.batch.cron",
            "wecms.dispatch.result-check.cron",
            "wecms.dispatch.recovery.cron");

    /**
     * Bound on every startup by plain {@code @ConfigurationProperties} beans regardless of their
     * feature flag, so they must be declared and supplied like anything else.
     */
    private static final List<String> UNCONDITIONALLY_BOUND = List.of(
            "wecms.dispatch.efs.base-url",
            "wecms.dispatch.sftp.host",
            "wecms.dispatch.sftp.port",
            "wecms.dispatch.sftp.username");

    /** The read-only stuck-pay-file monitor — the documented exception to rule 3. See below. */
    private static final String STUCK_ENABLED = "wecms.dispatch.stuck-monitor.enabled";
    private static final String STUCK_CRON = "wecms.dispatch.stuck-monitor.cron";

    // ------------------------------------------------------------------ helpers

    /** Raw, UNRESOLVED yaml values for a profile: profile file wins over the base, mirroring Boot. */
    private static Map<String, String> rawYaml(String profile) throws IOException {
        Map<String, String> merged = new HashMap<>();
        for (String file : List.of("application.yml", "application-" + profile + ".yml")) {
            ClassPathResource resource = new ClassPathResource(file);
            assertThat(resource.exists()).as("%s must exist", file).isTrue();
            for (PropertySource<?> ps : LOADER.load(file, resource)) {
                if (ps instanceof org.springframework.core.env.EnumerablePropertySource<?> eps) {
                    for (String name : eps.getPropertyNames()) {
                        Object v = eps.getProperty(name);
                        if (v != null) {
                            merged.put(name, String.valueOf(v));
                        }
                    }
                }
            }
        }
        return merged;
    }

    /** Key/value pairs committed in a profile's {@code .env.*.example}. */
    private static Map<String, String> envExample(String profile) throws IOException {
        Path p = Path.of(".env." + profile + ".example");
        assertThat(Files.exists(p)).as("%s must exist — it is the deploy checklist", p).isTrue();
        Map<String, String> out = new HashMap<>();
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            String s = line.strip();
            if (s.isEmpty() || s.startsWith("#") || !s.contains("=")) {
                continue;
            }
            int eq = s.indexOf('=');
            out.put(s.substring(0, eq).strip(), s.substring(eq + 1));
        }
        return out;
    }

    /**
     * Rules 1, 2 and 5 for one property: bare in yaml, and its variable present in the example.
     * Returns the committed value so the caller can assert rules 3 and 4 on it.
     */
    private static String assertBareAndSupplied(String profile, String key,
                                                Map<String, String> yaml,
                                                Map<String, String> example) {
        String raw = yaml.get(key);
        assertThat(raw)
                .as("%s must be declared in profile %s", key, profile)
                .isNotNull();

        Matcher m = BARE_PLACEHOLDER.matcher(raw.strip());
        assertThat(m.matches())
                .as("%s in profile %s must be a BARE ${VAR} with no inline default, but is: %s%n"
                        + "Inline defaults were removed on 2026-07-30 — a default here is a value "
                        + "hidden where nobody looks, and makes a misspelled variable name "
                        + "indistinguishable from 'not configured'. Put the value in "
                        + ".env.%s.example instead.", key, profile, raw, profile)
                .isTrue();

        String var = m.group(1);
        assertThat(example)
                .as("%s resolves ${%s}, so .env.%s.example must carry a %s= line. A bare placeholder "
                        + "with no example entry is a variable nobody knows to set, and startup fails "
                        + "on it.", key, var, profile, var)
                .containsKey(var);
        return example.get(var);
    }

    // ------------------------------------------------------------------ the contract

    @DisplayName("mutating scheduler flags: bare in yaml, supplied by the example, committed false")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void mutatingEnableFlagsAreBareAndCommittedInert(String profile) throws IOException {
        Map<String, String> yaml = rawYaml(profile);
        Map<String, String> example = envExample(profile);

        for (String key : MUTATING_ENABLE_FLAGS) {
            String committed = assertBareAndSupplied(profile, key, yaml, example);
            assertThat(committed)
                    .as("%s is committed as '%s' in .env.%s.example — no mutating scheduler may be "
                            + "enabled by committed configuration", key, committed, profile)
                    .isEqualTo("false");
        }
    }

    @DisplayName("mutating-job crons: bare in yaml, supplied by the example, committed never-fire")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void mutatingCronsAreBareAndCommittedNeverFire(String profile) throws IOException {
        Map<String, String> yaml = rawYaml(profile);
        Map<String, String> example = envExample(profile);

        for (String key : MUTATING_CRONS) {
            if (!yaml.containsKey(key)) {
                continue; // not every profile declares every cron; uat/prod inherit some from base
            }
            String committed = assertBareAndSupplied(profile, key, yaml, example);
            assertThat(committed)
                    .as("%s is committed as '%s' in .env.%s.example — must be \"-\" (Spring's "
                            + "never-fire marker). An empty value throws at scheduler init.",
                            key, committed, profile)
                    .isEqualTo("-");
        }
    }

    /**
     * The stuck-pay-file monitor is the one scheduler whose committed value may be ON.
     *
     * <p>It is exempt from the "committed inert" rule because it is <b>read-only</b>:
     * {@code StuckPayFileMonitorService} is {@code @Transactional(readOnly = true)}, performs no
     * writes, does not touch {@code PROC_STAT}, and never re-drives dispatch. The rule exists to stop
     * committed configuration from mutating payment state, which this sweep cannot do. It is the only
     * detection for {@code BULK_EXECUTION_UNCONFIRMED} and {@code BULK_EXECUTION_NOT_ISSUED} — two
     * states with no automatic retry that resolve solely by operator action
     * ({@code docs/deployment/bulk-file-reconciliation-procedure.md}).
     *
     * <p>What is asserted instead is that <b>both gates agree</b>. An enabled flag with a never-fire
     * cron is a silent no-op — monitoring that looks configured and reports nothing — and that is the
     * failure mode most likely to go unnoticed. Rules 1, 2, 4 and 5 apply unchanged.
     *
     * <p>The committed values currently differ across profiles ({@code sit} ships it ON; {@code local},
     * {@code uat} and {@code prod} ship it OFF), which is why this asserts agreement rather than a
     * per-profile literal. Whether UAT should run it is an open operational question, not a contract
     * violation — see {@code docs/deployment/config-bare-placeholder-migration.md} §5.
     */
    @DisplayName("stuck-pay-file monitor: bare, supplied, and both gates agree")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void stuckMonitorGatesAgree(String profile) throws IOException {
        Map<String, String> yaml = rawYaml(profile);
        Map<String, String> example = envExample(profile);

        String enabled = assertBareAndSupplied(profile, STUCK_ENABLED, yaml, example);
        String cron = assertBareAndSupplied(profile, STUCK_CRON, yaml, example);

        assertThat(enabled)
                .as("%s must be committed as true or false in .env.%s.example", STUCK_ENABLED, profile)
                .isIn("true", "false");

        if ("true".equals(enabled)) {
            assertThat(cron)
                    .as("stuck-monitor is ON in %s but its cron is the never-fire marker — that is a "
                            + "silent no-op: monitoring that looks configured and reports nothing",
                            profile)
                    .isNotEqualTo("-");
        } else {
            assertThat(cron)
                    .as("stuck-monitor is OFF in %s, so its cron must be \"-\" — a real cron with the "
                            + "flag off is misleading configuration", profile)
                    .isEqualTo("-");
        }
    }

    @DisplayName("every committed cron that is not \"-\" is a valid Spring cron expression")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void committedCronsParse(String profile) throws IOException {
        Map<String, String> yaml = rawYaml(profile);
        Map<String, String> example = envExample(profile);

        List<String> cronKeys = new ArrayList<>(MUTATING_CRONS);
        cronKeys.add(STUCK_CRON);
        cronKeys.add("haengsung.scheduled-payment-import.cron");
        cronKeys.add("haengsung.exchange-rate-sync.cron");
        cronKeys.add("haengsung.card-account-sync.cron");

        for (String key : cronKeys) {
            String raw = yaml.get(key);
            if (raw == null) {
                continue;
            }
            Matcher m = BARE_PLACEHOLDER.matcher(raw.strip());
            assertThat(m.matches()).as("%s must be bare in profile %s", key, profile).isTrue();
            String committed = example.get(m.group(1));
            if (committed == null || "-".equals(committed) || committed.isBlank()) {
                continue; // "-" is never-fire; blank is caught by the inert-value tests above
            }
            assertThatCode(() -> CronExpression.parse(committed))
                    .as("%s is committed as '%s' in .env.%s.example, which is not a valid Spring cron "
                            + "expression — this throws at scheduler initialisation and the "
                            + "application fails to start", key, committed, profile)
                    .doesNotThrowAnyException();
        }
    }

    @DisplayName("unconditionally-bound dispatch endpoints: bare in yaml, supplied by the example")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void unconditionallyBoundDispatchPropertiesAreSupplied(String profile) throws IOException {
        Map<String, String> yaml = rawYaml(profile);
        Map<String, String> example = envExample(profile);

        // EfsProperties and SftpProperties are plain @Component + @ConfigurationProperties — they bind
        // on every startup even when their feature flags are false. Note the binding is LENIENT: an
        // unresolvable placeholder on a String field binds the literal "${VAR}" with no error at all,
        // and only a typed field (sftp.port is an int) fails loudly. That asymmetry is exactly why the
        // example must carry every one of these.
        for (String key : UNCONDITIONALLY_BOUND) {
            assertBareAndSupplied(profile, key, yaml, example);
        }
    }

    @DisplayName("the retired seed-cron / refresh-cron keys are gone")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void retiredOperationStatusCronKeysAreAbsent(String profile) throws IOException {
        Map<String, String> yaml = rawYaml(profile);

        // Seed and refresh now share one cron; leaving the old keys around would look like they still
        // control something.
        assertThat(yaml).doesNotContainKey("wecms.operation-status.batch.seed-cron");
        assertThat(yaml).doesNotContainKey("wecms.operation-status.batch.refresh-cron");
    }

    /**
     * D-2 (legacy-verified 2026-07-29). The EFS envelope {@code ID} is always
     * {@code TB_FIRM_CONF.VAN_ORG_CD} on every leg (201/202/203/204/301/302/MSG) — legacy has no
     * partner-id property, so a configured one could only send a value legacy never sends. The
     * property was removed from {@code EfsProperties} and from all four profile YAMLs; this test fails
     * if anyone reintroduces it in configuration.
     */
    @DisplayName("the retired EFS partner-id key is gone from every profile")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void partnerIdPropertyIsRetired(String profile) throws IOException {
        assertThat(rawYaml(profile)).doesNotContainKey("wecms.dispatch.efs.partner-id");
    }
}
