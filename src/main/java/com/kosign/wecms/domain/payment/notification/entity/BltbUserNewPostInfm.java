package com.kosign.wecms.domain.payment.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * "New post" tracker — one row per approver, marking the bulletin post
 * as unread for that user. Mirrors legacy
 * {@code wabooks.entity.BltbUserNewPostInfm} + {@code BLTB_USER_NEW_POST_INFM}
 * (Flyway {@code V20}). Written by the legacy
 * {@code createUserNewPostNotifications} step
 * ({@code comm_0192_001_c005_act.jsp:565} / {@code card_0001_005_c001_act.jsp:428}).
 *
 * <p>{@code POST_SEQ_NO} reuses the parent {@link BltbPostInfm#getPostSeqNo()}
 * (FK to the bulletin post), NOT a freshly allocated sequence.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BLTB_USER_NEW_POST_INFM")
@IdClass(BltbUserNewPostInfmId.class)
public class BltbUserNewPostInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "BLTB_CD", length = 15, nullable = false)
    private String bltbCd;

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    @Id
    @Column(name = "POST_SEQ_NO", nullable = false)
    private BigDecimal postSeqNo;

    /** {@code 'N'} = unread (legacy default). CHAR(1) on DEV. */
    @Column(name = "READ_YN", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String readYn;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
