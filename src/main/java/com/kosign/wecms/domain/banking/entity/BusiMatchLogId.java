package com.kosign.wecms.domain.banking.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link BusiMatchLog} — mirrors legacy
 * {@code busi_match_log} PK {@code (BIZ_KEY, MATCH_LOG_SEQ)}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BusiMatchLogId implements Serializable {

    private String bizKey;
    private String matchLogSeq;
}
