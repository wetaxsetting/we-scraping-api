package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.payment.internaltransfer.dto.CutoffConfigResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CutoffConfigResponse.BankCutoff;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CutoffConfigResponse.NetworkCutoff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the read-only {@link CutoffConfigResponse} from
 * {@link CutoffConfigSnapshot} — the single backend source of truth for cut-off
 * windows + the ≥500M threshold. Lets the frontend fetch the resolved config
 * instead of hard-coding it (2026-07-09). Also stamps the authoritative
 * Vietnam server datetime so the frontend evaluates cut-off against server
 * time, not the browser clock (2026-07-10).
 */
@Service
@RequiredArgsConstructor
public class CutoffConfigService {

    /** Cut-off is evaluated in Vietnam-local time everywhere — same zone the
     *  authoritative {@code CutoffValidator} uses. No hard-coded UTC+7. */
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CutoffConfigProvider configProvider;
    /** Server clock (the {@code internalTransferClock} bean — {@code systemUTC()}). */
    private final Clock clock;

    /** Snapshot the current configuration + server time for the wire (DB-only,
     *  loaded by CutoffConfigProvider). Window contract unchanged; adds
     *  {@code server_datetime} + {@code timezone}. */
    public CutoffConfigResponse build() {
        CutoffConfigSnapshot properties = configProvider.current();
        String serverDatetime = ZonedDateTime.now(clock.withZone(VIETNAM_ZONE))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new CutoffConfigResponse(
                serverDatetime,
                VIETNAM_ZONE.getId(),
                properties.largeTxnThreshold(),
                List.of(
                        bank("SHINHAN", properties.shinhan()),
                        bank("WOORI", properties.woori()),
                        bank("VCB", properties.vcb())));
    }

    private static BankCutoff bank(String key, CutoffConfigSnapshot.BankWindows w) {
        List<NetworkCutoff> nets = new ArrayList<>();
        if (w != null) {
            add(nets, "NAPAS", w.napas());
            add(nets, "CITAD", w.citad());
            add(nets, "CITAD_API", w.citadApi());
            add(nets, "SWIFT", w.swift());
            add(nets, "CITAD_LARGE", w.citadLarge());
        }
        return new BankCutoff(key, nets);
    }

    private static void add(List<NetworkCutoff> nets, String name, CutoffConfigSnapshot.NetworkWindows nw) {
        if (nw == null || nw.start() == null || nw.end() == null) {
            return;
        }
        nets.add(new NetworkCutoff(name, fmt(nw.start()), fmt(nw.end()), fmt(nw.warning())));
    }

    /** {@code HH:mm}, or {@code null} when the window carries no such time. */
    private static String fmt(LocalTime t) {
        return t == null ? null : String.format("%02d:%02d", t.getHour(), t.getMinute());
    }
}
