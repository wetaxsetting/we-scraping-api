package com.kosign.wecms.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

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

    @Id
    @Column(name = "REG_DTM", nullable = false)
    private OffsetDateTime regDtm;

    @Id
    @Column(name = "REG_UID", length = 100, nullable = false)
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
    public static final String CHNL_PC      = "PC";
}
