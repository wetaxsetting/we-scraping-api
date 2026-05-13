package com.kosign.wecms.domain.payment.internaltransfer.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ScdulPymnSumr}. Mirrors legacy
 * {@code wabooks.entity.ScdulPymnSumrPk} (a {@code BusiInfmPk}-extending
 * class carrying {@code BIZ_KEY} + {@code SCDUL_PYMN_NO}).
 *
 * <p>Both columns participate in every query — {@code BIZ_KEY} for tenant
 * scoping, {@code SCDUL_PYMN_NO} for the per-tenant batch sequence. The
 * refactor generates {@code SCDUL_PYMN_NO} via
 * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferKeyGenerator}
 * (NOT via the legacy {@code wb_get_next_val} branch — see
 * {@code wecms-api-main/CLAUDE.md} §"DB Function / Legacy Function Policy").
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ScdulPymnSumrId implements Serializable {

    private String bizKey;
    private String scdulPymnNo;
}
