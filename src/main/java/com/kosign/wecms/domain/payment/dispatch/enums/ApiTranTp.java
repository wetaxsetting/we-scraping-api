package com.kosign.wecms.domain.payment.dispatch.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * API transaction type — value set of the legacy {@code API_TRAN_TP} column
 * on {@code TB_FIRM_BAT_API_TRAN} (the batch-level EFS API audit ledger).
 * 5-char code distinguishing which leg of the bulk-transfer exchange a row
 * audits.
 *
 * <p>Values verified against:
 * <ul>
 *   <li>{@code consts.WCConst.VN_API_301 = "00301"} / {@code VN_API_302 = "00302"}
 *       (WeCMS → EFS requests).</li>
 *   <li>{@code FILE_DEPOSIT_TRANSFER_API1_act.jsp} logs {@code API_TRAN_TP='01301'}
 *       and {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp} the {@code '01302'}
 *       counterpart (EFS → WeCMS callbacks).</li>
 * </ul>
 *
 * <p>Note: the realtime transaction types {@code 201/202/203/204} are a
 * <i>different</i> column ({@code TB_FIRM_TRAN.TRAN_TP}, {@code VARCHAR(3)}) and
 * are modelled by {@link com.kosign.wecms.common.enums.TranTp} — do not conflate
 * the two.
 *
 * <p>Same modernized idiom as {@link com.kosign.wecms.common.enums.InoutDv};
 * the DB column is {@code CHAR(5)}, never persisted via {@code @Enumerated}.
 */
public enum ApiTranTp {

    /** {@code 00301} — 대량이체 접수 요청 / bulk reception request (WeCMS → EFS, ex-BATCH1). */
    BULK_RECEPTION("00301"),

    /** {@code 00302} — 대량이체 실행 요청 / bulk execution request (WeCMS → EFS, ex-BATCH2 / now inline). */
    BULK_EXECUTION("00302"),

    /** {@code 01301} — 대량이체 접수 결과 통지 / bulk reception result (EFS → WeCMS callback / API1). */
    BULK_RECEPTION_RESULT("01301"),

    /** {@code 01302} — 대량이체 실행 결과 통지 / bulk execution result (EFS → WeCMS callback / API2). */
    BULK_EXECUTION_RESULT("01302");

    private final String code;

    ApiTranTp(String code) {
        this.code = code;
    }

    /** Raw 5-char code stored in {@code TB_FIRM_BAT_API_TRAN.API_TRAN_TP}. */
    public String code() {
        return code;
    }

    public static Optional<ApiTranTp> fromCode(String raw) {
        return Arrays.stream(values()).filter(e -> e.code.equals(raw)).findFirst();
    }
}
