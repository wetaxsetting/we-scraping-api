package com.kosign.wecms.domain.setting.erptransactionhistory.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link ErpTrnsHstr}. Mirrors the legacy
 * {@code wabooks.entity.ErpTrnsHstrPk} JEX class — same column pair
 * ({@code BIZ_KEY}, {@code SEQ_NO}), same uniqueness guarantee from the
 * DEV table's {@code PK_ERP_TRNS_HSTR} primary key.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ErpTrnsHstrId implements Serializable {

    private String bizKey;

    private Long seqNo;
}
