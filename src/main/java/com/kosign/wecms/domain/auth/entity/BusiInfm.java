package com.kosign.wecms.domain.auth.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Mirrors legacy wecmsvndevdb.BUSI_INFM column-for-column.
 * Login path only reads BIZ_KEY; the remaining ~40 columns exist so the
 * table can accept a direct copy from wecmsvndevdb without mapping tricks.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUSI_INFM")
public class BusiInfm extends BaseEntity {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Column(name = "CMPN_NM", length = 200, nullable = false)
    private String cmpnNm;

    @Column(name = "CMPN_ENM", length = 200)
    private String cmpnEnm;

    @Column(name = "TAX_ID", length = 40)
    private String taxId;

    @Column(name = "RPPR_NM", length = 100)
    private String rpprNm;

    @Column(name = "TEL_NO", length = 30)
    private String telNo;

    @Column(name = "FXNO", length = 30)
    private String fxno;

    @Column(name = "EML", length = 100)
    private String eml;

    @Column(name = "RPRS_BSUN_POST_ADRS", length = 200)
    private String rprsBsunPostAdrs;

    @Column(name = "RPRS_BSUN_DTL_ADRS", length = 200)
    private String rprsBsunDtlAdrs;

    @Column(name = "RPRS_BSUN_CITY", length = 200)
    private String rprsBsunCity;

    @Column(name = "RPRS_BSUN_ST", length = 200)
    private String rprsBsunSt;

    @Column(name = "CMPN_LOGO", length = 500)
    private String cmpnLogo;

    @Column(name = "JOIN_DT")
    private OffsetDateTime joinDt;

    @Column(name = "FST_JOIN_NM", length = 100)
    private String fstJoinNm;

    @Column(name = "FST_JOIN_TEL", length = 100)
    private String fstJoinTel;

    @Column(name = "FST_USE_DATE")
    private OffsetDateTime fstUseDate;

    @Column(name = "LST_PAY_DATE")
    private LocalDate lstPayDate;

    @Column(name = "USE_DATE_FROM")
    private LocalDate useDateFrom;

    @Column(name = "USE_DATE_TO")
    private LocalDate useDateTo;

    @Column(name = "BUSI_STTS", length = 2)
    private String busiStts;

    @Column(name = "NTNL_CD", length = 2, nullable = false)
    private String ntnlCd;

    @Column(name = "FREE_USE_DATE_FROM")
    private LocalDate freeUseDateFrom;

    @Column(name = "FREE_USE_DATE_TO")
    private LocalDate freeUseDateTo;

    @Column(name = "BANK_CD", length = 20)
    private String bankCd;

    @Column(name = "ACCT_NO", length = 100)
    private String acctNo;

    @Column(name = "CHNL_ID", length = 10)
    private String chnlId;

    @Column(name = "OVERDUE_YN", length = 1)
    private String overdueYn;

    @Column(name = "CANC_DATE")
    private LocalDate cancDate;

    @Column(name = "WIZ_RUN_YN", length = 1)
    private String wizRunYn;

    @Column(name = "CMPN_STAMP", columnDefinition = "TEXT")
    private String cmpnStamp;

    @Column(name = "DEL_YN", length = 1)
    private String delYn;

    @Column(name = "BUSI_NICK_NM", length = 200)
    private String busiNickNm;

    @Column(name = "ALLI_CMPN_CD", length = 20)
    private String alliCmpnCd;

    @Column(name = "ALLI_CMPN_NM", length = 200)
    private String alliCmpnNm;

    @Column(name = "PRDT_TYPE", length = 1)
    private String prdtType;

    @Column(name = "OWAC_NM", length = 200)
    private String owacNm;

    @Column(name = "MEMO", length = 200)
    private String memo;

    @Column(name = "PAID_START_DATE")
    private LocalDate paidStartDate;

    @Column(name = "CMPN_QR_CD_IMG", length = 500)
    private String cmpnQrCdImg;

    @Column(name = "ERP_CD", length = 1)
    private String erpCd;
}
