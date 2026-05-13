package com.kosign.wecms.domain.payment.internaltransfer.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ScdulPymnSumrDetl}. Mirrors legacy
 * {@code wabooks.entity.ScdulPymnSumrDetlPk} — three columns
 * ({@code BIZ_KEY} + {@code SCDUL_PYMN_NO} + {@code SCDUL_PYMN_DETL_NO}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ScdulPymnSumrDetlId implements Serializable {

    private String bizKey;
    private String scdulPymnNo;
    private String scdulPymnDetlNo;
}
