package com.kosign.wecms.domain.setting.contacts.repository;

import com.kosign.wecms.domain.setting.contacts.entity.TrcoOvrsInfm;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoOvrsInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data repository for {@link TrcoOvrsInfm}. Tenant-scoped on
 * every read / write.
 */
public interface TrcoOvrsInfmRepository
        extends JpaRepository<TrcoOvrsInfm, TrcoOvrsInfmId> {

    List<TrcoOvrsInfm> findByBizKeyAndTrcoKey(String bizKey, String trcoKey);

    List<TrcoOvrsInfm> findByBizKeyAndTrcoKeyIn(
            String bizKey, Collection<String> trcoKeys);

    @Transactional
    long deleteByBizKeyAndTrcoKeyAndTrcoOvrsKey(
            String bizKey, String trcoKey, String trcoOvrsKey);
}
