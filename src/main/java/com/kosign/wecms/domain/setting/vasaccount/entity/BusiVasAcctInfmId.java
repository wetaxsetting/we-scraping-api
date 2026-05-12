package com.kosign.wecms.domain.setting.vasaccount.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link BusiVasAcctInfm}. Mirrors the legacy
 * {@code wabooks.entity.BusiVasAcctInfmPk} — same column pair
 * ({@code BIZ_KEY}, {@code VAS_ACCT_SEQ_NO}), same uniqueness guarantee
 * from the DEV {@code PK_BUSI_VAS_ACCT_INFM} primary key.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BusiVasAcctInfmId implements Serializable {

    private String bizKey;

    private Long vasAcctSeqNo;
}
