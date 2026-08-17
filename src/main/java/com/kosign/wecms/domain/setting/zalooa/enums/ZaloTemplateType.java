package com.kosign.wecms.domain.setting.zalooa.enums;

import java.util.stream.Stream;

/**
 * Message-type codes stored in {@code ZALO_TMPL_INFM.TMPL_TYPE}.
 *
 * <p><b>Provisional.</b> The WeCMS message catalogue has not been agreed yet —
 * which WeCMS events warrant a Zalo message, and who receives them, is still an
 * open product decision. The two codes below carry over the shape of the
 * WeBill365 reference implementation (an event notice and a completion receipt)
 * so the schema and send path can be built; expect them to be renamed or
 * extended once the catalogue lands.
 *
 * <p>For that reason neither the DB column nor {@code ZaloTmplInfm} constrains
 * the value. Add a {@code CHECK} constraint in a follow-up migration when the
 * list is final.
 *
 * <p>Codes are stored verbatim rather than as an opaque numeric string — the
 * reference implementation used {@code '001'} / {@code '002'}, which forces a
 * lookup table to read a log line.
 */
public enum ZaloTemplateType {

    /** Something happened the recipient should know about. */
    NOTICE,

    /** Confirmation that something completed, typically carrying an amount. */
    RECEIPT;

    public String code() {
        return name();
    }

    /** @return the matching constant, or {@code null} when the code is unknown. */
    public static ZaloTemplateType fromCode(String raw) {
        return Stream.of(values())
                .filter(t -> t.name().equals(raw))
                .findFirst()
                .orElse(null);
    }
}
