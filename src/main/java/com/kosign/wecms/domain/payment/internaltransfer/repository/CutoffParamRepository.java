package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.CutoffParam;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read access to {@code TB_CUTOFF_PARAM}. Phase 2 reads scalar params
 * (e.g. {@code large-txn-threshold}) by key.
 */
public interface CutoffParamRepository extends JpaRepository<CutoffParam, String> {
}
