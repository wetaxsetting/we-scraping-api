package com.kosign.wecms.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserLoginHisId implements Serializable {
    private String userId;
    /** {@code varchar(14)} {@code yyyyMMddHHmmss} (UTC) — matches the live JEX PK column. */
    private String regDtm;
    private String regUid;
}
