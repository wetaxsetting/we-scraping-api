package com.kosign.wecms.domain.setting.contacts.repository;

import com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data repository for {@link TrcoAcctInfo}. Tenant-scoped on
 * every read / write.
 */
public interface TrcoAcctInfoRepository
        extends JpaRepository<TrcoAcctInfo, TrcoAcctInfoId> {

    List<TrcoAcctInfo> findByBizKeyAndTrcoKey(String bizKey, String trcoKey);

    List<TrcoAcctInfo> findByBizKeyAndTrcoKeyIn(
            String bizKey, Collection<String> trcoKeys);

    @Transactional
    long deleteByBizKeyAndTrcoKeyAndTrcoAcctKey(
            String bizKey, String trcoKey, String trcoAcctKey);
}
