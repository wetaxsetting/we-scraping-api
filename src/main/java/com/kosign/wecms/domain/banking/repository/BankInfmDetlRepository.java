package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.BankInfmDetl;
import com.kosign.wecms.domain.banking.entity.BankInfmDetlId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read-only lookup over {@code BANK_INFM_DETL}. Today it serves the
 * date-effective bank <b>short name</b> ({@code BANK_DETL_INFO_CD = '10'}) —
 * the legacy {@code wb_get_bank_nm(bank_cd, 'S')} source.
 */
public interface BankInfmDetlRepository extends JpaRepository<BankInfmDetl, BankInfmDetlId> {

    /**
     * Effective {@code BANK_DETL_INFO_CONT} values for a bank + info code where
     * {@code today} falls inside {@code [START_DATE, END_DATE]} (both compact
     * {@code yyyyMMdd} strings — lexical compare is correct for that format).
     * Newest first (by {@code SEQ_NO}) so the caller takes {@code get(0)}.
     * Mirrors the legacy {@code wb_get_bank_nm(_, 'S')} query.
     */
    @Query("select d.bankDetlInfoCont from BankInfmDetl d "
            + "where d.bankCd = :bankCd and d.bankDetlInfoCd = :infoCd "
            + "and :today between d.startDate and d.endDate "
            + "order by d.seqNo desc")
    List<String> findEffectiveContents(@Param("bankCd") String bankCd,
                                       @Param("infoCd") String infoCd,
                                       @Param("today") String today);
}
