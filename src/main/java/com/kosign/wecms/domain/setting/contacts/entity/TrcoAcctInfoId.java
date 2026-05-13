package com.kosign.wecms.domain.setting.contacts.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link TrcoAcctInfo}. Column order
 * {@code (BIZ_KEY, TRCO_KEY, TRCO_ACCT_KEY)} mirrors DEV's
 * {@code trco_acct_info_pk} verbatim &mdash; deliberately different
 * column ordering from {@link TrcoAddrInfmId} / {@link TrcoOvrsInfmId}
 * which start with {@code TRCO_KEY}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrcoAcctInfoId implements Serializable {

    private String bizKey;
    private String trcoKey;
    private String trcoAcctKey;
}
