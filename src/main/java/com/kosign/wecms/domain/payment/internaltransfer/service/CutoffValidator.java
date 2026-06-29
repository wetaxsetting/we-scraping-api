package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Verifies that a dispatch ("Transfer") request happens within the
 * bank's allowed window. Mirrors legacy
 * {@code PymnHelper.validateTransferTiming} + {@code getCutoffConfig}
 * but reads windows from {@link CutoffProperties} rather than hard-coded
 * constants.
 *
 * <h2>Rules (legacy parity + 17-Jun-2026 Shinhan IMP)</h2>
 *
 * <ul>
 *   <li>Time is evaluated in Vietnam-local zone (UTC+7).</li>
 *   <li>Bank identity is resolved from {@code TB_FIRM_BANK_CODE.ENG_ABRVAT_NM}
 *       ({@code ShinhanBank} / {@code WooriBank} / {@code Vietcombank}),
 *       not a hard-coded {@code OUT_BANK_CD}. Unknown banks pass silently
 *       (legacy "{@code getCutoffConfig} returns null → allow").</li>
 *   <li>NAPAS runs every day Mon-Sun. CITAD and SWIFT do not run
 *       Saturday / Sunday → {@code WBE2068}.</li>
 *   <li>Before the start time → {@code WBE2067}.</li>
 *   <li><b>Shinhan CITAD 16:30</b> (IMP 17-Jun-2026): the Shinhan CITAD
 *       cut-off is 16:30 for <b>VND→VND</b> ({@link CutoffCheck#vndToVnd()})
 *       and 15:30 otherwise. This is <b>currency-based, not transport-based</b>
 *       — it mirrors updated legacy {@code PymnHelper.validateTransferTiming}
 *       (PymnHelper.java:3257-3263) so the File-route modules (Payroll) also
 *       get 16:30. {@link CutoffCheck#apiTransport()} is retained on the
 *       context but no longer selects the window.</li>
 *   <li><b>VCB CITAD ≥500M tier</b>: when the bank is VCB, the network is
 *       CITAD, any line is ≥ 500,000,000 VND, and a {@code citadLarge}
 *       window is configured, the cut-off bumps from 15:30 to 16:30
 *       (legacy per-transaction tier).</li>
 *   <li>After the cut-off time → {@code WBE2066}. <b>Shinhan parity</b>:
 *       Shinhan past-cut-off is logged-only (allowed) EXCEPT for CITAD
 *       VND→VND, which hard-blocks like Woori/VCB (legacy req.
 *       2026-04-14). Woori / VCB always block past cut-off.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CutoffValidator {

    /** Vietnam zone — cut-off times are evaluated in VN-local time (UTC+7). */
    static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Active-bank flag in {@code TB_FIRM_BANK_CODE} (legacy {@code bank_stas = '1'}). */
    private static final String BANK_STAS_ACTIVE = "1";

    private final CutoffProperties properties;
    private final Clock clock;
    /** Brand resolution by {@code ENG_ABRVAT_NM} (legacy {@code TB_FIRM_BANK_CODE}). */
    private final TbFirmBankCodeRepository tbFirmBankCodeRepository;

    /** Brand the cut-off table is keyed on. */
    private enum Bank { SHINHAN, WOORI, VCB }

    /**
     * Back-compat entry point — builds a {@link CutoffCheck} with all
     * extra flags {@code false} (File-SFTP baseline, no large-tier, no
     * Shinhan hard-block). Used by the Overseas SWIFT path.
     */
    public void validate(String outBankCd, TransferNetwork network) {
        validate(new CutoffCheck(outBankCd, network, false, false, false));
    }

    /**
     * Validate dispatch timing.
     *
     * @throws BusinessException one of {@code WBE2066} / {@code WBE2067} /
     *                           {@code WBE2068} when the dispatch is rejected.
     */
    public void validate(CutoffCheck ctx) {
        TransferNetwork network = ctx.network() == null ? TransferNetwork.CITAD : ctx.network();
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(VIETNAM_ZONE));

        // 1. Weekend block for non-NAPAS networks.
        DayOfWeek dow = now.getDayOfWeek();
        boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
        if (isWeekend && network != TransferNetwork.NAPAS) {
            log.warn("feature=internal-transfer cutoff blocked: weekend non-NAPAS dispatch (bank={} network={})",
                    ctx.outBankCd(), network);
            throw new BusinessException("WBE2068");
        }

        // 2. Resolve the bank brand from TB_FIRM_BANK_CODE.ENG_ABRVAT_NM.
        Bank bank = resolveBank(ctx.outBankCd());
        if (bank == null) {
            log.debug("feature=internal-transfer cutoff bypass: unknown bank (outBankCd={})", ctx.outBankCd());
            return;
        }
        CutoffProperties.BankWindows windows = windowsFor(bank);
        if (windows == null) {
            log.debug("feature=internal-transfer cutoff bypass: no windows configured for bank={}", bank);
            return;
        }

        // 3. Resolve the (network, transport, tier) window.
        CutoffProperties.NetworkWindows window = resolveWindow(bank, network, windows, ctx);
        if (window == null || window.start() == null || window.end() == null) {
            log.debug("feature=internal-transfer cutoff bypass: no window for bank={} network={} apiTransport={}",
                    bank, network, ctx.apiTransport());
            return;
        }

        LocalTime nowTime = now.toLocalTime();

        // 4. Before start time.
        if (nowTime.isBefore(window.start())) {
            throw new BusinessException("WBE2067", network.name(), formatTime(window.start()));
        }

        // 5. After cut-off time. Shinhan: logged-only EXCEPT CITAD VND→VND.
        if (!nowTime.isBefore(window.end())) {
            boolean isShinhan = bank == Bank.SHINHAN;
            boolean shinhanHardBlock =
                    isShinhan && network == TransferNetwork.CITAD && ctx.vndToVnd();
            if (isShinhan && !shinhanHardBlock) {
                log.warn("feature=internal-transfer cutoff warning (Shinhan, non-blocking): bank={} network={} cutoff={}",
                        ctx.outBankCd(), network, window.end());
                return;
            }
            throw new BusinessException("WBE2066", formatTime(window.end()), network.name());
        }
    }

    /**
     * Pick the window for {@code (bank, network)}, applying the
     * CITAD-API transport split and the VCB ≥500M tier.
     */
    private CutoffProperties.NetworkWindows resolveWindow(
            Bank bank, TransferNetwork network, CutoffProperties.BankWindows windows, CutoffCheck ctx) {
        switch (network) {
            case NAPAS:
                return windows.napas();
            case SWIFT:
                return windows.swift();
            case CITAD:
            default:
                // Shinhan CITAD VND→VND → 16:30. Legacy parity:
                // PymnHelper.validateTransferTiming bumps the cut-off to 16:30
                // for Shinhan CITAD when CCY_CD==VND && WDRL_CCY_CD==VND
                // (currency-based, NOT transport-based) — see PymnHelper.java:3257-3263.
                // Keyed on vndToVnd, independent of apiTransport, so the File-route
                // modules (Payroll) get 16:30 too, matching legacy. (apiTransport is
                // retained on CutoffCheck but no longer drives window selection — the
                // VND→VND currency test fully determines the Shinhan 16:30 window.)
                if (bank == Bank.SHINHAN && ctx.vndToVnd() && windows.citadApi() != null) {
                    return windows.citadApi();
                }
                // VCB ≥500M tier — bumps the File-SFTP CITAD cut-off to 16:30.
                if (bank == Bank.VCB && ctx.hasTxnAtOrAbove500M() && windows.citadLarge() != null) {
                    return windows.citadLarge();
                }
                return windows.citad();
        }
    }

    /**
     * Resolve {@code OUT_BANK_CD} → brand via the active
     * {@code TB_FIRM_BANK_CODE} row's {@code ENG_ABRVAT_NM}. Returns
     * {@code null} when the bank is unknown / inactive / not Shinhan,
     * Woori or VCB — the cut-off then passes (legacy parity).
     */
    private Bank resolveBank(String outBankCd) {
        if (outBankCd == null || outBankCd.isBlank()) {
            return null;
        }
        String abrvat = tbFirmBankCodeRepository
                .findFirstByBankCdAndBankStas(outBankCd.trim(), BANK_STAS_ACTIVE)
                .map(b -> b.getEngAbrvatNm())
                .orElse(null);
        if (abrvat == null) {
            return null;
        }
        String key = abrvat.trim().toLowerCase(Locale.ROOT);
        if (key.contains("shinhan")) return Bank.SHINHAN;
        if (key.contains("woori"))   return Bank.WOORI;
        if (key.contains("vietcom") || key.contains("vcb")) return Bank.VCB;
        return null;
    }

    private CutoffProperties.BankWindows windowsFor(Bank bank) {
        return switch (bank) {
            case SHINHAN -> properties.shinhan();
            case WOORI   -> properties.woori();
            case VCB     -> properties.vcb();
        };
    }

    /** {@code HH:mm} formatter used in the user-facing error message. */
    private static String formatTime(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }
}
