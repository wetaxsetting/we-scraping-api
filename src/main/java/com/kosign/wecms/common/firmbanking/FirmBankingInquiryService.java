package com.kosign.wecms.common.firmbanking;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.dispatch.recipient.DepositorInquiryService;
import com.kosign.wecms.domain.payment.dispatch.recipient.RecipientLookupCommand;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * <p><b>Status today (C1, wired):</b> the {@code TB_FIRM_CONF} gate
 * (step 1) IS implemented &mdash; absence of an active row makes this
 * method return {@code Optional.empty()} just like legacy. The bank
 * call (steps 2-5) is now wired via the <b>REST</b> path: when a
 * firm-config row exists and {@code wecms.dispatch.recipient-inquiry=true},
 * this method builds a {@link RecipientLookupCommand} and delegates to
 * {@link DepositorInquiryService}, which runs WGVN_EFS_201 (the REST
 * refactor of the legacy {@code WB_00000100} socket call) plus the
 * {@code TB_FIRM_TRAN} C001/U002 audit lifecycle. The legacy fixed-width
 * <i>socket</i> protocol is deliberately not ported &mdash; the gateway
 * is REST.
 *
 * <p><b>Master gate:</b> {@code wecms.dispatch.recipient-inquiry}
 * defaults {@code false}, so until VAN keys (B2) + EFS (B1) are
 * provisioned this method stays the no-op skip (returns empty, writes
 * nothing) &mdash; the same behaviour as legacy's
 * &quot;{@code contactInfo == null}&quot; branch
 * ({@code PymnHelper.ckRecName} line 1037) and the established
 * {@code wecms.dispatch.realtime-dispatch} gating pattern. Any gateway
 * or infrastructure error is recorded as a FAIL audit row and degrades
 * to {@code Optional.empty()} &mdash; callers never error out.
 *
 * <p><b>Deferred:</b> the legacy UAT Shinhan/Woori product-type
 * override on {@code outBankCd} / {@code bankPrtnId}
 * ({@code PymnHelper.java:916-946}) is not implemented &mdash;
 * local-only R&amp;D has no UAT environment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirmBankingInquiryService {

    private final FirmBankingSettingRepository firmBankingSettingRepo;
    private final DepositorInquiryService depositorInquiryService;
    /** M — UAT-only Shinhan/Woori BANK_CD/PRTN_ID override (legacy PymnHelper:917-947). */
    private final UatShinhanWooriOverride uatOverride;

    /**
     * Master gate for the live WGVN_EFS_201 path (C1). Default {@code false} →
     * {@code inquiryAccount} stays the no-op stub (returns empty, writes nothing)
     * so the refactor never co-writes {@code TB_FIRM_TRAN} rows on a shared live DB
     * until VAN keys (B2) + EFS (B1) are provisioned. Mirrors the
     * {@code wecms.dispatch.realtime-dispatch} gating pattern.
     */
    @Value("${wecms.dispatch.recipient-inquiry:false}")
    private boolean recipientInquiryEnabled;

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
     * Whether the live WGVN_EFS_201 recipient-inquiry path is switched on
     * (the C1 master gate {@code wecms.dispatch.recipient-inquiry}). This flag
     * has <b>no legacy counterpart</b> — legacy always runs the inquiry. Excel
     * upload parsers consult it so that, when the feature is globally disabled,
     * they skip recipient-name verification entirely instead of flagging every
     * NAPAS row (which would happen if the always-empty inquiry result were
     * treated as a failed name match). When enabled, parsers mirror legacy
     * {@code PymnHelper.ckRecName} exactly.
     */
    public boolean isRecipientInquiryEnabled() {
        return recipientInquiryEnabled;
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

        // Master gate (C1): until recipient-inquiry is switched on the method
        // stays the legacy no-op skip — callers get a clean empty Optional and
        // nothing is written to TB_FIRM_TRAN.
        if (!recipientInquiryEnabled) {
            log.debug("inquiryAccount: recipient-inquiry disabled "
                    + "(wecms.dispatch.recipient-inquiry=false); skipping");
            return Optional.empty();
        }

        // Per-tenant firm-config gate — legacy comm_1003_001_act.jsp
        // queries TB_FIRM_CONF first; when contactInfo is null it
        // silently skips the inquiry. We mirror that here so a tenant
        // without firm-banking configured gets a clean empty Optional.
        Optional<TbFirmConf> firmConf = lookupFirmConfig();
        if (firmConf.isEmpty()) {
            log.debug("inquiryAccount: no active TB_FIRM_CONF row for tenant; skipping inquiry");
            return Optional.empty();
        }
        TbFirmConf conf = firmConf.get();

        // Build the WGVN_EFS_201 inquiry context from the active firm-config row,
        // then delegate to the depositor-inquiry orchestrator (EFS 201 + the
        // TB_FIRM_TRAN C001/U002 audit lifecycle). The REST WGVN_EFS_201 variant
        // replaces the legacy WB_00000100 socket call.
        //
        // M — legacy UAT Shinhan/Woori product-type override on BANK_CD / PRTN_ID
        // (PymnHelper.java:917-947). Under the UAT profile only, for a Shinhan/Woori
        // target bank the outbound PRTN_ID is swapped by product type (F/H); every
        // other profile / bank / product type passes the firm-config values through
        // verbatim. Isolated in UatShinhanWooriOverride — request-only, no DB write.
        UatShinhanWooriOverride.Override ov = uatOverride.resolve(
                napasBankCd, TenantContext.currentPrdtType(),
                conf.getOutBankCd(), conf.getBankPrtnId());
        RecipientLookupCommand cmd = new RecipientLookupCommand(
                conf.getVanOrgCd(),    // ID (VAN_ORG_CD)
                ov.bankCd(),           // BANK_CD (OUT_BANK_CD; UAT override else firm-config)
                ov.prtnId(),           // PRTN_ID (BANK_PRTN_ID; UAT override else firm-config)
                napasBankCd,           // TAR_BANK_CD
                "A",                   // TAR_FINC_MDA_DIV — account (CITAD rejected upstream)
                trimmed,               // TAR_FINC_MDA_NO
                conf.getWdrlActNo());  // WITHD_ACCT_NO

        return depositorInquiryService.inquire(TenantContext.currentBizKey(), cmd);
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
