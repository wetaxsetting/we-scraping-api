package com.kosign.wecms.domain.einvoice.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link TrcoActvHstr} &mdash;
 * {@code (BIZ_KEY, ACTV_SEQ)}. {@code BIZ_KEY} leads the key for tenant
 * scoping; {@code ACTV_SEQ} is the per-tenant activity sequence.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrcoActvHstrId implements Serializable {

    private String bizKey;
    private String actvSeq;
}
