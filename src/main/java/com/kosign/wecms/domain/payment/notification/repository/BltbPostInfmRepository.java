package com.kosign.wecms.domain.payment.notification.repository;

import com.kosign.wecms.domain.payment.notification.entity.BltbPostInfm;
import com.kosign.wecms.domain.payment.notification.entity.BltbPostInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Repository for {@code BLTB_POST_INFM} (request-approval bulletin post). */
public interface BltbPostInfmRepository extends JpaRepository<BltbPostInfm, BltbPostInfmId> {

    /** Resolve the bulletin post backing an approval — used by the
     *  approve/reject notification + comment writers and by withdraw
     *  cleanup to find the {@code POST_SEQ_NO} for an {@code APPR_SEQ_NO}. */
    Optional<BltbPostInfm> findByBizKeyAndBltbCdAndApprSeqNo(
            String bizKey, String bltbCd, String apprSeqNo);
}
