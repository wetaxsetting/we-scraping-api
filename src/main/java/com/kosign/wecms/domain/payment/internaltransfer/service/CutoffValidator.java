package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Verifies that a dispatch ("Transfer") request happens within the
 * bank's allowed window. Mirrors legacy
 * {@code PymnHelper.validateTransferTiming} + {@code getCutoffConfig}
 * but reads windows from {@link CutoffProperties} rather than
 * hard-coded constants — see OQ-6 in
 * {@code docs/refactor-wecms/features/internal-transfer.md}.
 *
 * <h2>Rules</h2>
 *
 * <ul>
 *   <li>Time is evaluated in Vietnam-local zone
 *       ({@link InternalTransferKeyGenerator#VIETNAM_ZONE}).</li>
 *   <li>NAPAS runs every day Mon-Sun. CITAD and SWIFT do not run
 *       Saturday / Sunday → {@code WBE2068}.</li>
 *   <li>Before the start time → {@code WBE2067}.</li>
 *   <li>After the cut-off time → {@code WBE2066}. Legacy parity:
 *       Shinhan past-cut-off is logged-only (warning, not blocking)
 *       — preserved here. Refactor still emits a log line so ops can
 *       grep for it.</li>
 *   <li>Unknown {@code (bank, network)} pairs pass silently (matches
 *       legacy "return null → allow transfer").</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CutoffValidator {

    /** Vietnam zone — re-used from the key generator for consistency. */
    static final ZoneId VIETNAM_ZONE = InternalTransferKeyGenerator.VIETNAM_ZONE;

    private final CutoffProperties properties;
    private final Clock clock;

    /**
     * Validate dispatch timing.
     *
     * @param outBankCd batch's withdrawal bank ({@code SCDUL_PYMN_SUMR.OUT_BANK_CD}).
     * @param network   network derived from {@code SCDUL_PYMN_SUMR_DETL.ANT_DIV_CD}
     *                  of the first line (use
     *                  {@link TransferNetwork#fromAntDivCd}).
     * @throws BusinessException one of {@code WBE2066} / {@code WBE2067} /
     *                           {@code WBE2068} when the dispatch is rejected.
     */
    public void validate(String outBankCd, TransferNetwork network) {
        if (network == null) {
            // Defensive — the dispatch service always passes a network,
            // but if it ever doesn't, default to CITAD per legacy line 3144.
            network = TransferNetwork.CITAD;
        }
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(VIETNAM_ZONE));

        // 1. Weekend block for non-NAPAS networks.
        DayOfWeek dow = now.getDayOfWeek();
        boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
        if (isWeekend && network != TransferNetwork.NAPAS) {
            log.warn("feature=internal-transfer cutoff blocked: weekend non-NAPAS dispatch (bank={} network={})",
                    outBankCd, network);
            throw new BusinessException("WBE2068");
        }

        // 2. Per-(bank, network) window lookup.
        CutoffProperties.NetworkWindows window = resolveWindow(outBankCd, network);
        if (window == null) {
            // Unknown bank — legacy returns null and allows. Refactor matches.
            log.debug("feature=internal-transfer cutoff bypass: no window for bank={} network={}",
                    outBankCd, network);
            return;
        }

        LocalTime nowTime = now.toLocalTime();

        // 3. Before start time.
        if (nowTime.isBefore(window.start())) {
            throw new BusinessException("WBE2067", network.name(), formatTime(window.start()));
        }

        // 4. After cut-off time. Shinhan: legacy logs warning, doesn't block.
        if (!nowTime.isBefore(window.end())) {
            boolean isShinhan = outBankCd != null
                    && outBankCd.equals(properties.shinhanBankCd());
            if (isShinhan) {
                log.warn("feature=internal-transfer cutoff warning (Shinhan, non-blocking): bank={} network={} cutoff={}",
                        outBankCd, network, window.end());
                return;
            }
            throw new BusinessException("WBE2066", formatTime(window.end()), network.name());
        }
    }

    /** Returns the window for {@code (bank, network)}, or {@code null}
     *  when the bank is not in the configured map. */
    private CutoffProperties.NetworkWindows resolveWindow(String outBankCd, TransferNetwork network) {
        Map<String, CutoffProperties.NetworkWindows> bankMap = bankMapFor(outBankCd);
        if (bankMap == null) return null;
        return bankMap.get(network.name().toLowerCase(Locale.ROOT));
    }

    private Map<String, CutoffProperties.NetworkWindows> bankMapFor(String outBankCd) {
        if (outBankCd == null) return null;
        if (outBankCd.equals(properties.shinhanBankCd())) return properties.shinhan();
        if (outBankCd.equals(properties.wooriBankCd()))   return properties.woori();
        return null;
    }

    /** {@code HH:mm} formatter used in the user-facing error message. */
    private static String formatTime(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }
}
