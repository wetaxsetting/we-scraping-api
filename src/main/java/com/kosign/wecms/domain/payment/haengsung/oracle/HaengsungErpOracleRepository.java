package com.kosign.wecms.domain.payment.haengsung.oracle;

import com.kosign.wecms.config.haengsung.HaengsungOracleDataSourceConfig;
import com.kosign.wecms.domain.payment.haengsung.HaengsungPaymentType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Narrow access layer for the HAENGSUNG Oracle ERP tables
 * {@code TB_TRNS_MAST} / {@code TB_TRNS_DETAIL} — the only Oracle objects this
 * feature touches ({@code haengsung-payment-refresh} §2). Reads pending batches
 * and writes back the receive-status columns (§7); it performs no other Oracle
 * writes (never {@code LAST_STATUS}, {@code PROC_STS}, or dispatch columns).
 *
 * <p>Gated on {@code haengsung.oracle.enabled=true} (Step 1 foundation): when
 * the flag is off, the secondary Oracle {@code JdbcTemplate} bean does not exist
 * and neither does this repository — the app boots PostgreSQL-only.
 *
 * <p>Uses the Step-1 secondary {@code JdbcTemplate} bound to the Oracle pool;
 * the primary PostgreSQL datasource is untouched. No credentials are logged.
 */
@Repository
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class HaengsungErpOracleRepository {

    private static final String WEBCASH = "WEBCASH";

    private final JdbcTemplate oracle;

    public HaengsungErpOracleRepository(
            @Qualifier(HaengsungOracleDataSourceConfig.ORACLE_JDBC_TEMPLATE) JdbcTemplate oracle) {
        this.oracle = oracle;
    }

    private static final RowMapper<OracleTransMaster> MASTER_MAPPER = (rs, n) -> new OracleTransMaster(
            rs.getString("FILE_DATE"),
            rs.getString("FILE_NUM"),
            rs.getString("FILE_GB"),
            rs.getString("FILE_NM"),
            rs.getString("BIZ_NO"),
            rs.getString("OUT_BANK_CD"),
            rs.getString("OUT_ACCT_NO"),
            rs.getBigDecimal("REGI_CNT"),
            rs.getBigDecimal("REGI_AMT"),
            rs.getString("ERP_RCV_FLAG"));

    private static final RowMapper<OracleTransDetail> DETAIL_MAPPER = (rs, n) -> new OracleTransDetail(
            rs.getBigDecimal("FILE_SEQ"),
            rs.getBigDecimal("TR_AMT"),
            rs.getString("ISO_CODE"),
            rs.getString("SEND_GBN"),
            rs.getString("ANT_DIV_CD"),
            rs.getString("ACET_NM"),
            rs.getString("TR_ACCT_NO"),
            rs.getString("TR_ACCT_BANK_CD"),
            rs.getString("TR_ACCT_BANK_NM"),
            rs.getString("ACET_ADDR"),
            rs.getString("FORI_FEE_MAN"),
            rs.getString("JEOKYO"),
            rs.getString("ATTRIBUTE2"));

    /**
     * Read pending masters: {@code LAST_STATUS='00'} AND {@code BIZ_NO=<taxId>}
     * AND {@code FILE_DATE BETWEEN fromDate AND toDate}, plus the payment-type
     * predicate ({@code haengsung-payment-refresh} §5.2). The type predicate is
     * a fixed enum-derived SQL fragment (no user text), so it is not an injection
     * surface.
     */
    public List<OracleTransMaster> findMasters(String taxId, String fromDate, String toDate,
                                               HaengsungPaymentType paymentType) {
        String sql = "SELECT FILE_DATE, FILE_NUM, FILE_GB, FILE_NM, BIZ_NO, OUT_BANK_CD, OUT_ACCT_NO, "
                + "REGI_CNT, REGI_AMT, ERP_RCV_FLAG "
                + "FROM TB_TRNS_MAST "
                + "WHERE LAST_STATUS = '00' AND BIZ_NO = ? AND FILE_DATE BETWEEN ? AND ? "
                + typePredicate(paymentType)
                + "ORDER BY FILE_DATE, FILE_NUM";
        return oracle.query(sql, MASTER_MAPPER, taxId, fromDate, toDate);
    }

    /**
     * Read ALL un-received pending masters for a tenant — the scheduled-batch
     * read filter (legacy {@code ERP_0100_002}): {@code LAST_STATUS='00' AND
     * ERP_RCV_FLAG='N' AND BIZ_NO=<taxId>}, no period and no payment-type
     * narrowing (every type; per-master {@code FILE_GB} routing decides the
     * target). The {@code ERP_RCV_FLAG='N'} predicate is what makes the batch
     * idempotent across runs — a master already received ({@code 'Y'}) is never
     * re-pulled. Manual Refresh uses {@link #findMasters} instead (period + type,
     * no {@code ERP_RCV_FLAG} filter).
     */
    public List<OracleTransMaster> findUnreceivedMasters(String taxId) {
        String sql = "SELECT FILE_DATE, FILE_NUM, FILE_GB, FILE_NM, BIZ_NO, OUT_BANK_CD, OUT_ACCT_NO, "
                + "REGI_CNT, REGI_AMT, ERP_RCV_FLAG "
                + "FROM TB_TRNS_MAST "
                + "WHERE LAST_STATUS = '00' AND ERP_RCV_FLAG = 'N' AND BIZ_NO = ? "
                + "ORDER BY FILE_DATE, FILE_NUM";
        return oracle.query(sql, MASTER_MAPPER, taxId);
    }

    /** Read not-yet-imported detail lines ({@code END_GB='0'}) for one master. */
    public List<OracleTransDetail> findDetails(String fileDate, String fileNum) {
        String sql = "SELECT FILE_SEQ, TR_AMT, ISO_CODE, SEND_GBN, ANT_DIV_CD, ACET_NM, TR_ACCT_NO, "
                + "TR_ACCT_BANK_CD, TR_ACCT_BANK_NM, ACET_ADDR, FORI_FEE_MAN, JEOKYO, ATTRIBUTE2 "
                + "FROM TB_TRNS_DETAIL "
                + "WHERE FILE_DATE = ? AND FILE_NUM = ? AND END_GB = '0' "
                + "ORDER BY FILE_SEQ";
        return oracle.query(sql, DETAIL_MAPPER, fileDate, fileNum);
    }

    /**
     * Receive write-back on the master (§7). Sets {@code ERP_RCV_FLAG='Y'} +
     * user/time + {@code IHB_UDT_*}. Deliberately does NOT touch
     * {@code LAST_STATUS} or {@code END_GB} (owned by approval / dispatch).
     * Caller invokes only when {@code ERP_RCV_FLAG != 'Y'}.
     */
    public int markMasterReceived(String fileDate, String fileNum, String nowVn) {
        String sql = "UPDATE TB_TRNS_MAST SET "
                + "ERP_RCV_FLAG = 'Y', ERP_RCV_FLAG_USR = ?, ERP_RCV_FLAG_DT = ?, "
                + "IHB_UDT_USR = ?, IHB_UDT_DT = ? "
                + "WHERE FILE_DATE = ? AND FILE_NUM = ?";
        return oracle.update(sql, WEBCASH, nowVn, WEBCASH, nowVn, fileDate, fileNum);
    }

    /**
     * Receive write-back on a detail line (first import, §7). Sets
     * {@code END_GB='2'}, {@code FLAG='Y'}, {@code IF_SND_*}, {@code FLAG_*},
     * {@code IHB_UDT_*}. Deliberately does NOT touch {@code PROC_STS} /
     * {@code PROC_STS_MSG} (owned by dispatch / approval).
     */
    public int markDetailReceived(String fileDate, String fileNum, java.math.BigDecimal fileSeq, String nowVn) {
        String sql = "UPDATE TB_TRNS_DETAIL SET "
                + "END_GB = '2', FLAG = 'Y', IF_SND_ID = ?, IF_SND_DT = ?, "
                + "FLAG_USR = ?, FLAG_DT = ?, IHB_UDT_USR = ?, IHB_UDT_DT = ? "
                + "WHERE FILE_DATE = ? AND FILE_NUM = ? AND FILE_SEQ = ?";
        return oracle.update(sql, WEBCASH, nowVn, WEBCASH, nowVn, WEBCASH, nowVn, fileDate, fileNum, fileSeq);
    }

    // =====================================================================
    // Approval-status write-back (Step 4 — legacy ErpStatusUpdateHelper)
    // Writes ONLY LAST_STATUS (+ reject-close fields) and the IHB_UDT audit
    // pair on the master, and PROC_STS/END_GB on the detail on rejection.
    // Never touches the receive-flag columns (import) or dispatch-result
    // columns (PROC_STS_MSG / RS_CD / NOR_/ERR_ counts — Step 5).
    // =====================================================================

    /**
     * Non-reject approval status write-back (PENDING=10, APPROVED=20,
     * PROCESSING=51). Sets {@code LAST_STATUS} + the {@code IHB_UDT} audit pair
     * only — no dispatch-result columns.
     */
    public int updateMasterApprovalStatus(String fileDate, String fileNum,
                                          String lastStatus, String nowVn) {
        String sql = "UPDATE TB_TRNS_MAST SET "
                + "LAST_STATUS = ?, IHB_UDT_USR = ?, IHB_UDT_DT = ? "
                + "WHERE FILE_DATE = ? AND FILE_NUM = ?";
        return oracle.update(sql, lastStatus, WEBCASH, nowVn, fileDate, fileNum);
    }

    /**
     * Reject write-back on the master (LAST_STATUS=32). Per spec §6 the rejected
     * batch is also closed as "unable": {@code END_GB='1'}, {@code END_DATETIME},
     * {@code BUL_CNT}=total count, {@code BUL_AMT}=total amount, plus the
     * {@code IHB_UDT} audit pair.
     */
    public int updateMasterRejected(String fileDate, String fileNum, String nowVn,
                                    java.math.BigDecimal bulCnt, java.math.BigDecimal bulAmt) {
        String sql = "UPDATE TB_TRNS_MAST SET "
                + "LAST_STATUS = '32', IHB_UDT_USR = ?, IHB_UDT_DT = ?, "
                + "END_GB = '1', END_DATETIME = ?, BUL_CNT = ?, BUL_AMT = ? "
                + "WHERE FILE_DATE = ? AND FILE_NUM = ?";
        return oracle.update(sql, WEBCASH, nowVn, nowVn, bulCnt, bulAmt, fileDate, fileNum);
    }

    /**
     * Reject write-back on a detail line: {@code PROC_STS='XXXX'},
     * {@code END_GB='3'} (송금의뢰취소 / request-cancelled), {@code IHB_UDT_DT},
     * {@code TR_DATE}=today. (PROC_STS here is the reject marker, not a
     * dispatch-result value — spec §6.)
     */
    public int markDetailRejected(String fileDate, String fileNum, java.math.BigDecimal fileSeq,
                                  String nowVn, String today) {
        String sql = "UPDATE TB_TRNS_DETAIL SET "
                + "PROC_STS = 'XXXX', END_GB = '3', IHB_UDT_DT = ?, TR_DATE = ? "
                + "WHERE FILE_DATE = ? AND FILE_NUM = ? AND FILE_SEQ = ?";
        return oracle.update(sql, nowVn, today, fileDate, fileNum, fileSeq);
    }

    // =====================================================================
    // Dispatch-result write-back (Step 5 — legacy ERP_0100_004)
    // Writes the FINAL result onto the Oracle batch: master close-out
    // (LAST_STATUS='52', END_GB='1', counts/amounts) and per-line result
    // (END_GB '8'/'5', PROC_STS, PROC_STS_MSG, TR_DATE). Keyed by the imported
    // ERP reference (FILE_DATE/FILE_NUM/FILE_SEQ) — never amount/account.
    // Never touches the receive-flag columns (import) or the approval-only
    // columns (LAST_STATUS 10/20/32, reject markers — Step 4).
    // =====================================================================

    /**
     * Master close-out on dispatch completion (legacy {@code updateTbTrnsMstr}).
     * Sets {@code LAST_STATUS='52'} (saf 처리완료 / executed), {@code END_GB='1'}
     * (complete), {@code END_DATETIME}, the success/error counts + amounts, and
     * the {@code IHB_UDT} audit pair.
     *
     * <p>Guarded {@code WHERE LAST_STATUS='51'} — only batches the approval flow
     * (Step 4) already moved to "processing" are closed (legacy IdoUtils dynamic
     * {@code "and last_status = '51'"}). Returns the rows updated: {@code 0} when
     * the master is not at {@code '51'} (already closed / not yet processing) —
     * the caller treats {@code 0} as a benign no-op, not a failure, exactly like
     * legacy (which ignores the row count and proceeds).
     */
    public int updateMasterDispatchResult(String fileDate, String fileNum, String nowVn,
                                          String endDatetime,
                                          java.math.BigDecimal norCnt, java.math.BigDecimal norAmt,
                                          java.math.BigDecimal errCnt, java.math.BigDecimal errAmt) {
        String sql = "UPDATE TB_TRNS_MAST SET "
                + "LAST_STATUS = '52', IHB_UDT_USR = ?, IHB_UDT_DT = ?, "
                + "END_GB = '1', END_DATETIME = ?, "
                + "NOR_CNT = ?, NOR_AMT = ?, ERR_CNT = ?, ERR_AMT = ? "
                + "WHERE FILE_DATE = ? AND FILE_NUM = ? AND LAST_STATUS = '51'";
        return oracle.update(sql, WEBCASH, nowVn, endDatetime, norCnt, norAmt, errCnt, errAmt,
                fileDate, fileNum);
    }

    /**
     * Per-line dispatch-result write-back (legacy {@code updateTbTrnsDetail}).
     * {@code END_GB} = {@code '8'} (송금완료 / success) or {@code '5'} (송금의뢰오류 /
     * fail); {@code PROC_STS} = the bank response code (or the {@code '0999'}
     * override when {@code PROC_STAT='3'}); {@code PROC_STS_MSG} = the response
     * message (null on the file dispatch path); {@code TR_DATE}; {@code IHB_UDT_DT}.
     * Deliberately does NOT touch {@code ERP_RCV_FLAG} / {@code IF_SND_*} /
     * {@code FLAG} (import receive columns) or the approval reject markers.
     */
    public int updateDetailDispatchResult(String fileDate, String fileNum, java.math.BigDecimal fileSeq,
                                          String endGb, String procSts, String procStsMsg,
                                          String nowVn, String trDate) {
        String sql = "UPDATE TB_TRNS_DETAIL SET "
                + "END_GB = ?, PROC_STS = ?, PROC_STS_MSG = ?, IHB_UDT_DT = ?, TR_DATE = ? "
                + "WHERE FILE_DATE = ? AND FILE_NUM = ? AND FILE_SEQ = ?";
        return oracle.update(sql, endGb, procSts, procStsMsg, nowVn, trDate, fileDate, fileNum, fileSeq);
    }

    /**
     * Payment-type → Oracle predicate fragment (trailing space). Values are
     * fixed literals — never interpolated user input.
     */
    private static String typePredicate(HaengsungPaymentType type) {
        return switch (type) {
            case OVERSEAS -> "AND FILE_GB = 'AF1' ";
            case PAYROLL  -> "AND FILE_GB = 'AC2' ";
            // M-2 (UNRESOLVED): legacy splits Expense by FILE_NM LIKE 'E%'/'e%'.
            // Sample data contradicts this (all AB0 FILE_NM are <date>-<num>), so
            // under real data this predicate may select nothing. Kept verbatim to
            // legacy pending PO/ERP confirmation — see spec §17 M-2.
            case EXPENSE  -> "AND (FILE_NM LIKE 'E%' OR FILE_NM LIKE 'e%') ";
            case VENDOR   -> "AND FILE_GB NOT IN ('AF1','AC2') "
                           + "AND FILE_NM NOT LIKE 'E%' AND FILE_NM NOT LIKE 'e%' ";
            case ALL      -> "";
        };
    }
}
