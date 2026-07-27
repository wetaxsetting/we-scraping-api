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

    /**
     * Duplicate-settlement guard for the scraping-result intake —
     * mirrors legacy {@code ValidationHelper.validateTrnsStlmId} +
     * {@code trns_0002_001_c001_act.jsp:94-99}: a transfer whose bank
     * settlement id ({@code TRNS_STLM_ID}) already exists and is not yet
     * settled ({@code TRNS_STLM_YN <> 'Y'}) must not be recorded twice.
     * Prevents a double-submit / engine-retry from writing a duplicate
     * {@code NAPAS_REMIT_TRNS_HSTR} row.
     *
     * <p><b>Scope divergence (documented):</b> legacy scans globally on
     * {@code TRNS_STLM_ID}; the refactor also constrains {@code BIZ_KEY}
     * per the mandatory tenant-isolation rule (a bank settlement id
     * belongs to one tenant's account, so the real-world double-submit
     * is same-tenant, and a cross-tenant scan would leak another
     * tenant's row existence). {@code trnsStlmYn} is the settled value
     * to exclude ({@code "Y"}).
     */
    boolean existsByBizKeyAndTrnsStlmIdAndTrnsStlmYnNot(
            String bizKey, String trnsStlmId, String trnsStlmYn);

    // SEQ_NO allocation now routes through the single
    // LegacyFunctionRepository.nextNapasRemitSeqNo funnel (V140 #22) — the
    // former MAX+1 finder was removed so every wb_get_next_val call goes
    // through the one funnel per CLAUDE.md.
}
