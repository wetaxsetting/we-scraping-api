package com.kosign.wecms.domain.setting.contacts.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link TrcoAddrInfm}. Column order
 * {@code (TRCO_KEY, BIZ_KEY, TRCO_ADDR_KEY)} mirrors DEV's
 * {@code pk_trco_addr_infm} verbatim.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrcoAddrInfmId implements Serializable {

    private String trcoKey;
    private String bizKey;
    private String trcoAddrKey;
}
