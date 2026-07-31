package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.internaltransfer.service.CutoffValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Zone-consistency contract for the {@code SUPPORTED OVERRIDE} model chosen for
 * {@code wecms.dispatch.operating-hours.zone}.
 *
 * <h2>What this exists to prevent</h2>
 * The request-time gate ({@link CutoffValidator}) and the dispatch-time gate
 * ({@link OperatingHoursService}) must agree, or a payment accepted at 15:00 can be cut-off-failed at
 * dispatch. Both default to the business zone, so they agree out of the box — but only
 * {@code OperatingHoursService} reads the property. That asymmetry is the trap this class pins.
 *
 * <h2>Verified scope of the override (2026-07-29)</h2>
 * The property is honoured by <b>one</b> class. Twelve other dispatch classes plus
 * {@code CutoffValidator} hold a hardcoded {@code ZoneId.of("Asia/Ho_Chi_Minh")}. Setting the override
 * to {@code Asia/Seoul} therefore shifts <b>only the window decision</b> — not {@code PROC_DTTM},
 * {@code RQ_DTIME}, {@code TRAN_TM}, the request-time cut-off, the recovery grace window, or the
 * monitor. That is deliberate and narrow; treating it as a system-wide timezone switch would be wrong.
 * Documented in {@code docs/architecture/operating-hours-timezone-decision.md}.
 */
class OperatingHoursZoneConsistencyTest {

    /** The one property this class guards. Asserted as a KEY, never as file text. */
    private static final String ZONE_PROPERTY = "wecms.dispatch.operating-hours.zone";

    /** Dispatch classes that are intentionally pinned to ICT and do NOT read the property. */
    private static final List<String> ICT_FIXED_CONSUMERS = List.of(
            "dispatch/service/RealtimeDispatchService.java",
            "dispatch/service/RealtimeTransferService.java",
            "dispatch/service/BulkReceptionService.java",
            "dispatch/service/ResultCheckService.java",
            "dispatch/service/StrandedRecoveryService.java",
            "dispatch/callback/service/BulkReceptionResultService.java",
            "dispatch/callback/service/BulkExecutionResultService.java",
            "dispatch/monitor/StuckPayFileMonitorService.java",
            "dispatch/alert/OpsAlertService.java",
            "dispatch/recipient/DepositorInquiryService.java",
            "dispatch/recipient/FirmTranInquiryAuditService.java",
            "dispatch/recipient/RecipientLookupService.java");

    private static final Path PAYMENT_SRC =
            Path.of("src/main/java/com/kosign/wecms/domain/payment");

    private static OperatingHoursService serviceWithZone(String zone) {
        OperatingHoursService s = new OperatingHoursService(null, null);
        ReflectionTestUtils.setField(s, "operatingHoursZone", zone);
        return s;
    }

    private static String defaultZone() {
        // The in-code field default is what applies when the property is unset — the case that must
        // never be able to abort startup.
        return (String) ReflectionTestUtils.getField(
                new OperatingHoursService(null, null), "operatingHoursZone");
    }

    // ── the two required tests ───────────────────────────────────────────────

    @Test
    @DisplayName("requestTimeGate_andDispatchTimeGate_useTheSameConfiguredZone")
    void requestTimeGate_andDispatchTimeGate_useTheSameConfiguredZone() {
        // CutoffValidator gates at request time; OperatingHoursService gates at dispatch time. If their
        // zones differ, a payment accepted by the first can be rejected by the second — the exact
        // contradiction the OperatingHoursService Javadoc claims is impossible.
        // VIETNAM_ZONE is package-private in another package, so read it reflectively rather than
        // widening production visibility for a test.
        ZoneId requestTimeZone = (ZoneId) ReflectionTestUtils.getField(
                CutoffValidator.class, "VIETNAM_ZONE");
        ZoneId dispatchTimeZone = ZoneId.of(defaultZone());

        assertThat(dispatchTimeZone)
                .as("the dispatch-time gate's DEFAULT zone must equal the request-time gate's zone; "
                        + "otherwise the two gates disagree in every default deployment")
                .isEqualTo(requestTimeZone);
        assertThat(requestTimeZone.getId()).isEqualTo(OperatingHoursService.ZONE_BUSINESS_ICT);
    }

    @Test
    @DisplayName("allOperatingHoursConsumers_useOneValidatedZoneSource")
    void allOperatingHoursConsumers_useOneValidatedZoneSource() throws IOException {
        // Exactly ONE class may read the property. Every other dispatch class must be ICT-pinned, so
        // the override's blast radius is known and bounded. If a second class started reading the
        // property, the two could be configured inconsistently with each other; if an ICT-pinned class
        // silently became configurable, the "gate only" guarantee below would quietly become false.
        List<String> propertyReaders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(PAYMENT_SRC)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String body = Files.readString(p, StandardCharsets.UTF_8);
                if (body.contains("wecms.dispatch.operating-hours.zone")) {
                    propertyReaders.add(p.getFileName().toString());
                }
            }
        }

        assertThat(propertyReaders)
                .as("exactly one class may bind wecms.dispatch.operating-hours.zone")
                .containsExactly("OperatingHoursService.java");

        // And the rest stay pinned — asserted against the real sources so the list cannot rot.
        for (String rel : ICT_FIXED_CONSUMERS) {
            Path p = PAYMENT_SRC.resolve(rel);
            assertThat(Files.exists(p)).as("%s must exist (update this list if it moved)", rel).isTrue();
            String body = Files.readString(p, StandardCharsets.UTF_8);

            assertThat(body)
                    .as("%s must stay pinned to the business zone — the override is gate-only", rel)
                    .contains("ZoneId.of(\"" + OperatingHoursService.ZONE_BUSINESS_ICT + "\")");
            assertThat(body)
                    .as("%s must NOT read the operating-hours zone property", rel)
                    .doesNotContain("operating-hours.zone");
        }
    }

    // ── the closed accepted set ─────────────────────────────────────────────

    @Test
    @DisplayName("the accepted set is exactly two values — the business zone and the legacy clock")
    void acceptedSetIsExactlyTwoValues() {
        assertThat(OperatingHoursService.ACCEPTED_ZONES)
                .as("widening this set is a PO decision, not a code change")
                .isEqualTo(Set.of("Asia/Ho_Chi_Minh", "Asia/Seoul"));
    }

    @Test
    @DisplayName("default zone is explicit and is the business zone")
    void defaultZoneIsExplicitAndIsTheBusinessZone() {
        // An unset property must resolve to the business zone via the in-code field default, so a host
        // that never exports the variable still starts and still behaves correctly.
        assertThat(defaultZone()).isEqualTo(OperatingHoursService.ZONE_BUSINESS_ICT);
        assertThatCode(() -> serviceWithZone(defaultZone()).validateOperatingHoursZone())
                .doesNotThrowAnyException();
    }

    @DisplayName("both accepted zones start cleanly")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Asia/Ho_Chi_Minh", "Asia/Seoul"})
    void bothAcceptedZonesStartCleanly(String zone) {
        assertThatCode(() -> serviceWithZone(zone).validateOperatingHoursZone())
                .doesNotThrowAnyException();
    }

    @DisplayName("an unsupported zone fails at startup — including ones that parse fine")
    // The name pattern must stay non-blank: one argument below is the empty string, and JUnit rejects
    // a blank generated display name with PreconditionViolationException.
    @ParameterizedTest(name = "[{index}] zone=''{0}''")
    @ValueSource(strings = {
            "America/New_York",   // resolvable, but meaningless here — would silently move the window
            "UTC",                // ditto
            "Asia/Bangkok",       // same offset as ICT, still not an approved value
            "Asia/Hochiminh",     // the realistic typo (missing underscores)
            "",                   // blank
            "not-a-zone",         // garbage
    })
    void unsupportedZoneFailsAtStartup(String zone) {
        // A zone that merely parses is more dangerous than a typo: it throws nothing and just shifts
        // the gate. The closed set is what makes both cases fail loudly, at boot.
        assertThatThrownBy(() -> serviceWithZone(zone).validateOperatingHoursZone())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wecms.dispatch.operating-hours.zone")
                .hasMessageContaining("must be one of");
    }

    @Test
    @DisplayName("no committed YAML sets the property — it can only arrive deliberately")
    void noCommittedYamlSetsTheProperty() throws IOException {
        // If a profile YAML carried it, a deployment could inherit a zone nobody chose. Keeping it out
        // of every YAML means the only way to set it is an explicit, reviewed environment variable.
        //
        // 2026-07-30 — this assertion now parses KEYS instead of matching raw file text.
        // The old form was `Files.readString(...).doesNotContain("operating-hours")`, which could not
        // tell a property from a comment ABOUT that property. The bare-placeholder migration added a
        // "code-only properties" block to application.yml's header that names
        // wecms.dispatch.operating-hours.zone (with its in-code default) purely so the knob is
        // discoverable — no key, just documentation. The text match failed on that comment.
        //
        // Documenting the property is desirable; SETTING it in a committed YAML is what this test
        // exists to prevent. Parsing the loaded property source distinguishes the two, so the guard
        // now binds to the real contract and the comment can stay.
        for (String yml : List.of("application.yml", "application-local.yml",
                "application-sit.yml", "application-uat.yml", "application-prod.yml")) {
            Path p = Path.of("src/main/resources", yml);
            assertThat(Files.exists(p)).as("%s must exist", yml).isTrue();

            List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load(yml, new FileSystemResource(p));
            boolean declaresKey = sources.stream()
                    .anyMatch(s -> s.containsProperty(ZONE_PROPERTY));
            assertThat(declaresKey)
                    .as("%s must not declare %s as a key (a comment naming it is fine)",
                            yml, ZONE_PROPERTY)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("the two accepted zones genuinely differ, so the override is meaningful")
    void theTwoAcceptedZonesGenuinelyDiffer() {
        // Sanity anchor for the decision doc: ICT is UTC+7, KST UTC+9, neither observes DST. A stored
        // 15:30 cut-off therefore lands 2 h apart depending on the choice — which is precisely why the
        // knob exists and why it must not be set casually.
        java.time.Instant t = java.time.Instant.parse("2026-07-29T08:00:00Z");
        assertThat(java.time.LocalTime.ofInstant(t, ZoneId.of("Asia/Ho_Chi_Minh")))
                .isEqualTo(java.time.LocalTime.of(15, 0));
        assertThat(java.time.LocalTime.ofInstant(t, ZoneId.of("Asia/Seoul")))
                .isEqualTo(java.time.LocalTime.of(17, 0));
    }
}
