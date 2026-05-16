package com.kosign.wecms.domain.banking.nxisas;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Per-{@code BANK_CD} Coocon adapter config — supplies the
 * NXISAS {@code Module} path and {@code Class} (CORPORATE / COMMON /
 * PERSONAL / DIGIBIZ / CORPORATE_IBANK / CORPORATE_S2B / …) that the
 * gateway requires on every thread.
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
 * convention) entry per bank. Banks with multiple legitimate
 * Class values per {@code SEQ_NO} (BIDV's CORPORATE_IBANK vs
 * PERSONAL; Vietcom's CORPORATE / DIGIBIZ / PERSONAL; Agribank's
 * CORPORATE / PERSONAL; VP, TP, Techcom) need the sub-type radio
 * to dispatch correctly — that's the Step 5 work tracked in
 * the migration doc.
 *
 * <p>Eximbank (11704112) is intentionally registered with a
 * best-guess {@code BANK_Eximbank/CORPORATE} pair — there are no
 * production log rows for it. Once a live login proves the actual
 * values, update this entry from the same {@code
 * nxsias_module_tran_log_his} table.
 */
@Component
public class NxisasBankConfig {

    public record Entry(String module, String category) { }

    /**
     * Per-sub-type overrides. Some banks dispatch the same Module to
     * a different Coocon Class depending on the picker's sub-type
     * radio (e.g. Vietcom: VCB iB@nking → CORPORATE, VCB DigiBiz →
     * DIGIBIZ, Personal → PERSONAL). Lookup falls through to the
     * {@code SEQ_NO=0} default in {@link #ENTRIES} when no entry
     * exists for {@code (bankCd, seqNo)}.
     *
     * <p>Verified against production traffic in
     * {@code wecmsvndev.nxsias_module_tran_log_his} (2026-05-15).
     * The {@code (bankCd, seqNo)} key matches the
     * {@code BANK_INFM_DETL.SEQ_NO} the picker sends as
     * {@code nxisas_gb_seq_no}.
     */
    private static final Map<String, Entry> SUB_TYPE_ENTRIES = Map.ofEntries(
            // Vietin VN: Corporate (0) / Personal (1)
            Map.entry("11704001|1", new Entry("GLOBAL/VNM/BANK_Vietin",  "PERSONAL")),
            // Techcom: Corporate (0) / Personal (1)
            Map.entry("11704002|1", new Entry("GLOBAL/VNM/BANK_Techcom", "PERSONAL")),
            // Vietcom: VCB iB@nking (0) → CORPORATE (default in ENTRIES),
            //         VCB DigiBiz   (1) → DIGIBIZ,
            //         Personal      (2) → PERSONAL
            Map.entry("11704004|1", new Entry("GLOBAL/VNM/BANK_Vietcom", "DIGIBIZ")),
            Map.entry("11704004|2", new Entry("GLOBAL/VNM/BANK_Vietcom", "PERSONAL")),
            // MB: Corporate (0) / Personal (1)
            Map.entry("11704007|1", new Entry("GLOBAL/VNM/BANK_MB",      "PERSONAL")),
            // BIDV: CORPORATE_IBANK (0, default) / Personal (1)
            Map.entry("11704008|1", new Entry("GLOBAL/VNM/BANK_BIDV",    "PERSONAL")),
            // Shinhan VN: COMMON (0, default) / Personal (1) — Personal
            //   class is best-guess since prod hasn't logged it; if
            //   Coocon rejects it, fall back to COMMON on the gateway
            //   layer (no per-bank override needed yet).
            Map.entry("11704088|1", new Entry("GLOBAL/GBL/BANK_Shinhan", "PERSONAL")),
            // Agribank: Corporate (0) / Personal (1)
            Map.entry("11704100|1", new Entry("GLOBAL/VNM/BANK_AGRI",    "PERSONAL")),
            // HD Bank: Corporate (0) / Personal (1)
            Map.entry("11704101|1", new Entry("GLOBAL/VNM/BANK_HD",      "PERSONAL")),
            // VP Bank: Corporate (0) / Personal (1)
            //   default in ENTRIES is PERSONAL (most-frequent prod
            //   value) — flip Corporate when seq=0 explicitly.
            Map.entry("11704102|0", new Entry("GLOBAL/VNM/BANK_VP",      "CORPORATE")),
            // TP Bank: Corporate (0) / Personal (1)
            Map.entry("11704103|1", new Entry("GLOBAL/VNM/BANK_TP",      "PERSONAL"))
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
            // bank adapter accepts CORPORATE_IBANK or PERSONAL only.
            // SEQ_NO=0 default is the iBank flavour (35 calls vs 15
            // for Personal). Step 5 (sub-type) routes seq=1 → PERSONAL.
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
            // VP Bank: PERSONAL is the dominant Class in production
            // (6 vs 4 for CORPORATE). Default to PERSONAL until the
            // sub-type radio (Step 5) makes the choice explicit.
            Map.entry("11704102", new Entry("GLOBAL/VNM/BANK_VP",       "PERSONAL")),
            Map.entry("11704103", new Entry("GLOBAL/VNM/BANK_TP",       "CORPORATE")),
            // SC VN: Module is GLOBAL/GBL/BANK_SC, Class is
            // CORPORATE_S2B (8 calls — only one Class observed).
            Map.entry("11704104", new Entry("GLOBAL/GBL/BANK_SC",       "CORPORATE_S2B")),
            Map.entry("11704105", new Entry("GLOBAL/VNM/BANK_SHB",      "CORPORATE")),
            // ABBANK: Module is BANK_AB (not BANK_ABBANK), Class is
            // COMMON. 40 production calls.
            Map.entry("11704106", new Entry("GLOBAL/VNM/BANK_AB",       "COMMON")),
            Map.entry("11704108", new Entry("GLOBAL/VNM/BANK_PG",       "CORPORATE")),
            // Eximbank: no production log rows — best-guess naming
            // convention (BANK_{Name}/CORPORATE) following the VNM
            // pattern. Update once a real login surfaces in the log.
            Map.entry("11704112", new Entry("GLOBAL/VNM/BANK_Eximbank", "CORPORATE"))
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
}
