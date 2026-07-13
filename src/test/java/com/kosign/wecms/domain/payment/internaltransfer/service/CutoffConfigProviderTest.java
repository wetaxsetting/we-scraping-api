package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.payment.internaltransfer.entity.CutoffConfig;
import com.kosign.wecms.domain.payment.internaltransfer.entity.CutoffParam;
import com.kosign.wecms.domain.payment.internaltransfer.repository.CutoffConfigRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.CutoffParamRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CutoffConfigProvider} — the <b>DB-only</b> load with
 * fail-fast startup validation and fail-safe refresh (2026-07-09). Pure Mockito.
 *
 * <p>No {@code application.yml} fallback exists: an empty / incomplete / invalid
 * DB config makes {@code init()} throw (the app would refuse to start), and an
 * invalid live reload keeps the last valid snapshot.
 */
class CutoffConfigProviderTest {

    private static final String KEY = "large-txn-threshold";

    /* ── builders ── */

    private static CutoffConfig row(String bank, String net, String s, String e, String w) {
        return row(bank, net, s, e, w, "Y");
    }

    private static CutoffConfig row(String bank, String net, String s, String e, String w, String enable) {
        CutoffConfig c = new CutoffConfig();
        c.setBankKey(bank);
        c.setNetwork(net);
        c.setStartTime(s);
        c.setEndTime(e);
        c.setWarningTime(w);
        c.setEnableYn(enable);
        return c;
    }

    private static CutoffParam param(String value, String enable) {
        CutoffParam p = new CutoffParam();
        p.setParamKey(KEY);
        p.setParamValue(value);
        p.setEnableYn(enable);
        return p;
    }

    /** A complete, valid set of the 10 required rows (matches the seed). */
    private static List<CutoffConfig> fullRows() {
        List<CutoffConfig> rows = new ArrayList<>();
        rows.add(row("SHINHAN", "NAPAS", "00:05", "23:45", "23:00"));
        rows.add(row("SHINHAN", "CITAD", "09:00", "15:30", "15:00"));
        rows.add(row("SHINHAN", "CITAD_API", "09:00", "16:30", "16:00"));
        rows.add(row("SHINHAN", "SWIFT", "09:00", "22:00", "18:30"));
        rows.add(row("WOORI", "NAPAS", "00:15", "23:55", "23:00"));
        rows.add(row("WOORI", "CITAD", "08:30", "17:40", "17:10"));
        rows.add(row("WOORI", "SWIFT", "08:30", "19:10", "18:30"));
        rows.add(row("VCB", "NAPAS", "00:05", "23:45", "23:00"));
        rows.add(row("VCB", "CITAD", "08:00", "15:30", "15:00"));
        rows.add(row("VCB", "CITAD_LARGE", "08:00", "16:30", null));
        return rows;
    }

    private static CutoffConfigProvider provider(List<CutoffConfig> rows, CutoffParam threshold) {
        CutoffConfigRepository configRepo = mock(CutoffConfigRepository.class);
        when(configRepo.findAll()).thenReturn(rows);
        CutoffParamRepository paramRepo = mock(CutoffParamRepository.class);
        when(paramRepo.findById(anyString())).thenReturn(Optional.ofNullable(threshold));
        return new CutoffConfigProvider(configRepo, paramRepo);
    }

    /* ── happy path ── */

    @Test
    void init_loads_complete_db_config() {
        CutoffConfigProvider p = provider(fullRows(), param("500000000", "Y"));
        p.init();
        CutoffConfigSnapshot s = p.current();
        assertThat(s.shinhan().citad().end()).isEqualTo(LocalTime.of(15, 30));
        assertThat(s.shinhan().citadApi().end()).isEqualTo(LocalTime.of(16, 30));
        assertThat(s.vcb().citadLarge().end()).isEqualTo(LocalTime.of(16, 30));
        assertThat(s.largeTxnThreshold()).isEqualByComparingTo("500000000");
        // A rail a bank does not offer (VCB SWIFT) stays null — intentional.
        assertThat(s.vcb().swift()).isNull();
    }

    @Test
    void db_values_are_the_source_of_truth() {
        List<CutoffConfig> rows = fullRows();
        // Override Shinhan CITAD to a distinct value only the DB could supply.
        rows.removeIf(r -> "SHINHAN".equals(r.getBankKey()) && "CITAD".equals(r.getNetwork()));
        rows.add(row("SHINHAN", "CITAD", "09:30", "15:45", "15:20"));
        CutoffConfigProvider p = provider(rows, param("700000000", "Y"));
        p.init();
        assertThat(p.current().shinhan().citad().end()).isEqualTo(LocalTime.of(15, 45));
        assertThat(p.current().largeTxnThreshold()).isEqualByComparingTo("700000000");
    }

    /* ── fail-fast startup validation (no fallback) ── */

    @Test
    void init_fails_when_config_empty() {
        CutoffConfigProvider p = provider(List.of(), param("500000000", "Y"));
        assertThatThrownBy(p::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete/invalid");
    }

    @Test
    void init_fails_when_required_row_missing() {
        List<CutoffConfig> rows = fullRows();
        rows.removeIf(r -> "SHINHAN".equals(r.getBankKey()) && "CITAD".equals(r.getNetwork()));
        CutoffConfigProvider p = provider(rows, param("500000000", "Y"));
        assertThatThrownBy(p::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHINHAN/CITAD");
    }

    @Test
    void init_fails_when_required_row_disabled() {
        List<CutoffConfig> rows = fullRows();
        rows.removeIf(r -> "VCB".equals(r.getBankKey()) && "CITAD_LARGE".equals(r.getNetwork()));
        rows.add(row("VCB", "CITAD_LARGE", "08:00", "16:30", null, "N")); // disabled
        CutoffConfigProvider p = provider(rows, param("500000000", "Y"));
        assertThatThrownBy(p::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VCB/CITAD_LARGE");
    }

    @Test
    void init_fails_when_threshold_missing() {
        CutoffConfigProvider p = provider(fullRows(), null);
        assertThatThrownBy(p::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(KEY);
    }

    @Test
    void init_fails_when_threshold_not_positive_or_not_numeric() {
        assertThatThrownBy(() -> provider(fullRows(), param("0", "Y")).init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be > 0");
        assertThatThrownBy(() -> provider(fullRows(), param("abc", "Y")).init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a number");
    }

    @Test
    void init_fails_when_time_malformed() {
        List<CutoffConfig> rows = fullRows();
        rows.removeIf(r -> "WOORI".equals(r.getBankKey()) && "CITAD".equals(r.getNetwork()));
        rows.add(row("WOORI", "CITAD", "8:3", "17:40", "17:10")); // bad HH:mm
        CutoffConfigProvider p = provider(rows, param("500000000", "Y"));
        assertThatThrownBy(p::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HH:mm");
    }

    @Test
    void init_fails_when_start_not_before_end() {
        List<CutoffConfig> rows = fullRows();
        rows.removeIf(r -> "SHINHAN".equals(r.getBankKey()) && "SWIFT".equals(r.getNetwork()));
        rows.add(row("SHINHAN", "SWIFT", "22:00", "09:00", "18:30")); // start after end
        CutoffConfigProvider p = provider(rows, param("500000000", "Y"));
        assertThatThrownBy(p::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be before");
    }

    @Test
    void current_throws_before_init() {
        CutoffConfigProvider p = provider(fullRows(), param("500000000", "Y"));
        assertThatThrownBy(p::current).isInstanceOf(IllegalStateException.class);
    }

    /* ── fail-safe refresh (keep last valid snapshot) ── */

    @Test
    void refresh_keeps_last_valid_snapshot_when_reload_is_invalid() {
        List<CutoffConfig> good = fullRows();
        List<CutoffConfig> broken = fullRows();
        broken.removeIf(r -> "SHINHAN".equals(r.getBankKey()) && "CITAD".equals(r.getNetwork())); // now incomplete

        CutoffConfigRepository configRepo = mock(CutoffConfigRepository.class);
        when(configRepo.findAll()).thenReturn(good).thenReturn(broken);
        CutoffParamRepository paramRepo = mock(CutoffParamRepository.class);
        when(paramRepo.findById(anyString())).thenReturn(Optional.of(param("500000000", "Y")));

        CutoffConfigProvider p = new CutoffConfigProvider(configRepo, paramRepo);
        p.init(); // good
        CutoffConfigSnapshot before = p.current();

        // Second load is incomplete → refresh rejected, last-good retained.
        assertThat(p.refresh()).isFalse();
        assertThat(p.current()).isSameAs(before);
        assertThat(p.current().shinhan().citad().end()).isEqualTo(LocalTime.of(15, 30));
    }

    @Test
    void refresh_applies_a_valid_reload() {
        List<CutoffConfig> first = fullRows();
        List<CutoffConfig> updated = fullRows();
        updated.removeIf(r -> "SHINHAN".equals(r.getBankKey()) && "CITAD".equals(r.getNetwork()));
        updated.add(row("SHINHAN", "CITAD", "09:00", "16:00", "15:30")); // new end 16:00

        CutoffConfigRepository configRepo = mock(CutoffConfigRepository.class);
        when(configRepo.findAll()).thenReturn(first).thenReturn(updated);
        CutoffParamRepository paramRepo = mock(CutoffParamRepository.class);
        when(paramRepo.findById(anyString())).thenReturn(Optional.of(param("500000000", "Y")));

        CutoffConfigProvider p = new CutoffConfigProvider(configRepo, paramRepo);
        p.init();
        assertThat(p.current().shinhan().citad().end()).isEqualTo(LocalTime.of(15, 30));

        assertThatCode(() -> assertThat(p.refresh()).isTrue()).doesNotThrowAnyException();
        assertThat(p.current().shinhan().citad().end()).isEqualTo(LocalTime.of(16, 0));
    }
}
