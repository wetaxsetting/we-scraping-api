package com.kosign.wecms.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite key for {@link BusiAdmnLoginHis} — 4-column PK
 * {@code (BIZ_KEY, REG_ID, REG_DTM, LOGIN_UUID)} matching the live JEX DDL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BusiAdmnLoginHisId implements Serializable {
    private String bizKey;
    private String regId;
    /** {@code varchar(14)} {@code yyyyMMddHHmmss} (UTC) — matches the live JEX PK column. */
    private String regDtm;
    private String loginUuid;
}
