package com.kosign.wecms.domain.einvoice.repository;

import com.kosign.wecms.domain.einvoice.entity.TrcoRspbInfm;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@code TRCO_RSPB_INFM} (e-Invoice counterparty
 * responsible-person rows). Tenant scope is the caller's responsibility
 * (constrain on {@code BIZ_KEY}).
 */
public interface TrcoRspbInfmRepository extends JpaRepository<TrcoRspbInfm, String> {

    /** Legacy {@code getListTrcoRspbInfm} existence check
     *  ({@code WHERE biz_key = ? AND trco_key = ?}) &mdash; c002 creates a
     *  responsible-person row for an existing contact only when none exists. */
    boolean existsByBizKeyAndTrcoKey(String bizKey, String trcoKey);
}
