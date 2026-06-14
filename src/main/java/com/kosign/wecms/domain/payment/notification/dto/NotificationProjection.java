package com.kosign.wecms.domain.payment.notification.dto;

import java.math.BigDecimal;

/**
 * JPQL constructor-projection row for {@code BLTB_NOTI_INFM} ⨝
 * {@code BLTB_NOTI_CONT_INFM} (⟕ {@code USER_BUSI_CONN_INFM}). Internal to
 * the repository layer — never escapes onto the wire; the service maps it
 * to {@link NotificationRow}.
 *
 * <p>Replaces the legacy IDO {@code BLTB_NOTI_INFM_R002}'s BLTB-branch
 * projection ({@code IDO.BLTB_NOTI_INFM_R002.xml:61-95}): the localized
 * {@code TITL} / {@code CONT} come from the content INNER JOIN, the sender
 * image from the {@code USER_BUSI_CONN_INFM} LEFT JOIN (legacy correlated
 * subquery at lines 74-77). The General-notification ({@code TYPE='G'})
 * branch is intentionally not represented — those tables are out of the
 * refactor inventory.
 */
public record NotificationProjection(
        String     bltbCd,
        BigDecimal notiSeqNo,
        String     titl,
        String     cont,
        String     sndId,
        String     sndName,
        String     notiJson,
        String     notiWsvc,
        String     delYn,
        String     actvYn,
        String     regDtm,
        String     modDtm,
        /** Raw {@code USER_BUSI_CONN_INFM.USER_IMG} for the sender; null
         *  when the sender has no image row or no stored image. The service
         *  turns a non-blank value into the wire {@code user_img_url}. */
        String     userImg
) { }
