package com.kosign.wecms.domain.setting.firmbankinghistory.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link FirmBankingHistoryView}. Views have no
 * real PK in PostgreSQL — this tuple is best-effort uniqueness sufficient
 * for Hibernate's first-level cache. The five-leg discriminator
 * {@code svcType} guarantees no collision between legs; within a leg
 * {@code (bizKey, tarnDtm, seqNo)} matches the natural identity used by
 * the source-table PKs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FirmBankingHistoryViewId implements Serializable {
    private String bizKey;
    private String tarnDtm;
    private String svcType;
    private String seqNo;
}
