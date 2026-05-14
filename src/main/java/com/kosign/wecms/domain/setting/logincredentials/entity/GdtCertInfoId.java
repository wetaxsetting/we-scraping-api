package com.kosign.wecms.domain.setting.logincredentials.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Composite identity for {@link GdtCertInfo}. Mirrors legacy JEX
 * {@code GdtCertInfoPk}: {@code (BIZ_KEY, SEQ_NO)}. Same shape as
 * {@link com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfoId}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class GdtCertInfoId implements Serializable {
    private String bizKey;
    private BigDecimal seqNo;
}
