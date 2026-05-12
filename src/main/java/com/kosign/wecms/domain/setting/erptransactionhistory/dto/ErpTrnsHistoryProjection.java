package com.kosign.wecms.domain.setting.erptransactionhistory.dto;

/**
 * JPQL constructor-projection row for {@code ERP_TRNS_HSTR} ⨝
 * {@code USER_INFM}. Internal to the repository layer — never escapes
 * onto the wire; the service maps it to {@link ErpTrnsHistoryRow}.
 *
 * <p>Replaces the legacy {@code WB_GET_USER_NM(REG_ID)} function call
 * from {@code IDO.ERP_TRNS_HSTR_R001.xml} with a LEFT JOIN. The LEFT
 * variant is intentional — {@code REG_ID = 'SYSTEM'} (batch writes)
 * and now-deleted users have no matching {@code USER_INFM} row, and
 * the frontend already falls back to {@code reg_id} when {@code
 * user_nm} is null (legacy behaviour at {@code comm_0219_001.ts:324}).
 */
public record ErpTrnsHistoryProjection(
        Long   seqNo,
        String orgFileNm,
        String saveFileNm,
        Long   fileSize,
        String erpType,
        String trnsTypeCd,
        String regDtm,
        String regId,
        String userNm
) { }
