package com.kosign.wecms.common.firmbanking;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Shared seam for the legacy {@code FIRM_PROC.inquiryAccount(...)} call
 * that resolves an account-holder name from the NAPAS bank network.
 *
 * <p><b>Why this exists as its own component:</b> the same lookup is
 * invoked from at least three flows today &mdash;
 * <ol>
 *   <li>Contacts Add / Edit popup &mdash; "Lookup Recipient" button
 *       (legacy {@code comm_0184_002.ts:530-580 lookUpRecipitend}).</li>
 *   <li>Contacts Excel upload preview &mdash; per-row auto-fill for
 *       NAPAS rows (legacy {@code comm_0184_007_r001_act.jsp:287-380
 *       checkRecptName}).</li>
 *   <li>Payment / Transfer flows that resolve a payee against an
 *       arbitrary account number before posting.</li>
 * </ol>
 *
 * <h2>Legacy flow (real bank API path)</h2>
 *
 * Captured from the {@code comm_1003_001_act.jsp} log on
 * {@code test.wecms.vn}:
 *
 * <ol>
 *   <li><b>Per-tenant gate</b> &mdash; query {@code TB_FIRM_CONF}
 *       for {@code (BIZ_KEY = ?, ORG_STAT = '1')}; returns
 *       {@code VAN_ORG_CD}, {@code OUT_BANK_CD}, {@code BANK_PRTN_ID},
 *       {@code WDRL_ACT_NO}. When no active row exists the inquiry
 *       is skipped silently.</li>
 *   <li><b>UAT product-type override</b> for Shinhan
 *       ({@code 970424}) / Woori ({@code 970457}) keyed off
 *       {@link com.kosign.wecms.common.enums.PrdtType} when running
 *       under {@code EnvHelper.isUat()}. Replaces
 *       {@code OUT_BANK_CD} / {@code BANK_PRTN_ID} with WEBC/HASU
 *       partner ids.</li>
 *   <li><b>Build the WB_00000100 inquiry message</b> &mdash; fixed-width
 *       binary payload carrying {@code ID} (VAN_ORG_CD), {@code BANK_CD}
 *       (OUT_BANK_CD), {@code PRTN_ID} (BANK_PRTN_ID),
 *       {@code TAR_BANK_CD} (target NAPAS bank code),
 *       {@code TAR_FINC_MDA_DIV='A'}, {@code TAR_FINC_MDA_NO} (the
 *       account being inquired), {@code WITHD_ACCT_NO} (firm's own
 *       account), {@code BIZ_KEY}, plus the
 *       {@code TR_DV_CD/TK_DV_CD=0000/0100} discriminator.</li>
 *   <li><b>Socket call</b> to the firm-banking gateway at
 *       {@code 127.0.0.1:11000} (resource {@code socket/wecms_batch}).
 *       Synchronous request/response, fixed-width 700-byte frame.</li>
 *   <li><b>Parse response</b> &mdash; the bank returns
 *       {@code FINC_MDA_OWNER_NM} (may be empty even on success),
 *       {@code RC}, {@code RM}. Legacy frontend treats empty owner
 *       name as {@code WBE1143} "Recipient Name not found".</li>
 * </ol>
 *
 * <p><b>Status today:</b> the {@code TB_FIRM_CONF} gate (step 1) IS
 * implemented &mdash; absence of an active row makes this method return
 * {@code Optional.empty()} just like legacy. The socket call (steps
 * 2-5) is <b>NOT IMPLEMENTED</b>: when a firm-config row exists, the
 * method currently logs a warning and returns {@code Optional.empty()}
 * so callers (Excel upload parsers, popup lookup) silently skip name
 * verification &mdash; the same behaviour as legacy's
 * &quot;{@code contactInfo == null}&quot; branch
 * ({@code PymnHelper.ckRecName} line 1037).
 *
 * <p><b>Production work required:</b> replace the
 * &quot;Production socket call&quot; block below with the real
 * {@code WB_00000100} implementation. The public signature, the
 * firm-config gate, and every caller stay untouched &mdash; once the
 * socket lands, recipient-name verification activates automatically
 * across every wired upload parser.
 *
 * <p><b>Workspace memory:</b> {@code project_refactoring_scope.md}
 * states the refactor is local-only R&amp;D with no bank integration;
 * that is why steps 2-5 above stay stubbed today.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirmBankingInquiryService {

    private final FirmBankingSettingRepository firmBankingSettingRepo;

    /**
     * Returns {@code true} when the current tenant has an active
     * {@code TB_FIRM_CONF} row ({@code ORG_STAT = '1'}). Bulk callers
     * (the Excel-upload preview) check this once per request to skip N
     * useless per-row queries when the tenant simply has no firm-banking
     * configured.
     */
    public boolean hasActiveFirmConfig() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) return false;
        return firmBankingSettingRepo.existsByBizKeyAndOrgStat(bizKey, "1");
    }

    /**
     * Resolve the account-holder name for the given NAPAS account.
     *
     * @param napasBankCd  6-char NAPAS bank code (required; CITAD has no
     *                     real-time account-name source and is rejected
     *                     by the legacy popup's {@code showLookup} gate)
     * @param acctNo       account number on the bank side (required)
     * @return name of the account holder when the bank API resolves
     *         it; {@link Optional#empty()} when:
     *         <ul>
     *           <li>blank inputs;</li>
     *           <li>the tenant has no active firm-banking config (legacy
     *               {@code contactInfo == null} branch);</li>
     *           <li>(post-integration) the bank reports an empty owner
     *               name &mdash; frontend treats this as
     *               {@code WBE1143}.</li>
     *         </ul>
     */
    public Optional<String> inquiryAccount(String napasBankCd, String acctNo) {
        // Defensive input checks. Empty napas → CITAD/USD popup branch
        // never should have called this; empty acct → can't inquire.
        if (napasBankCd == null || napasBankCd.isBlank()) return Optional.empty();
        if (acctNo == null) return Optional.empty();
        String trimmed = acctNo.trim();
        if (trimmed.isEmpty()) return Optional.empty();

        // Per-tenant firm-config gate — legacy comm_1003_001_act.jsp
        // queries TB_FIRM_CONF first; when contactInfo is null it
        // silently skips the inquiry. We mirror that here so a tenant
        // without firm-banking configured gets a clean empty Optional
        // instead of a synthetic stub answer (which would otherwise
        // mask the missing-config state when real integration lands).
        Optional<TbFirmConf> firmConf = lookupFirmConfig();
        if (firmConf.isEmpty()) {
            log.debug("inquiryAccount: no active TB_FIRM_CONF row for tenant; skipping inquiry");
            return Optional.empty();
        }

        // ============================================================
        // Production socket call — REPLACE THIS BLOCK with the real
        // implementation. Returns Optional.empty() until the team wires
        // the WB_00000100 socket protocol; meanwhile every caller (Excel
        // upload parsers, popup lookup) silently skips name verification,
        // which matches the legacy "no firm-banking config" branch
        // (PymnHelper.ckRecName line 1037 "contactInfo == null").
        //
        // What this method MUST do per legacy FIRM_PROC.inquiryAccount:
        //   1. Apply UAT product-type override on outBankCd / bankPrtnId
        //      (Shinhan 970424 → WEBC1/HASU1; Woori 970457 →
        //       000001008/000001020). See PymnHelper.java:916-946.
        //   2. Build WB_00000100 fixed-width payload using:
        //        ID         = firmConf.get().getVanOrgCd()
        //        BANK_CD    = outBankCd  (post-UAT-override)
        //        PRTN_ID    = bankPrtnId (post-UAT-override)
        //        TAR_BANK_CD       = napasBankCd
        //        TAR_FINC_MDA_DIV  = 'A'
        //        TAR_FINC_MDA_NO   = trimmed (acctNo)
        //        WITHD_ACCT_NO     = firmConf.get().getWdrlActNo()
        //        BIZ_KEY           = TenantContext.currentBizKey()
        //        TNO               = yyyyMMddHHmmss + UUID
        //        TR_DV_CD / TK_DV_CD = "0000" / "0100"
        //   3. Send via socket (legacy: 127.0.0.1:11000, resource
        //      socket/wecms_batch), receive 700-byte response
        //      (TK_DV_CD echoes back as "0110").
        //   4. Parse FINC_MDA_OWNER_NM. Return Optional.of(name) when
        //      RC == "0000" AND name is non-blank; Optional.empty()
        //      otherwise.
        //
        // See `docs/refactor-wecms/features/payment/recipient-name-inquiry.md`
        // (when written) for the full handoff brief.
        // ============================================================
        log.warn(
                "feature=firm-banking inquiry-account not-implemented: "
                + "TB_FIRM_CONF row found for tenant but WB_00000100 socket "
                + "call has not been wired yet. Returning Optional.empty() "
                + "so callers skip recipient-name verification — production "
                + "must implement the socket call. napasBankCd={} acctNo=(masked)",
                napasBankCd);
        return Optional.empty();
    }

    /**
     * Helper exposed for direct callers (currently
     * {@link com.kosign.wecms.domain.setting.contacts.service.ContactsService#resolveExcelImportRecipients}
     * doesn't need the row itself &mdash; it just gates on
     * {@link #hasActiveFirmConfig()}). When the real socket call lands
     * the row's {@code vanOrgCd} / {@code outBankCd} / {@code bankPrtnId}
     * / {@code wdrlActNo} fields are consumed to build the inquiry
     * message.
     */
    private Optional<TbFirmConf> lookupFirmConfig() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) return Optional.empty();
        return firmBankingSettingRepo
                .findFirstByBizKeyAndOrgStatOrderByRegDttmAsc(bizKey, "1");
    }
}
