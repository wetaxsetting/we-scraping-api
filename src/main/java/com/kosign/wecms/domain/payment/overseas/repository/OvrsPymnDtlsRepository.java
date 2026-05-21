package com.kosign.wecms.domain.payment.overseas.repository;

import com.kosign.wecms.domain.payment.entity.OvrsPymnDtls;
import com.kosign.wecms.domain.payment.entity.OvrsPymnDtlsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OvrsPymnDtlsRepository extends JpaRepository<OvrsPymnDtls, OvrsPymnDtlsId> {

    java.util.List<OvrsPymnDtls> findByBizKeyAndOvrsPymnNoOrderByTrnSeqAsc(
            String bizKey, String ovrsPymnNo);

    java.util.Optional<OvrsPymnDtls> findByBizKeyAndOvrsPymnNoAndTrnSeq(
            String bizKey, String ovrsPymnNo, Integer trnSeq);

    @Modifying
    @Query("DELETE FROM OvrsPymnDtls d WHERE d.bizKey = :bizKey AND d.ovrsPymnNo = :ovrsPymnNo")
    int deleteByBizKeyAndOvrsPymnNo(@Param("bizKey") String bizKey, @Param("ovrsPymnNo") String ovrsPymnNo);
}
