package com.kosign.wecms.domain.payment.notification.repository;

import com.kosign.wecms.domain.payment.notification.entity.BltbNotiInfm;
import com.kosign.wecms.domain.payment.notification.entity.BltbNotiInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/** Repository for {@code BLTB_NOTI_INFM} (per-recipient notification). */
public interface BltbNotiInfmRepository extends JpaRepository<BltbNotiInfm, BltbNotiInfmId> {

    /** Withdraw cleanup — soft-delete ({@code DEL_YN='Y'}) every
     *  notification under a bulletin post (legacy
     *  {@code comm_0192_004_d001} patches {@code BLTB_NOTI_INFM.DEL_YN='Y'},
     *  it does NOT hard-delete; the per-language {@code BLTB_NOTI_CONT_INFM}
     *  rows are left untouched, matching legacy). */
    @Modifying
    @Query("""
           UPDATE BltbNotiInfm n
           SET n.delYn = 'Y', n.modId = :modId, n.modDtm = :modDtm
           WHERE n.bizKey = :bizKey AND n.bltbCd = :bltbCd AND n.postSeqNo = :postSeqNo
           """)
    int softDeleteByPost(@Param("bizKey") String bizKey,
                         @Param("bltbCd") String bltbCd,
                         @Param("postSeqNo") BigDecimal postSeqNo,
                         @Param("modId") String modId,
                         @Param("modDtm") String modDtm);
}
