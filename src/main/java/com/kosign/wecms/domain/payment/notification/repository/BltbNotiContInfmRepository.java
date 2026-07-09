package com.kosign.wecms.domain.payment.notification.repository;

import com.kosign.wecms.domain.payment.notification.entity.BltbNotiContInfm;
import com.kosign.wecms.domain.payment.notification.entity.BltbNotiContInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@code BLTB_NOTI_CONT_INFM} (per-language notification content). */
public interface BltbNotiContInfmRepository extends JpaRepository<BltbNotiContInfm, BltbNotiContInfmId> {
}
