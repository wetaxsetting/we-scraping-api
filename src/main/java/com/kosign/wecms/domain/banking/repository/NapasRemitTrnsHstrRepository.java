package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstr;
import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstrId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NapasRemitTrnsHstrRepository extends JpaRepository<NapasRemitTrnsHstr, NapasRemitTrnsHstrId> {

    /** Transfer history rows for a tenant within an inclusive date range, newest first. */
    List<NapasRemitTrnsHstr> findByBizKeyAndRegDtmBetweenOrderByRegDtmDesc(String bizKey, String fromDtm, String toDtm);

    /** Same range, but filtered to one TRNS_STTS code. */
    List<NapasRemitTrnsHstr> findByBizKeyAndTrnsSttsAndRegDtmBetweenOrderByRegDtmDesc(
            String bizKey, String trnsStts, String fromDtm, String toDtm);

    /** Same range, status NOT one specific value (used to express "non-failed only"). */
    List<NapasRemitTrnsHstr> findByBizKeyAndTrnsSttsNotAndRegDtmBetweenOrderByRegDtmDesc(
            String bizKey, String trnsStts, String fromDtm, String toDtm);

    /** No-date-range variant for when the UI does not pass from/to. */
    List<NapasRemitTrnsHstr> findByBizKeyOrderByRegDtmDesc(String bizKey);

    // SEQ_NO allocation now routes through the single
    // LegacyFunctionRepository.nextNapasRemitSeqNo funnel (V140 #22) — the
    // former MAX+1 finder was removed so every wb_get_next_val call goes
    // through the one funnel per CLAUDE.md.
}
