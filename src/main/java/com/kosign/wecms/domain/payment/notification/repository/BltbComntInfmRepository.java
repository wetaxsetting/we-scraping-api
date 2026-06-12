package com.kosign.wecms.domain.payment.notification.repository;

import com.kosign.wecms.domain.payment.notification.entity.BltbComntInfm;
import com.kosign.wecms.domain.payment.notification.entity.BltbComntInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/** Repository for {@code BLTB_COMNT_INFM} (approval-action remark rows). */
public interface BltbComntInfmRepository extends JpaRepository<BltbComntInfm, BltbComntInfmId> {

    /** Withdraw cleanup — remove every comment row under a bulletin post
     *  (legacy {@code comm_0192_004_d001} comment purge). */
    @Modifying
    @Query("""
           DELETE FROM BltbComntInfm c
           WHERE c.bizKey = :bizKey AND c.bltbCd = :bltbCd AND c.postSeqNo = :postSeqNo
           """)
    int deleteByPost(@Param("bizKey") String bizKey,
                     @Param("bltbCd") String bltbCd,
                     @Param("postSeqNo") BigDecimal postSeqNo);
}
