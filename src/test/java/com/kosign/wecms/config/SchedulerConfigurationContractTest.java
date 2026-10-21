package com.kosign.wecms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

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
 * <p><b>Why F-6's safety intent is preserved.</b> F-6 protected one guarantee: a forgotten variable
 * must never silently enable a money-moving job. Under bare placeholders the app refuses to start until
 * somebody states the value, so a forgotten variable is a failed start, never a hidden default.
 *
 * <h2>Keys-only templates (2026-09-29)</h2>
 * The {@code .env*.example} files carry <b>keys only, every value blank</b> — no environment data
 * (hosts, URLs, paths, flags) is committed. The earlier rules that checked committed <i>values</i>
 * (mutating flags committed {@code false}, mutating crons committed {@code -}, committed crons parse,
 * stuck-monitor gates agree) were retired with them: there is no committed value left to check. Those
 * values are now an operational responsibility of each host's real {@code .env}, and the per-key
 * comments in the templates say which value is safe.
 *
 * <h2>The contract now asserted, per profile</h2>
 * <ol>
 *   <li><b>Bare in YAML</b> — every scheduler flag/cron is {@code ${VAR}} with no {@code :default}.
 *       This fails the build if someone reintroduces an inline default on a scheduler property.</li>
 *   <li><b>Present in the example</b> — the variable has a line in the matching
 *       {@code .env.*.example}.</li>
 *   <li><b>Completeness</b> — the example's key set equals the set of variables the profile's YAML
 *       reads, exactly. A bare placeholder with no example line is a variable nobody knows to set, and
 *       an example line with no YAML placeholder is a value nothing reads.</li>
 *   <li><b>Keys only</b> — every value in every {@code .env*.example} is blank.</li>
 * </ol>
 *
 * <p>Still a configuration test, not a context test: no database, no ShedLock table, no secrets, so it
 * runs on every commit.
 */
class SchedulerConfigurationContractTest {

    private static final YamlPropertySourceLoader LOADER = new YamlPropertySourceLoader();

    /** A bare placeholder: {@code ${VAR}}, optionally quoted, with no {@code :default} part. */
    private static final Pattern BARE_PLACEHOLDER = Pattern.compile("^\"?\\$\\{([A-Z][A-Z0-9_]*)}\"?$");

    /** Any placeholder variable name inside a yaml value. */
    private static final Pattern ANY_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)");

    /** Every mutating scheduler's enable flag. */
    private static final List<String> MUTATING_ENABLE_FLAGS = List.of(
            "wecms.exchange-rate.batch.enabled",
            "wecms.operation-status.batch.enabled",
            "haengsung.oracle.enabled",
            "haengsung.scheduled-payment-import.enabled",
            "haengsung.exchange-rate-sync.enabled",
            "haengsung.card-account-sync.enabled");

    /** Every scheduler cron. */
    private static final List<String> SCHEDULER_CRONS = List.of(
            "wecms.operation-status.batch.cron",
            "wecms.dispatch.result-check.cron",
            "wecms.dispatch.recovery.cron",
            "wecms.dispatch.stuck-monitor.cron",
            "haengsung.scheduled-payment-import.cron",
            "haengsung.exchange-rate-sync.cron",
            "haengsung.card-account-sync.cron");

    /**
     * Bound on every startup by plain {@code @ConfigurationProperties} beans regardless of their
     * feature flag, so they must be declared and supplied like anything else.
     */
    private static final List<String> UNCONDITIONALLY_BOUND = List.of(
            "wecms.dispatch.efs.base-url",
            "wecms.dispatch.sftp.host",
            "wecms.dispatch.sftp.port",
            "wecms.dispatch.sftp.username");

    /** The read-only stuck-pay-file monitor's enable flag. */
    private static final String STUCK_ENABLED = "wecms.dispatch.stuck-monitor.enabled";

    // ------------------------------------------------------------------ helpers

    /** Raw, UNRESOLVED yaml values for a profile: profile file wins over the base, mirroring Boot. */
    private static Map<String, String> rawYaml(String profile) throws IOException {
        Map<String, String> merged = new HashMap<>(yamlFile("application.yml"));
        merged.putAll(yamlFile("application-" + profile + ".yml"));
        return merged;
    }

    /** Raw, UNRESOLVED yaml values of one file. */
    private static Map<String, String> yamlFile(String file) throws IOException {
        Map<String, String> out = new HashMap<>();
        ClassPathResource resource = new ClassPathResource(file);
        assertThat(resource.exists()).as("%s must exist", file).isTrue();
        for (PropertySource<?> ps : LOADER.load(file, resource)) {
            if (ps instanceof org.springframework.core.env.EnumerablePropertySource<?> eps) {
                for (String name : eps.getPropertyNames()) {
                    Object v = eps.getProperty(name);
                    if (v != null) {
                        out.put(name, String.valueOf(v));
                    }
                }
            }
        }
        return out;
    }

    /** Every placeholder variable named in a set of yaml values. */
    private static Set<String> placeholders(Iterable<String> values) {
        Set<String> read = new TreeSet<>();
        for (String raw : values) {
            Matcher m = ANY_PLACEHOLDER.matcher(raw);
            while (m.find()) {
                read.add(m.group(1));
            }
        }
        return read;
    }

    /** Key/value pairs committed in a template file. */
    private static Map<String, String> envFile(Path p) throws IOException {
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

    /** Key/value pairs committed in a profile's {@code .env.*.example}. */
    private static Map<String, String> envExample(String profile) throws IOException {
        return envFile(Path.of(".env." + profile + ".example"));
    }

    /** Rules 1 and 2 for one property: bare in yaml, and its variable present in the example. */
    private static void assertBareAndSupplied(String profile, String key,
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
                        + "indistinguishable from 'not configured'. Set the value in the host .env.",
                        key, profile, raw)
                .isTrue();

        String var = m.group(1);
        assertThat(example)
                .as("%s resolves ${%s}, so .env.%s.example must carry a %s= line. A bare placeholder "
                        + "with no example entry is a variable nobody knows to set, and startup fails "
                        + "on it.", key, var, profile, var)
                .containsKey(var);
    }

    // ------------------------------------------------------------------ the contract

    @DisplayName("scheduler flags and crons: bare in yaml, key present in the example")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void schedulerPropertiesAreBareAndListed(String profile) throws IOException {
        Map<String, String> yaml = rawYaml(profile);
        Map<String, String> example = envExample(profile);

        List<String> keys = new ArrayList<>(MUTATING_ENABLE_FLAGS);
        keys.add(STUCK_ENABLED);
        for (String key : SCHEDULER_CRONS) {
            if (yaml.containsKey(key)) {
                keys.add(key); // not every profile declares every cron; uat/prod inherit some from base
            }
        }
        for (String key : keys) {
            assertBareAndSupplied(profile, key, yaml, example);
        }
    }

    @DisplayName("unconditionally-bound dispatch endpoints: bare in yaml, key present in the example")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void unconditionallyBoundDispatchPropertiesAreSupplied(String profile) throws IOException {
        Map<String, String> yaml = rawYaml(profile);
        Map<String, String> example = envExample(profile);

        // EfsProperties and SftpProperties are plain @Component + @ConfigurationProperties — they bind
        // on every startup even when their feature flags are false. Note the binding is LENIENT: an
        // unresolvable placeholder on a String field binds the literal "${VAR}" with no error at all,
        // and only a typed field (sftp.port is an int) fails loudly. That asymmetry is exactly why the
        // example must list every one of these.
        for (String key : UNCONDITIONALLY_BOUND) {
            assertBareAndSupplied(profile, key, yaml, example);
        }
    }

    @DisplayName("example key set equals the variables the profile yaml reads")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void exampleKeysMatchYamlExactly(String profile) throws IOException {
        assertThat(new TreeSet<>(envExample(profile).keySet()))
                .as(".env.%s.example must list exactly the variables application.yml + "
                        + "application-%s.yml read — no missing key, no dead key", profile, profile)
                .isEqualTo(placeholders(rawYaml(profile).values()));
    }

    @DisplayName(".env.example key set equals the variables application.yml reads")
    @org.junit.jupiter.api.Test
    void baseExampleKeysMatchBaseYamlExactly() throws IOException {
        assertThat(new TreeSet<>(envFile(Path.of(".env.example")).keySet()))
                .as(".env.example must list exactly the variables application.yml reads")
                .isEqualTo(placeholders(yamlFile("application.yml").values()));
    }

    /**
     * Layout rule (2026-09-29): application.yml declares every shared key exactly once; each profile
     * file declares only per-environment knobs, and all four declare the SAME knobs. A profile key that
     * repeats a base key is the duplication that let the profiles drift apart (prod and local once
     * lacked four dispatch keys sit/uat had).
     */
    @DisplayName("profile files declare the same knobs and never re-declare a base key")
    @org.junit.jupiter.api.Test
    void profileFilesDeclareSameKnobsOnly() throws IOException {
        Set<String> base = yamlFile("application.yml").keySet();
        Set<String> expected = null;
        for (String profile : List.of("local", "sit", "uat", "prod")) {
            Set<String> knobs = new TreeSet<>(yamlFile("application-" + profile + ".yml").keySet());
            Set<String> repeated = new TreeSet<>(knobs);
            repeated.retainAll(base);
            repeated.remove("spring.config.import"); // each profile imports its own .env on top of base
            assertThat(repeated)
                    .as("application-%s.yml re-declares keys already in application.yml", profile)
                    .isEmpty();
            if (expected == null) {
                expected = knobs;
            }
            assertThat(knobs)
                    .as("application-%s.yml must declare the same knobs as application-local.yml", profile)
                    .isEqualTo(expected);
        }
    }

    @DisplayName("every .env*.example carries keys only — all values blank")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {".env.example", ".env.local.example", ".env.sit.example",
            ".env.uat.example", ".env.prod.example"})
    void exampleValuesAreBlank(String file) throws IOException {
        Map<String, String> nonBlank = new HashMap<>();
        envFile(Path.of(file)).forEach((k, v) -> {
            if (!v.isBlank()) {
                nonBlank.put(k, v);
            }
        });
        assertThat(nonBlank)
                .as("%s is a keys-only template — environment values belong in the host .env, "
                        + "never in a committed file", file)
                .isEmpty();
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
