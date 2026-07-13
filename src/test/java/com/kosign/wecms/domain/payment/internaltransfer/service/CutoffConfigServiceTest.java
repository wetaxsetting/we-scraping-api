package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.payment.internaltransfer.dto.CutoffConfigResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CutoffConfigResponse.BankCutoff;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CutoffConfigResponse.NetworkCutoff;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link CutoffConfigService} projects {@link CutoffConfigSnapshot} onto
 * the wire faithfully — the frontend reads this instead of a hard-coded table,
 * so the mapping must preserve the key cut-off rules:
 * Shinhan CITAD-API 16:30, Shinhan CITAD File-SFTP 15:30, VCB ≥500M 16:30,
 * and the config-driven ≥500M threshold.
 */
class CutoffConfigServiceTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("500000000");

    private static CutoffConfigSnapshot.NetworkWindows win(int h1, int m1, int h2, int m2, LocalTime warn) {
        return new CutoffConfigSnapshot.NetworkWindows(LocalTime.of(h1, m1), LocalTime.of(h2, m2), warn);
    }

    private static final CutoffConfigSnapshot FIXTURE = new CutoffConfigSnapshot(
            new CutoffConfigSnapshot.BankWindows(
                    win(0, 5, 23, 45, LocalTime.of(23, 0)),
                    win(9, 0, 15, 30, LocalTime.of(15, 0)),
                    win(9, 0, 16, 30, LocalTime.of(16, 0)),
                    win(9, 0, 22, 0, LocalTime.of(18, 30)),
                    null),
            new CutoffConfigSnapshot.BankWindows(
                    win(0, 15, 23, 55, LocalTime.of(23, 0)),
                    win(8, 30, 17, 40, LocalTime.of(17, 10)),
                    null,
                    win(8, 30, 19, 10, LocalTime.of(18, 30)),
                    null),
            new CutoffConfigSnapshot.BankWindows(
                    win(0, 5, 23, 45, LocalTime.of(23, 0)),
                    win(8, 0, 15, 30, LocalTime.of(15, 0)),
                    null,
                    null,
                    win(8, 0, 16, 30, null)),
            THRESHOLD
    );

    // Mock the provider to return the fixture snapshot; this test covers the
    // API mapping (snapshot → CutoffConfigResponse), not the DB load.
    private static CutoffConfigProvider providerReturning(CutoffConfigSnapshot snapshot) {
        CutoffConfigProvider provider = org.mockito.Mockito.mock(CutoffConfigProvider.class);
        org.mockito.Mockito.when(provider.current()).thenReturn(snapshot);
        return provider;
    }

    /** 2026-07-10 08:25:30 UTC == 15:25:30 Vietnam (+07:00). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T08:25:30Z"), ZoneOffset.UTC);

    private final CutoffConfigService service = new CutoffConfigService(providerReturning(FIXTURE), CLOCK);

    @Test
    void exposes_vietnam_server_datetime_and_timezone() {
        CutoffConfigResponse resp = service.build();
        assertThat(resp.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        // Server clock rendered in Vietnam zone with the +07:00 offset — never UTC.
        assertThat(resp.serverDatetime()).isEqualTo("2026-07-10T15:25:30+07:00");
    }

    private static NetworkCutoff net(CutoffConfigResponse resp, String bankKey, String network) {
        BankCutoff bank = resp.banks().stream()
                .filter(b -> b.bankKey().equals(bankKey)).findFirst().orElseThrow();
        return bank.networks().stream()
                .filter(n -> n.network().equals(network)).findFirst().orElseThrow();
    }

    @Test
    void exposes_threshold_from_config() {
        assertThat(service.build().largeTxnThreshold()).isEqualByComparingTo(THRESHOLD);
    }

    @Test
    void shinhan_citad_file_stays_1530_and_citad_api_stays_1630() {
        CutoffConfigResponse resp = service.build();
        assertThat(net(resp, "SHINHAN", "CITAD").end()).isEqualTo("15:30");
        assertThat(net(resp, "SHINHAN", "CITAD_API").end()).isEqualTo("16:30");
    }

    @Test
    void vcb_citad_base_1530_and_large_1630() {
        CutoffConfigResponse resp = service.build();
        assertThat(net(resp, "VCB", "CITAD").end()).isEqualTo("15:30");
        assertThat(net(resp, "VCB", "CITAD_LARGE").end()).isEqualTo("16:30");
    }

    @Test
    void warning_is_surfaced_and_hhmm_formatted_with_leading_zeros() {
        CutoffConfigResponse resp = service.build();
        assertThat(net(resp, "SHINHAN", "NAPAS").start()).isEqualTo("00:05");
        assertThat(net(resp, "SHINHAN", "CITAD").warning()).isEqualTo("15:00");
        // citad-large carries no warning → null on the wire.
        assertThat(net(resp, "VCB", "CITAD_LARGE").warning()).isNull();
    }

    @Test
    void vcb_has_no_swift_window() {
        CutoffConfigResponse resp = service.build();
        boolean hasSwift = resp.banks().stream()
                .filter(b -> b.bankKey().equals("VCB")).findFirst().orElseThrow()
                .networks().stream().anyMatch(n -> n.network().equals("SWIFT"));
        assertThat(hasSwift).isFalse();
    }
}
