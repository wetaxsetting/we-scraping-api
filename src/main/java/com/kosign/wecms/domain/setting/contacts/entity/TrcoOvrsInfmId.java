package com.kosign.wecms.domain.setting.contacts.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link TrcoOvrsInfm}. Column order
 * {@code (TRCO_KEY, BIZ_KEY, TRCO_OVRS_KEY)} mirrors DEV's
 * {@code pk_trco_oversea_infm} verbatim.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrcoOvrsInfmId implements Serializable {

    private String trcoKey;
    private String bizKey;
    private String trcoOvrsKey;
}
