package com.kosign.wecms.domain.setting.bankaccount.dto;

import jakarta.validation.constraints.Size;

/**
 * Patch payload for the Settings &gt; Bank Account row identified by
 * {@code (bankCd, acctNo)} in the path. Mirrors the legacy
 * {@code comm_0183_003_u001_act.jsp:103-145} update path — every field
 * is optional, null means "leave unchanged". The service rejects the
 * all-null case with {@code WBE_VALIDATION}.
 *
 * <p>VAS mapping fields ({@code vasAcctSeqNo} / {@code vasAcctCd} /
 * {@code vasAcctNm}) are NOT in Batch A — they ship in Batch B alongside
 * the VAS picker dialog. Including them on the request would let a
 * malicious client overwrite the FK; keep the surface tight until the
 * picker lands and we can validate the FK against
 * {@code BUSI_VAS_ACCT_INFM}.
 */
public record BankAccountUpdateRequest(
        @Size(max = 20)  String  nickname,
        @Size(max = 200) String  description,
        /** Legacy 2-char `ACCT_TYPE_CD` — frontend translates from the
         *  semantic "current" / "saving" / "fixed" value before sending.
         *  Service rejects unknown codes via {@link com.kosign.wecms.common.enums.AcctTypeCd#fromCode}. */
        @Size(max = 2)   String  accountTypeCd,
        /** Maturity date as ISO {@code yyyy-MM-dd}; service strips the
         *  hyphens before writing to the legacy `VARCHAR(8)` column.
         *  Empty string = clear the date (saving → current). */
        @Size(max = 10)  String  maturityDate
) { }
