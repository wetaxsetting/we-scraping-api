package com.kosign.wecms.domain.setting.bankaccount.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite-key holder for {@link AcctAuthInfo}. PK columns mirror the
 * legacy {@code AcctAuthInfoPk} class: {@code BIZ_KEY + USER_ID +
 * BANK_CD + ACCT_NO + AUTH_TYPE}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AcctAuthInfoId implements Serializable {
    private String bizKey;
    private String userId;
    private String bankCd;
    private String acctNo;
    private String authType;
}
