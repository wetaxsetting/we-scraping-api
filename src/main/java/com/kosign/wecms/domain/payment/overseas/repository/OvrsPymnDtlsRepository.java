package com.kosign.wecms.domain.payment.overseas.repository;

import com.kosign.wecms.domain.payment.entity.OvrsPymnDtls;
import com.kosign.wecms.domain.payment.entity.OvrsPymnDtlsId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OvrsPymnDtlsRepository extends JpaRepository<OvrsPymnDtls, OvrsPymnDtlsId> {

    java.util.List<OvrsPymnDtls> findByBizKeyAndOvrsPymnNoOrderByTrnSeqAsc(
            String bizKey, String ovrsPymnNo);

    java.util.Optional<OvrsPymnDtls> findByBizKeyAndOvrsPymnNoAndTrnSeq(
            String bizKey, String ovrsPymnNo, Integer trnSeq);
}
