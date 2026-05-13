package com.kosign.wecms.domain.setting.contacts.repository;

import com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data repository for {@link TrcoAddrInfm}. Every method
 * constrains on {@code BIZ_KEY} for tenant safety.
 *
 * <p>The Contacts service batch-fetches addresses for a whole page
 * via {@link #findByBizKeyAndTrcoKeyIn} to avoid an N+1 across the
 * page. Single-contact reads use {@link #findByBizKeyAndTrcoKey}.
 */
public interface TrcoAddrInfmRepository
        extends JpaRepository<TrcoAddrInfm, TrcoAddrInfmId> {

    List<TrcoAddrInfm> findByBizKeyAndTrcoKey(String bizKey, String trcoKey);

    List<TrcoAddrInfm> findByBizKeyAndTrcoKeyIn(
            String bizKey, Collection<String> trcoKeys);

    @Transactional
    long deleteByBizKeyAndTrcoKeyAndTrcoAddrKey(
            String bizKey, String trcoKey, String trcoAddrKey);
}
