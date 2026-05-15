package com.kosign.wecms.domain.payment.overseas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Refactor-only helper for the overseas-payment batch key generator.
 * Mirrors {@code INTERNAL_TRANSFER_KEY_SEQ} (V85) — independent
 * per-(tenant, day) sequence so overseas and internal-transfer key
 * spaces never collide.
 *
 * <p>Table created by V135. Not present in JEX DEV/PROD.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "OVERSEAS_PAYMENT_KEY_SEQ")
@IdClass(OverseasPaymentKeySeqId.class)
public class OverseasPaymentKeySeq {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "KEY_DT", nullable = false)
    private LocalDate keyDt;

    @Column(name = "LAST_SEQ", nullable = false)
    private Integer lastSeq;

    @Column(name = "REG_DTM", nullable = false)
    private OffsetDateTime regDtm;

    @Column(name = "MOD_DTM", nullable = false)
    private OffsetDateTime modDtm;
}
