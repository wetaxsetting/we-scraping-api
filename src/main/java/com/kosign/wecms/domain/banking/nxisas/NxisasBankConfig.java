package com.kosign.wecms.domain.banking.nxisas;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Per-{@code BANK_CD} Coocon adapter config — supplies the
 * NXISAS {@code Module} path and {@code Class} (CORPORATE / COMMON /
 * DIGIBIZ / CORPORATE_IBANK / CORPORATE_S2B / …) that the
 * gateway requires on every thread. WeCMS is enterprise-only — the
 * PERSONAL class is not supported (removed 2026-06-16, §16 of the
 * audit doc).
 *
 * <p>Source of truth: production traffic snapshot from
 * {@code wecmsvndev.nxsias_module_tran_log_his} (2026-05-15). The
 * legacy resolution path is two-pronged — static XML codes
 * ({@code WECMS_ETC/xml/code/NXISAS_MODULE} + {@code NXISAS_CLASS})
 * for ~14 banks, plus an OSMC API call
 * ({@code WABOOKSOSMCAPIUtil.callApi("BNK_BNK001_{BANK_CD}")} via
 * {@code nxisas_getModuleInfo_act.jsp}) for the remaining 9 banks.
 * The XML files carry stale/placeholder values; the OSMC service
 * runs externally and is out of refactor-spike scope. Production
 * NXISAS log rows captured the actual Module/Class pairs the
 * gateway accepted, including the OSMC-resolved values.
 *
 * <p>Defaults below are the {@code SEQ_NO=0} (Corporate by
 * convention) entry per bank. The only remaining multi-class bank is
 * Vietcom (CORPORATE default + DigiBiz SME portal at seq 1 →
 * DIGIBIZ); see {@link #SUB_TYPE_ENTRIES}. The former per-bank
 * personal sub-types were removed (enterprise-only, §16).
 *
 * <p>Eximbank (11704112) has no production log rows, so its pair
 * comes from the second authoritative source instead: the live
 * JexAdmin {@code API_CONN_INFM_DETL} dump (2026-06-16), which
 * carries {@code MODULE = GLOBAL/VNM/BANK_EXIM} and
 * {@code CLASS = CORPORATE} for {@code BNK_BNK001_11704112}. The
 * earlier best-guess {@code BANK_Eximbank} was corrected on
 * 2026-07-27 — see docs/audits/login-credentials-legacy-vs-refactor-audit.md
 * §"Bank cross-check" item 1.
 */
@Component
public class NxisasBankConfig {

    public record Entry(String module, String category) { }

    /**
     * Per-sub-type overrides. Some banks dispatch the same Module to
     * a different Coocon Class depending on the picker's sub-type
     * radio. WeCMS supports enterprise / corporate banking only, so the
     * sole remaining override is Vietcom's DigiBiz SME portal
     * (seq 1 → DIGIBIZ, an enterprise class); every other sub-type
     * falls through to the enterprise {@code SEQ_NO=0} default in
     * {@link #ENTRIES}.
     *
     * <p><b>PERSONAL profiles removed 2026-06-16.</b> PO decision: WeCMS
     * does not support personal scraping classes; the refactor
     * {@code BANK_CERT_INFO} carries zero credentials with
     * {@code NXISAS_GB_SEQ_NO IN (1, 2)} (verified — only credential is
     * bank 11704088 with seq 0). The ten {@code <bankCd>|1} / Vietcom
     * {@code |2} PERSONAL overrides were deleted; a seq=1/2 lookup for
     * those banks now resolves the enterprise default. See
     * docs/audits/bank-profile-enterprise-audit.md §16.
     *
     * <p>Verified against production traffic in
     * {@code wecmsvndev.nxsias_module_tran_log_his} (2026-05-15).
     * The {@code (bankCd, seqNo)} key matches the
     * {@code BANK_INFM_DETL.SEQ_NO} the picker sends as
     * {@code nxisas_gb_seq_no}.
     */
    private static final Map<String, Entry> SUB_TYPE_ENTRIES = Map.ofEntries(
            // Vietcom: VCB iB@nking (0) → CORPORATE (default in ENTRIES),
            //          VCB DigiBiz   (1) → DIGIBIZ (enterprise SME portal).
            // The Personal sub-type (seq 2) was removed — WeCMS is
            // enterprise-only; seq=2 now falls through to CORPORATE.
            Map.entry("11704004|1", new Entry("GLOBAL/VNM/BANK_Vietcom", "DIGIBIZ"))
    );

    private static final Map<String, Entry> ENTRIES = Map.ofEntries(
            // Statically-resolved set (14 banks) — values verified
            // against the live DEV NXISAS log on 2026-05-15.
            Map.entry("11704001", new Entry("GLOBAL/VNM/BANK_Vietin",   "CORPORATE")),
            Map.entry("11704002", new Entry("GLOBAL/VNM/BANK_Techcom",  "CORPORATE")),
            Map.entry("11704003", new Entry("GLOBAL/GBL/BANK_IBK",      "CORPORATE")),
            Map.entry("11704004", new Entry("GLOBAL/VNM/BANK_Vietcom",  "CORPORATE")),
            Map.entry("11704007", new Entry("GLOBAL/VNM/BANK_MB",       "CORPORATE")),
            // BIDV: production never uses bare "CORPORATE" — the
            // CORPORATE_IBANK flavour is the supported enterprise class.
            // (The legacy personal sub-type was removed — enterprise-only, §16.)
            Map.entry("11704008", new Entry("GLOBAL/VNM/BANK_BIDV",     "CORPORATE_IBANK")),
            Map.entry("11704011", new Entry("GLOBAL/VNM/BANK_ACB",      "CORPORATE")),
            Map.entry("11704012", new Entry("GLOBAL/VNM/BANK_VIB",      "COMMON")),
            // KB Bank VN: Module is GLOBAL/VNM/BANK_KB (not GBL) per
            // live traffic — 30 successful LOGIN calls.
            Map.entry("11704013", new Entry("GLOBAL/VNM/BANK_KB",       "COMMON")),
            Map.entry("11704014", new Entry("GLOBAL/VNM/BANK_MSB",      "CORPORATE")),
            Map.entry("11704020", new Entry("GLOBAL/GBL/BANK_Woori",    "CORPORATE")),
            Map.entry("11704080", new Entry("GLOBAL/GBL/BANK_KEBHana",  "CORPORATE")),
            // Shinhan VN: Class is COMMON in production (102 calls);
            // the single CORPORATE row in the log is an outlier /
            // legacy probe — the bank adapter rejects CORPORATE.
            Map.entry("11704088", new Entry("GLOBAL/GBL/BANK_Shinhan",  "COMMON")),
            Map.entry("11740099", new Entry("GLOBAL/APA/BANK_SMBC",     "CORPORATE")),

            // OSMC-resolved set (9 banks) — production-verified values
            // captured from nxsias_module_tran_log_his rows. These were
            // previously gated behind WBE2093 because the refactor
            // hasn't ported the OSMC API client; baking the resolved
            // values here lifts the gate for the spike.
            Map.entry("11704100", new Entry("GLOBAL/VNM/BANK_AGRI",     "CORPORATE")),
            Map.entry("11704101", new Entry("GLOBAL/VNM/BANK_HD",       "CORPORATE")),
            // VP Bank: default CORPORATE. WeCMS is an enterprise cash-
            // management platform, so a bank's SEQ_NO=0 (Corporate) path
            // is the only supported profile. (History: was PERSONAL with a
            // redundant 11704102|0 → CORPORATE override; the §13 VP defect
            // fix flipped the default to CORPORATE, then §16 removed the
            // PERSONAL sub-type entirely — docs/audits/bank-profile-enterprise-audit.md.)
            Map.entry("11704102", new Entry("GLOBAL/VNM/BANK_VP",       "CORPORATE")),
            Map.entry("11704103", new Entry("GLOBAL/VNM/BANK_TP",       "CORPORATE")),
            // SC VN: Module is GLOBAL/GBL/BANK_SC, Class is
            // CORPORATE_S2B (8 calls — only one Class observed).
            Map.entry("11704104", new Entry("GLOBAL/GBL/BANK_SC",       "CORPORATE_S2B")),
            Map.entry("11704105", new Entry("GLOBAL/VNM/BANK_SHB",      "CORPORATE")),
            // ABBANK: Module is BANK_AB (not BANK_ABBANK), Class is
            // COMMON. 40 production calls.
            Map.entry("11704106", new Entry("GLOBAL/VNM/BANK_AB",       "COMMON")),
            Map.entry("11704108", new Entry("GLOBAL/VNM/BANK_PG",       "CORPORATE")),
            // Eximbank: no production NXISAS log rows, but the live
            // JexAdmin API_CONN_INFM_DETL dump (2026-06-16) carries the
            // authoritative pair — BANK_EXIM (not BANK_Eximbank).
            Map.entry("11704112", new Entry("GLOBAL/VNM/BANK_EXIM",     "CORPORATE"))
    );

    public Optional<Entry> find(String bankCd) {
        return Optional.ofNullable(ENTRIES.get(bankCd));
    }

    /**
     * Sub-type-aware lookup. {@code seqNo} is the picker's
     * {@code nxisas_gb_seq_no} value (0 = Corporate, 1 = Personal,
     * etc., per {@code BANK_INFM_DETL.SEQ_NO}). Returns the per-sub-
     * type override when one exists in {@link #SUB_TYPE_ENTRIES};
     * otherwise falls back to {@link #find(String)} which returns
     * the {@code SEQ_NO=0} default.
     *
     * <p>A {@code null} or negative {@code seqNo} is treated as the
     * default sub-type — keeps legacy callers safe when the picker
     * hasn't surfaced sub-type radios yet (single-sub-type banks).
     */
    public Optional<Entry> find(String bankCd, Integer seqNo) {
        if (seqNo != null && seqNo >= 0) {
            Entry override = SUB_TYPE_ENTRIES.get(bankCd + "|" + seqNo);
            if (override != null) return Optional.of(override);
        }
        return find(bankCd);
    }

    /** ABBANK Module — its IB adapter rejects a {@code loginType} of
     *  {@code "ID"} and expects an empty string instead. */
    public static final String AB_BANK_MODULE = "GLOBAL/VNM/BANK_AB";

    /**
     * Resolve the Coocon {@code loginType} for a LOGIN job, centralising
     * the legacy per-bank quirk so the scraping + sync login paths stay
     * in lockstep. Mirrors legacy {@code nxisas_fnsh_r001_act.jsp:148-157}:
     *
     * <ul>
     *   <li>An explicit (non-blank) {@code rawLoginType} always wins — a
     *       credential row may carry a bank-specific value.</li>
     *   <li>When blank, ABBANK ({@link #AB_BANK_MODULE}) sends {@code ""}
     *       (its adapter rejects {@code "ID"}); every other bank defaults
     *       to {@code "ID"} (FNSH_0100_001:210).</li>
     * </ul>
     */
    public static String defaultLoginType(String module, String rawLoginType) {
        if (rawLoginType != null && !rawLoginType.isBlank()) {
            return rawLoginType;
        }
        return AB_BANK_MODULE.equalsIgnoreCase(module) ? "" : "ID";
    }
}
