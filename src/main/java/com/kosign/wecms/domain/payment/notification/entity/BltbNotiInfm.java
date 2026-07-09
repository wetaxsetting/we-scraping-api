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
 * Per-recipient notification row. Mirrors legacy
 * {@code wabooks.entity.BltbNotiInfm} + {@code BLTB_NOTI_INFM}
 * (Flyway {@code V17}). Written by the legacy
 * {@code BltbHelper.createNotificationToApprvr → createPublicNotification}
 * step — one row per approver, with {@code NOTI_WSVC}/{@code NOTI_URL}
 * pointing at the approval-detail screen and {@code NOTI_JSON} carrying
 * the navigation payload.
 *
 * <p>Localized title/content lives in the child {@link BltbNotiContInfm}
 * rows keyed by the same {@code NOTI_SEQ_NO}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BLTB_NOTI_INFM")
@IdClass(BltbNotiInfmId.class)
public class BltbNotiInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "BLTB_CD", length = 15, nullable = false)
    private String bltbCd;

    @Id
    @Column(name = "NOTI_SEQ_NO", nullable = false)
    private BigDecimal notiSeqNo;

    /** Legacy sets this to {@code ""} for approval notifications. */
    @Column(name = "BLTB_NAME", length = 50, nullable = false)
    private String bltbName;

    @Column(name = "SND_ID", length = 100, nullable = false)
    private String sndId;

    @Column(name = "SND_NAME", length = 200, nullable = false)
    private String sndName;

    @Column(name = "RCV_ID", length = 100, nullable = false)
    private String rcvId;

    @Column(name = "RCV_NM", length = 200, nullable = false)
    private String rcvNm;

    /** {@code yyyyMMdd}. */
    @Column(name = "NOTI_DT", length = 8, nullable = false)
    private String notiDt;

    /** {@code HHmmss}. */
    @Column(name = "NOTI_TM", length = 6, nullable = false)
    private String notiTm;

    @Column(name = "NOTI_URL", length = 100)
    private String notiUrl;

    @Column(name = "ACTV_YN", length = 1, nullable = false)
    private String actvYn;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;

    @Column(name = "NOTI_WSVC", length = 100)
    private String notiWsvc;

    @Column(name = "NOTI_JSON", length = 2000)
    private String notiJson;

    /** FK to {@link BltbPostInfm#getPostSeqNo()}. */
    @Column(name = "POST_SEQ_NO")
    private BigDecimal postSeqNo;

    @Column(name = "DEL_YN", length = 1)
    private String delYn;
}
