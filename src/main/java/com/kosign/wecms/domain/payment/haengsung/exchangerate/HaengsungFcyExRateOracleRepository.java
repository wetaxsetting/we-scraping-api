package com.kosign.wecms.domain.payment.haengsung.exchangerate;

import com.kosign.wecms.config.haengsung.HaengsungOracleDataSourceConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Narrow Oracle access for the HAENGSUNG FX-rate sync (Step 6 — legacy
 * {@code ERP_0100_001}). Writes the one Oracle object this feature touches,
 * {@code TB_FCY_EXRATE}, via the Step-1 secondary Oracle {@code JdbcTemplate}.
 * The primary WeCMS PostgreSQL datasource is never used here; no other Oracle
 * table is read or written. No credentials are logged.
 *
 * <p>Gated on {@code haengsung.oracle.enabled=true} (Step-1 foundation): when the
 * flag is off, the secondary Oracle {@code JdbcTemplate} bean does not exist and
 * neither does this repository — the app boots PostgreSQL-only.
 *
 * <p>Upsert strategy is verbatim legacy: {@link #insert} replicates the
 * idempotent {@code TB_FCY_EXRATE_C002} ({@code INSERT … SELECT … FROM DUAL WHERE
 * NOT EXISTS} on the PK), {@link #update} replicates {@code TB_FCY_EXRATE_U001}.
 * The caller (legacy {@code UpsertTbFcyExRateData}) picks insert vs update from
 * the source row's {@code ERP_SND_YN}: {@code 'Y'} → update, else insert.
 */
@Repository
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class HaengsungFcyExRateOracleRepository {

    /** Legacy sets ERP_GET_ST='0' on both insert and update. */
    private static final String ERP_GET_ST = "0";

    private final JdbcTemplate oracle;

    public HaengsungFcyExRateOracleRepository(
            @Qualifier(HaengsungOracleDataSourceConfig.ORACLE_JDBC_TEMPLATE) JdbcTemplate oracle) {
        this.oracle = oracle;
    }

    /**
     * Idempotent insert (legacy {@code TB_FCY_EXRATE_C002}). Inserts only when no
     * row with the same PK ({@code BIZ_NO, BANK_CD, REF_DT, CCY_CD, REF_SEQ})
     * already exists, so a re-run never throws a unique-constraint error — it
     * simply affects 0 rows. {@code ERP_GET_DTTM} is left null (legacy parity).
     * Returns rows inserted (0 or 1).
     */
    public int insert(OracleFcyExRate r) {
        String sql = "INSERT INTO TB_FCY_EXRATE ("
                + "BIZ_NO, BANK_CD, REF_DT, CCY_CD, REF_SEQ, REF_TM, "
                + "BUY_AMT, SELL_AMT, TRAN_SELL_AMT, TRAN_BUY_AMT, BASIC_RATE, "
                + "LAST_INQ_DTTM, CMS_PUT_DTTM, ERP_GET_DTTM, ERP_GET_ST) "
                + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? FROM DUAL "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM TB_FCY_EXRATE "
                + "WHERE BIZ_NO = ? AND BANK_CD = ? AND REF_DT = ? AND CCY_CD = ? AND REF_SEQ = ?)";
        return oracle.update(sql,
                r.bizNo(), r.bankCd(), r.refDt(), r.ccyCd(), r.refSeq(), r.refTm(),
                r.buyAmt(), r.sellAmt(), r.tranSellAmt(), r.tranBuyAmt(), r.basicRate(),
                r.lastInqDttm(), r.cmsPutDttm(), null, ERP_GET_ST,
                // NOT EXISTS guard (PK)
                r.bizNo(), r.bankCd(), r.refDt(), r.ccyCd(), r.refSeq());
    }

    /**
     * Update an already-sent rate (legacy {@code TB_FCY_EXRATE_U001}) — refreshes
     * the rate/amount columns + {@code LAST_INQ_DTTM} / {@code CMS_PUT_DTTM} /
     * {@code ERP_GET_ST}, keyed on the PK. Returns rows updated (0 when the row is
     * not present — benign).
     */
    public int update(OracleFcyExRate r) {
        String sql = "UPDATE TB_FCY_EXRATE SET "
                + "REF_TM = ?, BUY_AMT = ?, SELL_AMT = ?, TRAN_SELL_AMT = ?, TRAN_BUY_AMT = ?, "
                + "BASIC_RATE = ?, LAST_INQ_DTTM = ?, CMS_PUT_DTTM = ?, ERP_GET_ST = ? "
                + "WHERE BIZ_NO = ? AND BANK_CD = ? AND REF_DT = ? AND CCY_CD = ? AND REF_SEQ = ?";
        return oracle.update(sql,
                r.refTm(), r.buyAmt(), r.sellAmt(), r.tranSellAmt(), r.tranBuyAmt(),
                r.basicRate(), r.lastInqDttm(), r.cmsPutDttm(), ERP_GET_ST,
                r.bizNo(), r.bankCd(), r.refDt(), r.ccyCd(), r.refSeq());
    }
}
