package com.kosign.wecms.domain.setting.contacts.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link PgroBsacLnknInfm}. Column order
 * {@code (BIZ_KEY, USER_ID, GRP_CD, TRCO_KEY)} mirrors DEV's
 * {@code pgro_bsac_lnkn_infm_pk} verbatim.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PgroBsacLnknInfmId implements Serializable {

    private String bizKey;
    private String userId;
    private String grpCd;
    private String trcoKey;
}
