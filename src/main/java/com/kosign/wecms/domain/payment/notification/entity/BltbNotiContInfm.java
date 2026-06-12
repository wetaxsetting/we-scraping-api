package com.kosign.wecms.domain.payment.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Per-language notification content. Mirrors legacy
 * {@code wabooks.entity.BltbNotiContInfm} + {@code BLTB_NOTI_CONT_INFM}
 * (Flyway {@code V16}). Written by the legacy
 * {@code createPublicNotification} content loop — one row per language
 * (EN / KO / local), keyed by the parent {@link BltbNotiInfm}'s
 * {@code NOTI_SEQ_NO}. {@code TITL} = legacy {@code WbMessageCode.WBI1035}
 * ("Added a new approval."), {@code CONT} = "".
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BLTB_NOTI_CONT_INFM")
@IdClass(BltbNotiContInfmId.class)
public class BltbNotiContInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "BLTB_CD", length = 15, nullable = false)
    private String bltbCd;

    @Id
    @Column(name = "NOTI_SEQ_NO", nullable = false)
    private BigDecimal notiSeqNo;

    @Id
    @Column(name = "LANG_CD", length = 2, nullable = false)
    private String langCd;

    @Column(name = "TITL", length = 100)
    private String titl;

    @Column(name = "CONT", length = 200)
    private String cont;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;
}
