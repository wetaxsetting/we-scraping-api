package com.kosign.wecms.domain.payment.notification.repository;

import com.kosign.wecms.domain.payment.notification.entity.BltbUserNewPostInfm;
import com.kosign.wecms.domain.payment.notification.entity.BltbUserNewPostInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/** Repository for {@code BLTB_USER_NEW_POST_INFM} (per-approver unread tracker). */
public interface BltbUserNewPostInfmRepository extends JpaRepository<BltbUserNewPostInfm, BltbUserNewPostInfmId> {

    /** Withdraw cleanup — remove every unread-marker under a bulletin post. */
    @Modifying
    @Query("""
           DELETE FROM BltbUserNewPostInfm u
           WHERE u.bizKey = :bizKey AND u.bltbCd = :bltbCd AND u.postSeqNo = :postSeqNo
           """)
    int deleteByPost(@Param("bizKey") String bizKey,
                     @Param("bltbCd") String bltbCd,
                     @Param("postSeqNo") BigDecimal postSeqNo);
}
