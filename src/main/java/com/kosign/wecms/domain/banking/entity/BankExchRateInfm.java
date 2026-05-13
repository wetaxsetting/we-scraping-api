package com.kosign.wecms.domain.banking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Exchange rate record fetched from Coocon (Shinhan Bank) via batch EXCH_0001_001.
 *
 * <p>JEX source: {@code wabooks.entity.BankExchRateInfm}.<br>
 * Table: {@code BANK_EXCH_RATE_INFM} (created in V84 migration).</p>
 *
 * <p>One row = one counter-currency rate for a given business, bank, reference
 * date, and sequence number. The batch inserts seq_no=1 on the first daily run
 * and updates it on subsequent runs of the same day.</p>
 */
@Entity
@Table(name = "BANK_EXCH_RATE_INFM")
@IdClass(BankExchRateInfmId.class)
@Getter
@Setter
@NoArgsConstructor
public class BankExchRateInfm {

    // ── Primary key ──────────────────────────────────────────────────────────

    @Id
    @Column(name = "BIZ_KEY", length = 50, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "BANK_CD", length = 20, nullable = false)
    private String bankCd;

    /** Reference date in YYYYMMDD format. */
    @Id
    @Column(name = "REF_DATE", length = 8, nullable = false)
    private String refDate;

    /** Counter currency code, e.g. USD, EUR, JPY. */
    @Id
    @Column(name = "CCY_CD", length = 10, nullable = false)
    private String ccyCd;

    /** Auto-generated via wb_get_next_val (V25 migration). */
    @Id
    @Column(name = "EXCH_SEQ_NO", length = 10, nullable = false)
    private String exchSeqNo;

    // ── Rate data ────────────────────────────────────────────────────────────

    /** Base currency the rate is quoted against, e.g. KRW. */
    @Column(name = "REF_CCY_CD", length = 10, nullable = false)
    private String refCcyCd;

    @Column(name = "TRAN_SELL_AMT", precision = 20, scale = 6)
    private BigDecimal tranSellAmt;

    @Column(name = "TRAN_BUY_AMT", precision = 20, scale = 6)
    private BigDecimal tranBuyAmt;

    @Column(name = "BUY_AMT", precision = 20, scale = 6)
    private BigDecimal buyAmt;

    @Column(name = "SELL_AMT", precision = 20, scale = 6)
    private BigDecimal sellAmt;

    /** Date the rate data was searched/retrieved — YYYYMMDD. */
    @Column(name = "SRCH_DT", length = 8)
    private String srchDt;

    @Column(name = "BASIC_RATE", precision = 20, scale = 6)
    private BigDecimal basicRate;

    @Column(name = "BASIC_TRAN", precision = 20, scale = 6)
    private BigDecimal basicTran;

    /** Coocon category string, e.g. "COMMON". */
    @Column(name = "CLASS_EXCH", length = 50)
    private String classExch;

    /** Coocon module string, e.g. "GLOBAL/GBL/BANK_Shinhan". */
    @Column(name = "MODULE", length = 100)
    private String module;

    /** Datetime the rate was registered in the source system — YYYYMMDDHHMMSS. */
    @Column(name = "EXCH_RATE_REG_DTM", length = 14)
    private String exchRateRegDtm;

    /** Daily sequence number as a string — "1" for first run, "2" for second, etc. */
    @Column(name = "SEQ_NO", length = 10)
    private String seqNo;

    /** Stand point from Coocon resultData. */
    @Column(name = "STAND_POINT", length = 100)
    private String standPoint;

    /** Last update date from Coocon response — YYYYMMDD. */
    @Column(name = "LAST_UPDATE_DATE", length = 8)
    private String lastUpdateDate;

    /** Last update time from Coocon response — HHMMSS. */
    @Column(name = "LAST_UPDATE_TIME", length = 6)
    private String lastUpdateTime;

    /** ERP send flag — Y/N. */
    @Column(name = "ERP_SND_YN", length = 1, nullable = false)
    private String erpSndYn = "N";

    // ── Audit columns ────────────────────────────────────────────────────────

    @Column(name = "REG_DTM", length = 14)
    private String regDtm;

    @Column(name = "REG_USER", length = 100)
    private String regUser;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;
}
