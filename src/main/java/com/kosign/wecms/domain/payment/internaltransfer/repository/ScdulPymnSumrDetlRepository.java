package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetlId;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ScdulPymnSumrDetl}. Every query
 * is tenant-scoped on {@code BIZ_KEY}.
 */
public interface ScdulPymnSumrDetlRepository
        extends JpaRepository<ScdulPymnSumrDetl, ScdulPymnSumrDetlId> {

    /** Detail-line lookup by composite PK + tenant. Used by the
     *  Reporting Account update endpoint. */
    Optional<ScdulPymnSumrDetl> findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(
            String bizKey, String scdulPymnNo, String scdulPymnDetlNo);

    /** Lines for one batch, sorted by the caller-supplied {@code Sort}.
     *  Used by the detail endpoint to render the per-line table. */
    List<ScdulPymnSumrDetl> findByBizKeyAndScdulPymnNo(
            String bizKey, String scdulPymnNo, Sort sort);

    /** Bulk delete by batch — used when deleting a draft batch. The
     *  parent SCDUL_PYMN_SUMR row must be deleted in the same
     *  transaction (see InternalTransferService#delete). */
    long deleteByBizKeyAndScdulPymnNo(String bizKey, String scdulPymnNo);
}
