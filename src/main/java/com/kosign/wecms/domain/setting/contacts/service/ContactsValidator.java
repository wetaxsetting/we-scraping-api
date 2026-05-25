package com.kosign.wecms.domain.setting.contacts.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.setting.contacts.dto.AccountOp;
import com.kosign.wecms.domain.setting.contacts.dto.AddressOp;
import com.kosign.wecms.domain.setting.contacts.dto.ContactCreateRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest;
import com.kosign.wecms.domain.setting.contacts.dto.OverseasOp;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Business-rule validator for Contacts create + update flows.
 *
 * <p>Bean Validation on the request records handles shape (length,
 * required-on-the-wire, regex). This component handles cross-row +
 * tenant-aware checks:
 *
 * <ul>
 *   <li>{@code HAND_TRCO_KEY} duplicate per tenant
 *       ({@link #validateCreate} / {@link #validateUpdate}) &mdash; mirrors
 *       legacy {@code TrcoHelper.validateTrcoHandCdDuplicate}.</li>
 *   <li>{@code TRCO_TAX_ID} duplicate per tenant &mdash; mirrors legacy
 *       {@code TrcoHelper.validateTrcoTaxDuplicate}.</li>
 *   <li>Per-payload duplicate {@code (BANK_NM, ACCT_NO)} on accounts
 *       and {@code (BENE_ACCT_NO, BIC_CODE)} on overseas rows
 *       ({@code WBE_CONTACTS_ACCT_DUP} / {@code WBE_CONTACTS_BENE_DUP})
 *       &mdash; mirrors {@code comm_0184_005_u001_act.jsp:79-92,118-133}.</li>
 *   <li>{@code DISP_ORD} on every upsert address row must be in
 *       {1, 2} (OQ-8 resolved 2026-05-12 &mdash; current/previous
 *       discriminator).</li>
 * </ul>
 *
 * <p><b>Legacy parity note (2026-05-25):</b> the previous
 * &quot;Local contact must carry at least one bank account&quot;
 * rule ({@code WBE_CONTACTS_LOCAL_BANK_REQUIRED}) was retired after a
 * re-read of {@code comm_0184_002_c001_act.jsp:69-80} confirmed legacy
 * does NOT require any account row for Local contacts &mdash; it only
 * validates rows that already carry {@code NAPAS_BANK_CD} or
 * {@code CITAD_BANK_CD}. The half-filled rule (Bean Validation +
 * frontend Zod super-refine) still catches a card with only one of
 * bank / account number. Both Add (dialog) and Excel-import paths
 * now share the same validation surface.
 *
 * <p>The {@code WBE1190} in-use guard ({@code ACCT_TRNS_RCV_PAY_INFM}
 * existence check) runs in {@code ContactsService} where the
 * {@link com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository}
 * is injected &mdash; not here, to keep the validator free of
 * banking-domain coupling.
 */
@Component
@RequiredArgsConstructor
public class ContactsValidator {

    private final TrcoInfmRepository trcoInfmRepository;

    /* =================================================================
     *  Create
     * ================================================================= */

    /**
     * @param req     request body, Bean-Validation already passed
     * @param bizKey  resolved tenant scope (never user-supplied)
     */
    public void validateCreate(ContactCreateRequest req, String bizKey) {
        if (trcoInfmRepository.existsByBizKeyAndHandTrcoKey(bizKey, req.handTrcoKey())) {
            throw new BusinessException("WBE_CONTACTS_HAND_KEY_DUP");
        }
        if (req.trcoTaxId() != null && !req.trcoTaxId().isBlank()
                && trcoInfmRepository.existsByBizKeyAndTrcoTaxId(bizKey, req.trcoTaxId())) {
            throw new BusinessException("WBE_CONTACTS_TAX_ID_DUP");
        }
        checkAccountDuplicates(req.accounts());
        checkOverseasDuplicates(req.overseasBeneficiaries());
        checkUpsertAddressDispOrd(req.addresses());
    }

    /**
     * @deprecated since 2026-05-25 &mdash; legacy parity: Add (dialog)
     * and Excel upload now share the same validation surface, so this
     * is an alias of {@link #validateCreate}. Kept for one MR cycle so
     * the import service caller does not need to flip in the same
     * change. Will be removed when callers are migrated.
     */
    @Deprecated
    public void validateImport(ContactCreateRequest req, String bizKey) {
        validateCreate(req, bizKey);
    }

    /* =================================================================
     *  Update
     * ================================================================= */

    /**
     * @param req       request body, Bean-Validation already passed
     * @param existing  the loaded master row (used for duplicate-checks
     *                  that should skip when the field is unchanged)
     * @param bizKey    resolved tenant scope
     */
    public void validateUpdate(ContactUpdateRequest req, TrcoInfm existing, String bizKey) {
        if (!req.handTrcoKey().equals(existing.getHandTrcoKey())
                && trcoInfmRepository.existsByBizKeyAndHandTrcoKey(bizKey, req.handTrcoKey())) {
            throw new BusinessException("WBE_CONTACTS_HAND_KEY_DUP");
        }
        String newTaxId = req.trcoTaxId();
        if (newTaxId != null && !newTaxId.isBlank()
                && !newTaxId.equals(existing.getTrcoTaxId())
                && trcoInfmRepository.existsByBizKeyAndTrcoTaxId(bizKey, newTaxId)) {
            throw new BusinessException("WBE_CONTACTS_TAX_ID_DUP");
        }
        checkAccountDuplicates(req.accounts());
        checkOverseasDuplicates(req.overseasBeneficiaries());
        checkUpsertAddressDispOrd(req.addresses());
        // Legacy `comm_0184_005_u001_act.jsp:103-116` does not require a
        // Local contact to carry any bank account at update time either —
        // its `validateRequired("BANK_NM")` branch fires only when REC2
        // is fully empty, and the frontend always sends at least one
        // (possibly empty) row, so that branch never trips in practice.
        // Refactor parity drops the rule entirely.
    }

    /* =================================================================
     *  Helpers
     * ================================================================= */

    private static void checkAccountDuplicates(List<AccountOp> accounts) {
        if (accounts == null) return;
        Set<String> seen = new HashSet<>();
        for (AccountOp a : accounts) {
            if (!"upsert".equals(a.op())) continue;
            if (a.bankNm() == null || a.bankNm().isBlank()) continue;
            if (a.acctNo() == null || a.acctNo().isBlank()) continue;
            String key = a.bankNm() + "|" + a.acctNo();
            if (!seen.add(key)) {
                throw new BusinessException("WBE_CONTACTS_ACCT_DUP");
            }
        }
    }

    private static void checkOverseasDuplicates(List<OverseasOp> overseas) {
        if (overseas == null) return;
        Set<String> seen = new HashSet<>();
        for (OverseasOp o : overseas) {
            if (!"upsert".equals(o.op())) continue;
            if (o.beneAcctNo() == null || o.beneAcctNo().isBlank()) continue;
            if (o.bicCode()    == null || o.bicCode().isBlank())    continue;
            String key = o.beneAcctNo() + "|" + o.bicCode();
            if (!seen.add(key)) {
                throw new BusinessException("WBE_CONTACTS_BENE_DUP");
            }
        }
    }

    /** OQ-8 resolved 2026-05-12 &mdash; every upsert address row must
     *  carry a {@code dispOrd} of 1 or 2. Bean Validation already
     *  enforces the range when the value is present; here we additionally
     *  enforce required-on-upsert (which annotations can't express
     *  conditional on a sibling field). */
    private static void checkUpsertAddressDispOrd(List<AddressOp> addresses) {
        if (addresses == null) return;
        for (AddressOp a : addresses) {
            if (!"upsert".equals(a.op())) continue;
            Integer d = a.dispOrd();
            if (d == null || d < 1 || d > 2) {
                throw new BusinessException("WBE_VALIDATION");
            }
        }
    }
}
