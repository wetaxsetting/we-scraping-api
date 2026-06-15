package com.kosign.wecms.domain.auth.entity;

import com.kosign.wecms.common.entity.BaseEntity;
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

/**
 * Mirrors legacy wabooksdev.EMPL_INFM column-for-column. Phase 1 only writes
 * the columns required for the bootstrap chain (BIZ_KEY, EMPL_NO, EMPL_NM,
 * CLPH_NO, audit). Other fields stay nullable so V5 / direct legacy COPY
 * remains valid.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "EMPL_INFM")
@IdClass(EmplInfmId.class)
public class EmplInfm extends BaseEntity {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "EMPL_NO", length = 20, nullable = false)
    private String emplNo;

    /** JEX column is the quoted leading-underscore {@code "_empl_nm"}
     *  (varchar(100), nullable); Java field kept as {@code emplNm}. */
    @Column(name = "`_empl_nm`", length = 100)
    private String emplNm;

    @Column(name = "JBCL_NM", length = 100)
    private String jbclNm;

    @Column(name = "DEPT_NM", length = 100)
    private String deptNm;

    @Column(name = "CLPH_NO", length = 100)
    private String clphNo;

    @Column(name = "EML", length = 100)
    private String eml;

    @Column(name = "CMPN_JOIN_DATE", length = 8)
    private String cmpnJoinDate;

    @Column(name = "CMPN_FIRE_DATE", length = 8)
    private String cmpnFireDate;

    @Column(name = "EMPL_RESI_ST", length = 200)
    private String emplResiSt;

    @Column(name = "EMPL_RESI_CITY", length = 200)
    private String emplResiCity;

    @Column(name = "EMPL_RESI_DTL_ADRS", length = 200)
    private String emplResiDtlAdrs;

    @Column(name = "EMPL_RESI_POST_ADRS", length = 200)
    private String emplResiPostAdrs;

    @Column(name = "USER_IMG", length = 500)
    private String userImg;

    @Column(name = "NATN_ID", length = 20)
    private String natnId;

    @Column(name = "BANK_CD", length = 20)
    private String bankCd;

    @Column(name = "ACCT_NO", length = 100)
    private String acctNo;

    @Column(name = "MAPP_SEUP_YN", length = 1, nullable = false)
    private String mappSeupYn;

    @Column(name = "MAPP_NOTI_YN", length = 1, nullable = false)
    private String mappNotiYn;

    @Column(name = "PAYROLL_PAGE_REG_YN", length = 1, nullable = false)
    private String payrollPageRegYn;

    @Column(name = "WABOOKS_EMP_MNG_REG_YN", length = 1, nullable = false)
    private String wabooksEmpMngRegYn;

    @Column(name = "TEL_NO", length = 100)
    private String telNo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "EMPL_NO_AUTO_GEN_YN", length = 1, nullable = false)
    private String emplNoAutoGenYn;

    @Column(name = "R_EMPL_NO", length = 50)
    private String rEmplNo;
}
