package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileDataId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No custom queries today — the executor inserts one row per detail
 * line and never needs a lookup. Kept as a Spring Data interface so
 * the entity participates in the {@code @EnableJpaRepositories} scan.
 */
public interface TbPayFileDataRepository
        extends JpaRepository<TbPayFileData, TbPayFileDataId> {
}
