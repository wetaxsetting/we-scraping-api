package com.kosign.wecms.domain.payment.validation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row in {@code BLOCKED_CHARS_RULE} — a per-bank / per-payment-type /
 * per-field set of characters that Excel-upload data cells MUST NOT
 * contain.
 *
 * <p>Consumed by {@code SpecialCharValidator} during parse of the
 * Excel templates for Internal Transfer / Vendor / Expense / Payroll
 * / Overseas. Mirrors legacy {@code wabooks.dao.BlockedCharRuleDAO}.
 *
 * <p>Schema parity quirk: the legacy column name on DEV is
 * {@code PYMN_TPYE} (typo of "TYPE" — the YE is reversed). We preserve
 * the column name verbatim for parity but expose the Java field as
 * {@code pymnType} so refactor code never has to type the typo.
 */
@Entity
@Table(name = "BLOCKED_CHARS_RULE")
@Getter
@Setter
@NoArgsConstructor
public class BlockedCharsRule {

    @Id
    @Column(name = "RULE_ID", length = 4, nullable = false)
    private String ruleId;

    @Column(name = "BANK_CD", length = 15, nullable = false)
    private String bankCd;

    @Column(name = "BANK_NM", length = 100, nullable = false)
    private String bankNm;

    /** Payment-type code: INTR / VEND / EXPN / PAYR / OVER. Column name
     *  preserves the legacy {@code PYMN_TPYE} typo. */
    @Column(name = "PYMN_TPYE", length = 4, nullable = false)
    private String pymnType;

    /** Field code: RMKS / RCPT / MEMO / BENA / BEAD. */
    @Column(name = "FIELD_CD", length = 4, nullable = false)
    private String fieldCd;

    /** Raw set of blocked characters — NOT a regex. The validator
     *  iterates characters and rejects on any membership hit. */
    @Column(name = "BLOCKED_CHARS", length = 100, nullable = false)
    private String blockedChars;

    /** Inclusive activation date/time. JEX column is {@code varchar(14)}
     *  ({@code yyyyMMddHHmmss}); null = always active. */
    @Column(name = "EFF_FR_DT", length = 14)
    private String effFrDt;

    /** Inclusive retirement date/time. JEX column is {@code varchar(14)};
     *  null = no end. */
    @Column(name = "EFF_TO_DT", length = 14)
    private String effToDt;

    @Column(name = "VERSION", length = 2, nullable = false)
    private String version;

    /** {@code 'Y'} = active, {@code 'N'} = retired. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "STATUS", length = 1, nullable = false)
    private String status;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
