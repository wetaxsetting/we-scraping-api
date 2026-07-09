package com.kosign.wecms.domain.banking.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite key for {@link ReptTrnsHstr} — mirrors legacy {@code ReptTrnsHstrPk}
 * ({@code BIZ_KEY} + {@code ACCT_SEQ_NO}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReptTrnsHstrId implements Serializable {
    private String bizKey;
    private String acctSeqNo;
}
