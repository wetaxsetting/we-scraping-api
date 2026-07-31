package com.kosign.wecms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-3 — the Actuator exposure and access contract, asserted against the real
 * {@code application*.yml} and {@code SecurityConfig}.
 *
 * <p><b>Why this test exists.</b> Actuator was added because three places already assumed
 * {@code /actuator/health} existed ({@code SecurityConfig:75} permits it,
 * {@code RequestResponseLoggingFilter:69} skips {@code /actuator}, and the SIT deployment checklist has
 * an "{@code /actuator/health} UP" step) while the dependency was absent, so all three pointed at a 404.
 * Adding the starter closes that — but Actuator's default surface is wide, and the two things that must
 * never regress are: (a) only {@code health} and {@code info} are exposed, and (b) only
 * {@code health} is public.
 *
 * <p>Deliberately a configuration test, matching {@link SchedulerConfigurationDefaultsTest}: it needs no
 * database, no secrets and no Spring context, so it runs on every commit. A context test would prove the
 * live HTTP behaviour but could not run in CI without a DB, and the failure mode being guarded here is a
 * configuration edit, not a wiring bug.
 */
class ActuatorEndpointSecurityTest {

    private static final YamlPropertySourceLoader LOADER = new YamlPropertySourceLoader();

    /** The only endpoints any environment may expose. */
    private static final Set<String> ALLOWED_EXPOSURE = Set.of("health", "info");

    /**
     * Endpoints that must never appear in the exposure list — each would leak configuration,
     * credentials, or enough runtime detail to help an attacker.
     */
    private static final List<String> FORBIDDEN_ENDPOINTS = List.of(
            "env", "configprops", "beans", "heapdump", "threaddump", "loggers",
            "mappings", "scheduledtasks", "shutdown", "quartz", "flyway", "liquibase", "*");

    private static StandardEnvironment environmentFor(String profile) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

        addYaml(env, "application.yml");
        if (profile != null) {
            addYaml(env, "application-" + profile + ".yml");
        }
        return env;
    }

    private static void addYaml(StandardEnvironment env, String file) throws IOException {
        ClassPathResource resource = new ClassPathResource(file);
        assertThat(resource.exists()).as(file + " must exist").isTrue();
        for (PropertySource<?> ps : LOADER.load(file, resource)) {
            env.getPropertySources().addFirst(ps);
        }
    }

    /* ── exposure allow-list ────────────────────────────────────────────────────── */

    @DisplayName("only health + info are exposed, in every profile")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void exposureIsLimitedToHealthAndInfo(String profile) throws IOException {
        StandardEnvironment env = environmentFor(profile);

        String include = env.getProperty("management.endpoints.web.exposure.include");
        assertThat(include).as("exposure allow-list must be set explicitly").isNotBlank();

        Set<String> exposed = Arrays.stream(include.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(exposed)
                .as("widening this list is a security change, not a config tweak")
                .isSubsetOf(ALLOWED_EXPOSURE);
        assertThat(exposed).contains("health");   // the checklist step depends on it
    }

    @DisplayName("no sensitive endpoint is exposed, in any profile")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void sensitiveEndpointsAreNotExposed(String profile) throws IOException {
        String include = environmentFor(profile).getProperty("management.endpoints.web.exposure.include");

        for (String forbidden : FORBIDDEN_ENDPOINTS) {
            assertThat(include)
                    .as("'%s' must never be exposed (profile=%s)", forbidden, profile)
                    .doesNotContain(forbidden);
        }
    }

    /* ── health payload must carry no detail ────────────────────────────────────── */

    @DisplayName("health shows no details or components, in every profile")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void healthNeverShowsDetails(String profile) throws IOException {
        StandardEnvironment env = environmentFor(profile);

        // With details on, the DataSource contributor serialises the JDBC URL and the disk contributor
        // serialises absolute paths and free bytes. `never` keeps the body to {"status":"UP"} while the
        // contributors still decide the overall status, so the probe stays meaningful.
        assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(env.getProperty("management.endpoint.health.show-components")).isEqualTo("never");
    }

    @DisplayName("info contributes no build / git / env metadata")
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"local", "sit", "uat", "prod"})
    void infoContributorsAreOff(String profile) throws IOException {
        StandardEnvironment env = environmentFor(profile);

        assertThat(env.getProperty("management.info.env.enabled")).isEqualTo("false");
        assertThat(env.getProperty("management.info.java.enabled")).isEqualTo("false");
        assertThat(env.getProperty("management.info.os.enabled")).isEqualTo("false");
    }

    /* ── access split: health public, everything else authenticated ─────────────── */

    @Test
    @DisplayName("SecurityConfig permits /actuator/health and nothing else under /actuator")
    void onlyHealthIsUnauthenticated() throws IOException {
        String securityConfig = Files.readString(
                Path.of("src/main/java/com/kosign/wecms/config/SecurityConfig.java"));

        // The allow-list line that makes the checklist's unauthenticated probe possible.
        assertThat(securityConfig)
                .as("/actuator/health must stay in the permitAll matcher")
                .contains("\"/actuator/health\"");

        // Any broader actuator matcher would silently publish info (build/git metadata) or worse.
        assertThat(securityConfig)
                .as("no wildcard actuator matcher may be permitted — /actuator/info must stay authenticated")
                .doesNotContain("/actuator/**")
                .doesNotContain("\"/actuator/info\"")
                .doesNotContain("/actuator/*");
    }

    @Test
    @DisplayName("the management base path is the default /actuator the callers assume")
    void basePathIsNotRelocated() throws IOException {
        // SecurityConfig, RequestResponseLoggingFilter:69 and the SIT checklist all hardcode
        // "/actuator". Relocating the base path would bypass the allow-list and the log-skip.
        for (String profile : List.of("local", "sit", "uat", "prod")) {
            assertThat(environmentFor(profile).getProperty("management.endpoints.web.base-path"))
                    .as("profile=%s must keep the default base path", profile)
                    .isNull();
        }
    }
}
