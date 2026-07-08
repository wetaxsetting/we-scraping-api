package com.kosign.wecms.common.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G3/G4 follow-up (PO decision 2026-07-08) — the payment delete
 * approval-block uses the legacy code {@code WBE1063}, and the duplicate
 * {@code WBE2062} definition is removed.
 *
 * <p>Property files silently keep the last definition when a key is
 * duplicated, so this guards the raw file content (not the resolved
 * {@code Properties}) to catch a re-introduced duplicate.
 */
class DeleteBlockMessageCodesTest {

    private static List<String> lines(String resource) throws IOException {
        try (InputStream in = DeleteBlockMessageCodesTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("%s on classpath", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    /** Count non-comment definition lines for a key (`KEY=...`). */
    private static long defs(List<String> lines, String key) {
        return lines.stream().filter(l -> l.startsWith(key + "=")).count();
    }

    private static String value(List<String> lines, String key) {
        return lines.stream()
                .filter(l -> l.startsWith(key + "="))
                .map(l -> l.substring((key + "=").length()))
                .findFirst()
                .orElse(null);
    }

    /** G4 — `WBE2062` is defined exactly once (the NXISAS-block duplicate removed). */
    @Test
    void wbe2062_isDefinedExactlyOnce() throws IOException {
        assertThat(defs(lines("messages.properties"), "WBE2062")).isEqualTo(1);
    }

    /** G3 — the legacy delete-block code `WBE1063` is present and non-blank
     *  in both the base and the EN message bundles. */
    @Test
    void wbe1063_isDefinedInBothBundles() throws IOException {
        List<String> base = lines("messages.properties");
        List<String> en   = lines("messages_en.properties");

        assertThat(defs(base, "WBE1063")).isEqualTo(1);
        assertThat(defs(en, "WBE1063")).isEqualTo(1);
        assertThat(value(base, "WBE1063")).isNotBlank();
        assertThat(value(en, "WBE1063")).isNotBlank();
    }
}
