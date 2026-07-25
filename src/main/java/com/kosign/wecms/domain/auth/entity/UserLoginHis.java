package com.kosign.wecms.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "USER_LOGIN_HIS")
@IdClass(UserLoginHisId.class)
public class UserLoginHis {

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    /** Legacy JEX {@code USER_LOGIN_HIS.REG_DTM} is {@code varchar(14)}
     *  {@code yyyyMMddHHmmss} (UTC) — part of the composite PK. Mapped as a
     *  plain {@code String} to match the live DDL column-for-column (the value
     *  is generated UTC-now in {@code LoginHistoryService}, mirroring legacy
     *  {@code UserLoginHisDAO.create} → {@code DateTimeUtils.utcNow()} +
     *  {@code WB_FORMATTER_DTM14}). */
    @Id
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Id
    @Column(name = "REG_UID", length = 50, nullable = false)
    private String regUid;

    @Column(name = "IP_ADDR", length = 100)
    private String ipAddr;

    @Column(name = "LGN_CHNL", length = 3, nullable = false)
    private String lgnChnl;

    @Column(name = "RSLT_CD", length = 10, nullable = false)
    private String rsltCd;

    @Column(name = "RSLT_CONT", length = 200)
    private String rsltCont;

    public static final String RSLT_SUCCESS = "SUCCESS";
    public static final String RSLT_FAIL    = "FAIL";
    /** Legacy parity — logout writes a history row with this result code
     *  (JEX {@code logn_0002_001_r001_act.jsp:52} → {@code entity.setRsltCd("LOGOUT")}). */
    public static final String RSLT_LOGOUT  = "LOGOUT";
    public static final String CHNL_PC      = "PC";
}
