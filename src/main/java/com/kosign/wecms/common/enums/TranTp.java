package com.kosign.wecms.common.enums;

import java.util.Optional;

/**
 * Firm-banking gateway transaction-type codes — the value set of the
 * legacy {@code TB_FIRM_TRAN.TRAN_TP} column. Mirrors legacy enum
 * {@code wabooks.enums.TranTp} at
 * {@code WECMS DEV/WECMS/src/wabooks/enums/TranTp.java:6-11} (4 codes;
 * legacy Korean labels are the second column on lines 38-41).
 *
 * <p><b>Modernized refactor enum.</b> Carries {@code (code, i18nKey)} only —
 * no JEX {@code GenericEnum}, no {@code @JsonValue} / {@code @JsonCreator},
 * no {@code getLabel()}. Localised display strings live in the frontend
 * {@code locales/{en,ko,vi}.json} files under the {@code fb_history.service.*}
 * namespace.
 *
 * <p><b>Why a Spring enum, not a JPA-mapped entity attribute.</b>
 * The TRAN_TP column is read through the {@code V_FIRM_BANKING_HISTORY}
 * view's {@code SVC_NM} alias as a plain {@link String}; persistence
 * does not need an enum mapping. This class exists purely to:
 * <ul>
 *   <li>Centralise the code → i18n-key translation that powers the
 *       wire-side {@code svc_name_key} field on
 *       {@link com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryRow}.</li>
 *   <li>Give callers a typed handle on the legacy code set so adding
 *       a new TRAN_TP value (e.g. when the legacy enum grows) is one
 *       enum constant + one i18n entry, not a switch update across
 *       three layers.</li>
 * </ul>
 *
 * <p>Per the workspace task rule "Do not use {@code EnumType.ORDINAL}",
 * this enum is <b>not</b> persisted to the database under any
 * {@code @Enumerated} mapping. The DB column stays {@code VARCHAR(3)}
 * holding the legacy numeric string.
 *
 * <p>The enum's {@link #i18nKey()} resolves to the same
 * {@code fb_history.service.&lt;code&gt;} namespace the frontend's
 * {@code <FirmBankingHistoryTable>} consumes via {@code t(key)}, so
 * the wire field stays a stable identifier and the locale-specific
 * display text lives in {@code locales/en.json}.
 */
public enum TranTp {

    /** {@code 201} — 펌뱅킹 예금주 조회 / "Inquiry Recipient" (legacy {@code j021}). */
    BANKING_INQUIRY_OF_ACCOUNT_HOLDER("201", "fb_history.service.201"),

    /** {@code 202} — 펌뱅킹 입금이체 / "Transfer Payment" (legacy {@code j022}). */
    BANKING_DEPOSIT_TRANSFER("202", "fb_history.service.202"),

    /** {@code 203} — 펌뱅킹 입금이체 결과 조회 / "Inquiry Transfer Result" (legacy {@code j023}). */
    BANKING_DEPOSIT_TRANSFER_RESULT_INQUIRY("203", "fb_history.service.203"),

    /** {@code 204} — 환전이체조회 / FX-transfer inquiry. Present in the
     *  legacy enum but <b>absent from</b> {@code comm_0221_001.ts}
     *  SERVICE_NAME_MAP — there is no verified English label for this
     *  code on the legacy frontend. Per the workspace rule "Do not
     *  invent translations", {@link #i18nKey} is left {@code null} so
     *  the resolver propagates {@code null} → frontend renders the
     *  raw {@code "204"} (legacy parity). When a verified translation
     *  becomes available, set the i18n key here AND add the
     *  {@code fb_history.service.204} entry to {@code locales/en.json}
     *  in the same MR. */
    BANKING_EXCHANGE_TRANSFER_INQUIRY("204", null);

    private final String code;
    private final String i18nKey;

    TranTp(String code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }

    /** Raw 3-char code stored in {@code TB_FIRM_TRAN.TRAN_TP}. */
    public String code() {
        return code;
    }

    /** Frontend i18n key — {@code fb_history.service.<code>}.
     *  {@code null} for codes whose English translation has not been
     *  verified against the legacy frontend (today: only {@code 204}).
     *  Frontend treats {@code null} as "render raw {@code svc_nm}". */
    public String i18nKey() {
        return i18nKey;
    }

    /** Resolve a raw {@code TRAN_TP} string to its enum constant.
     *  Returns {@link Optional#empty()} for unknown / blank input
     *  (callers fall back to the raw {@code svc_nm}). Whitespace is
     *  trimmed; the lookup is case-sensitive (legacy values are
     *  numeric / uppercase only). */
    public static Optional<TranTp> fromCode(String code) {
        if (code == null) return Optional.empty();
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (TranTp t : values()) {
            if (t.code.equals(trimmed)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
