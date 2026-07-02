package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsEntryUpdate;
import com.kosign.wecms.domain.banking.entity.ReptTrnsHstr;
import com.kosign.wecms.domain.banking.repository.ReptTrnsHstrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write path for the HAENGSUNG Daily Financial editable cells — ports legacy
 * {@code rept_0017_001_u001_act.jsp} → {@code ReptTrnsHstrDAO.patch} → IDO
 * {@code REPT_TRNS_HSTR_U000}.
 *
 * <p>Per-field auto-save (blur/Enter): the frontend PATCHes the changed
 * column(s) for one ledger row; this service loads the row by its composite PK
 * ({@code BIZ_KEY} from the JWT + {@code ACCT_SEQ_NO} from the path), applies
 * the provided fields, stamps {@code MOD_ID}/{@code MOD_DTM}, and updates only
 * the mapped editable columns. No key generation, no schema change — the base
 * table already exists in the refactor baseline; balances stay owned by the
 * read function ({@code fn_get_haengsung_daily_financial_report}).
 */
@Service
@RequiredArgsConstructor
public class DailyFinancialHsWriteService {

    /** Non-breaking space (U+00A0) — stripped from saved values, mirroring
     *  legacy {@code StringUtils.sanitize}. */
    private static final char NBSP = (char) 0x00A0;

    private final ReptTrnsHstrRepository repo;

    /**
     * Apply a per-field edit to one HS ledger row.
     *
     * @param acctSeqNo the row's {@code ACCT_SEQ_NO} (path key)
     * @param update    the field(s) to set; {@code null} = leave unchanged,
     *                  {@code ""} = clear (legacy {@code FIELD_VAL} semantics)
     * @throws BusinessException {@code WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND}
     *                           when no such row exists in the caller's tenant
     */
    @Transactional
    public void updateEntry(String acctSeqNo, DailyFinancialHsEntryUpdate update) {
        final String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) throw new IllegalStateException("missing tenant context");

        ReptTrnsHstr entity = repo.findByBizKeyAndAcctSeqNo(bizKey, acctSeqNo)
                .orElseThrow(() -> new BusinessException("WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND"));

        if (update.reptCd() != null) entity.setReptCd(sanitize(update.reptCd()));
        if (update.pymnCd() != null) entity.setPymnCd(sanitize(update.pymnCd()));
        if (update.descNmEn() != null) entity.setDescNmEn(sanitize(update.descNmEn()));
        if (update.descNmKo() != null) entity.setDescNmKo(sanitize(update.descNmKo()));
        if (update.memo() != null) entity.setMemo(sanitize(update.memo()));

        // MOD_DTM is auto-stamped by BaseEntity#onUpdate; MOD_ID is not, so set
        // it explicitly to the acting user (legacy sets both to loginUser/now).
        entity.setModId(TenantContext.currentUserId());
        repo.save(entity);
    }

    /** Strip non-breaking spaces (legacy {@code StringUtils.sanitize}). An empty
     *  result clears the field (stored as empty string, matching legacy
     *  {@code FIELD_VAL=""} clear semantics). */
    private static String sanitize(String value) {
        return value.replace(NBSP, ' ');
    }
}
