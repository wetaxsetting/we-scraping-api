package com.kosign.wecms.domain.payment.internaltransfer.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Composite identity for {@link InternalTransferKeySeq}. PK is
 * {@code (BIZ_KEY, KEY_DT)} so each tenant maintains its own per-day
 * counter.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class InternalTransferKeySeqId implements Serializable {

    private String bizKey;
    private LocalDate keyDt;
}
