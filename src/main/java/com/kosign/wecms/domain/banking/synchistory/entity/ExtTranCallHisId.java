package com.kosign.wecms.domain.banking.synchistory.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/** Composite PK for {@link ExtTranCallHis} — mirrors the legacy
 *  {@code (SVC_ID, REF_DATE, SEQ_NO)} primary key. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ExtTranCallHisId implements Serializable {
    private String svcId;
    private String refDate;
    private BigDecimal seqNo;
}
