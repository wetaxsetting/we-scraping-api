package com.kosign.wecms.domain.payment.haengsung.cardaccount;

import com.kosign.wecms.config.haengsung.HaengsungOracleDataSourceConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Narrow Oracle access for the HAENGSUNG account-history + card-approval sync
 * (Step 7 — legacy {@code ERP_0100_003}). Writes the two Oracle objects this
 * feature targets — {@code TB_ACCT_HISTORY} and {@code TB_CARD_APPROVAL} — via
 * the Step-1 secondary Oracle {@code JdbcTemplate}. The primary WeCMS PostgreSQL
 * datasource is never used here; no payment Oracle table ({@code TB_TRNS_*}) is
 * touched. No credentials are logged.
 *
 * <p>Gated on {@code haengsung.oracle.enabled=true} (Step-1 foundation): with the
 * flag off, the secondary Oracle {@code JdbcTemplate} bean and this repository do
 * not exist.
 *
 * <p>Both inserts replicate the legacy {@code *_C002} IDOs verbatim — idempotent
 * {@code INSERT … SELECT … FROM DUAL WHERE NOT EXISTS} on the PK, so a re-run
 * never throws a unique-constraint error (affects 0 rows instead). {@code ERP_GET_ST='0'};
 * {@code ERP_GET_DTTM} + the columns not produced by the read are left null
 * (legacy {@code putAll} parity).
 */
@Repository
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class HaengsungCardAccountOracleRepository {

    private static final String ERP_GET_ST = "0";

    private final JdbcTemplate oracle;

    public HaengsungCardAccountOracleRepository(
            @Qualifier(HaengsungOracleDataSourceConfig.ORACLE_JDBC_TEMPLATE) JdbcTemplate oracle) {
        this.oracle = oracle;
    }

    /**
     * Idempotent account-history insert (legacy {@code TB_ACCT_HISTORY_C002}).
     * PK = {@code (BIZ_NO, BANK_CD, ACCT_NO, TR_DT, TR_SEQNO)}. Returns rows
     * inserted (0 or 1).
     */
    public int insertAcctHistory(OracleAcctHistory r) {
        String sql = "INSERT INTO TB_ACCT_HISTORY ("
                + "BIZ_NO, BANK_CD, ACCT_NO, TR_DT, TR_SEQNO, CCY_CD, TR_AMT, TR_CUR_BAL, IO_GB, "
                + "ACCT_MEMO, BANK_BRANCH, PURPOSE, VAS, USER_MENO, GDT_INVOICE_INFO, "
                + "LAST_INQ_DTTM, CMS_PUT_DTTM, ERP_GET_DTTM, ERP_GET_ST) "
                + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? FROM DUAL "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM TB_ACCT_HISTORY "
                + "WHERE BIZ_NO = ? AND BANK_CD = ? AND ACCT_NO = ? AND TR_DT = ? AND TR_SEQNO = ?)";
        return oracle.update(sql,
                r.bizNo(), r.bankCd(), r.acctNo(), r.trDt(), r.trSeqno(), r.ccyCd(), r.trAmt(),
                r.trCurBal(), r.ioGb(), r.acctMemo(),
                null, null, null, null, null,           // BANK_BRANCH/PURPOSE/VAS/USER_MENO/GDT_INVOICE_INFO
                r.lastInqDttm(), r.cmsPutDttm(), null, ERP_GET_ST,
                // NOT EXISTS guard (PK)
                r.bizNo(), r.bankCd(), r.acctNo(), r.trDt(), r.trSeqno());
    }

    /**
     * Idempotent card-approval insert (legacy {@code TB_CARD_APPROVAL_C002}).
     * PK = {@code (BIZ_NO, BANK_CD, CARD_NO, APPR_DT, APPR_TM, AUTH_NO)}. Returns
     * rows inserted (0 or 1).
     */
    public int insertCardApproval(OracleCardApproval r) {
        String sql = "INSERT INTO TB_CARD_APPROVAL ("
                + "BIZ_NO, BANK_CD, CARD_NO, APPR_DT, APPR_TM, AUTH_NO, CARD_NM, MERCH_NM, CCY_CD, "
                + "APPR_AMT, USAGE, PURCHASE_ST, CITY, PURPOSE, VAS, APPR_SEQNO, GDT_INVOICE_INFO, "
                + "LAST_INQ_DTTM, CMS_PUT_DTTM, ERP_GET_DTTM, ERP_GET_ST) "
                + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? FROM DUAL "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM TB_CARD_APPROVAL "
                + "WHERE BIZ_NO = ? AND BANK_CD = ? AND CARD_NO = ? AND APPR_DT = ? "
                + "AND APPR_TM = ? AND AUTH_NO = ?)";
        return oracle.update(sql,
                r.bizNo(), r.bankCd(), r.cardNo(), r.apprDt(), r.apprTm(), r.authNo(), r.cardNm(),
                r.merchNm(), r.ccyCd(), r.apprAmt(),
                null,                                   // USAGE
                r.purchaseSt(), r.city(),
                null, null, null, null,                 // PURPOSE/VAS/APPR_SEQNO/GDT_INVOICE_INFO
                r.lastInqDttm(), r.cmsPutDttm(), null, ERP_GET_ST,
                // NOT EXISTS guard (PK)
                r.bizNo(), r.bankCd(), r.cardNo(), r.apprDt(), r.apprTm(), r.authNo());
    }
}
