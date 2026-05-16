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
 * {@code vasAcctNm}) ride together — when the caller wants to set a
 * mapping, all three MUST be supplied; when the caller wants to clear
 * one, all three MUST be explicitly empty strings (saving → no VAS).
 * The service validates the FK against {@code BUSI_VAS_ACCT_INFM} so a
 * malicious / stale client cannot stamp an arbitrary {@code seqNo}.
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
        @Size(max = 10)  String  maturityDate,
        /** VAS Account FK — primary key on {@code BUSI_VAS_ACCT_INFM}.
         *  {@code null} = leave the existing mapping unchanged; {@code 0}
         *  / empty wire token = clear the mapping (all three VAS fields
         *  go null on the parent row). Service validates the row is
         *  {@code STTS='A'} + {@code DEL_YN='N'} for the current tenant. */
        Long             vasAcctSeqNo,
        /** Echo of {@code BUSI_VAS_ACCT_INFM.VAS_ACCT_CD} for the picked
         *  row. Service ignores any value mismatched against the
         *  authoritative DB row — kept on the wire purely so the dialog
         *  can dispatch a single PATCH without a follow-up GET. */
        @Size(max = 100) String  vasAcctCd,
        /** Echo of {@code BUSI_VAS_ACCT_INFM.VAS_ACCT_NM}. Same wire-only
         *  contract as {@code vasAcctCd}. */
        @Size(max = 200) String  vasAcctNm
) { }
