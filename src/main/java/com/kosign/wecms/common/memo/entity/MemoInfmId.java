package com.kosign.wecms.common.memo.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Composite PK for {@link MemoInfm} — {@code (BIZ_KEY, WEB_SVC_ID, SEQ_NO)}.
 * Mirrors the legacy 3-tuple — the same memo namespace is shared
 * across every parent type, scoped by {@code WEB_SVC_ID}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MemoInfmId implements Serializable {

    private String     bizKey;
    private String     webSvcId;
    private BigDecimal seqNo;
}
