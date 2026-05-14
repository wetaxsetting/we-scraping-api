package com.kosign.wecms.domain.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Daily Cash report — per-account detail row. Mirrors legacy
 * {@code DAILY_CASH_REPT_DAILY_SUMR_DETL} column-for-column (PROD active
 * table, V100 seed pending — this read-only feature uses existing rows).
 */
@Entity
@Table(name = "DAILY_CASH_REPT_DAILY_SUMR_DETL")
@IdClass(DailyCashReptDailySumrDetl.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class DailyCashReptDailySumrDetl {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "REF_DATE", length = 8, nullable = false)
    private String refDate;

    @Id
    @Column(name = "ACCT_TYPE_CD", length = 2, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String acctTypeCd;

    @Id
    @Column(name = "CCY_CD", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String ccyCd;

    @Id
    @Column(name = "BANK_CD", length = 20, nullable = false)
    private String bankCd;

    @Id
    @Column(name = "ACCT_NO", length = 100, nullable = false)
    private String acctNo;

    @Column(name = "BAL", precision = 20, scale = 6, nullable = false)
    private BigDecimal bal;

    @Column(name = "LOAN_NM", length = 100)
    private String loanNm;

    @Column(name = "EXPI_DT", length = 8)
    private String expiDt;

    @Column(name = "SND_CNT", precision = 20, scale = 6, nullable = false)
    private BigDecimal sndCnt;

    @Column(name = "SND_AMT", precision = 20, scale = 6, nullable = false)
    private BigDecimal sndAmt;

    @Column(name = "RCV_CNT", precision = 20, scale = 6, nullable = false)
    private BigDecimal rcvCnt;

    @Column(name = "RCV_AMT", precision = 20, scale = 6, nullable = false)
    private BigDecimal rcvAmt;

    @Column(name = "AGMT_AMT", precision = 20, scale = 6)
    private BigDecimal agmtAmt;

    @Column(name = "LST_COLL_DATE", length = 14)
    private String lstCollDate;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private String bizKey;
        private String refDate;
        private String acctTypeCd;
        private String ccyCd;
        private String bankCd;
        private String acctNo;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(bizKey, pk.bizKey)
                    && Objects.equals(refDate, pk.refDate)
                    && Objects.equals(acctTypeCd, pk.acctTypeCd)
                    && Objects.equals(ccyCd, pk.ccyCd)
                    && Objects.equals(bankCd, pk.bankCd)
                    && Objects.equals(acctNo, pk.acctNo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bizKey, refDate, acctTypeCd, ccyCd, bankCd, acctNo);
        }
    }
}
