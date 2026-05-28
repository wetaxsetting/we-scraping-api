package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository.TenantUserManagementProjection;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsRequest;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsResponse;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfo;
import com.kosign.wecms.domain.setting.bankaccount.entity.BankAccountAuthType;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the accounts a user picks from the Select Bank Accounts
 * dialog after a successful NXISAS scrape. Mirrors the legacy
 * {@code fnsh_0101_001_c001} UPSERT flow at
 * {@code WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0218_nxisas.ts:1188}.
 *
 * <p><b>Upsert per account</b> — legacy {@code fnsh_0101_001_c001}
 * loops every picked account and does {@code findByPk}: a new account
 * is INSERTed (line 265), an existing one is PATCHed (line 359). It
 * never raises a "duplicate" error. The refactor matches this:
 * <ul>
 *   <li><b>New</b> ({@code (BIZ_KEY, BANK_CD, ACCT_NO)} absent) → INSERT
 *       {@code ACCT_MSTR} + fan out the per-user {@code ACCT_AUTH_INFO}
 *       authority rows ({@link #fanOutAccountAuth}; legacy
 *       {@code fnsh_0101_001_c001:267-297}) so requester + approver
 *       share the account.</li>
 *   <li><b>Existing</b> → UPDATE the picker-editable fields (nickname,
 *       details/{@code CONT}, account type, balance) + re-link
 *       {@code BANK_CERT_SEQ} to the current credential (legacy
 *       {@code :321}) + reactivate ({@code ACTV_YN='Y'}, legacy
 *       {@code :312}). {@code ACCT_AUTH_INFO} is intentionally NOT
 *       re-fanned on update — legacy persists auth rows only on the
 *       new-account branch.</li>
 * </ul>
 *
 * <p>Both branches run in one transaction and report
 * {@code (inserted, updated)} counts; the only business error is the
 * empty-payload guard ({@code WBE_BANK_ACCOUNT_REGISTER_EMPTY}). The
 * earlier skip-existing + {@code WBE_BANK_ACCOUNT_REGISTER_ALL_DUP}
 * behaviour was a refactor-only divergence and has been removed.
 */
@Service
@RequiredArgsConstructor
public class AccountRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(AccountRegistrationService.class);
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** Legacy default when the picker leaves the Account Type
     *  dropdown unset. {@code "03"} = current account in
     *  {@link com.kosign.wecms.common.enums.AcctTypeCd}. */
    private static final String DEFAULT_ACCT_TYPE_CD = "03";

    private final AcctMstrRepository acctMstrRepo;
    private final BankCredentialService credentialService;
    private final AcctAuthInfoRepository acctAuthRepo;
    private final UserBusiConnInfmRepository userBusiRepo;

    /**
     * Register the picked accounts under the tenant's
     * {@code ACCT_MSTR}. The {@code BANK_CD} is resolved off the
     * credential row identified by {@code credentialSeqNo} — the
     * frontend cannot smuggle in a different bank by sending a
     * crafted payload.
     *
     * @return counts of newly-inserted vs. updated rows
     */
    @Transactional
    public RegisterAccountsResponse register(RegisterAccountsRequest req) {
        if (req.accounts() == null || req.accounts().isEmpty()) {
            throw new BusinessException("WBE_BANK_ACCOUNT_REGISTER_EMPTY");
        }
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();

        // Resolve bank_cd from the credential row, server-side. The
        // credential service enforces tenant scope, so a credential
        // belonging to a different tenant surfaces as WBE2050 before
        // we touch any account row.
        DecryptedCredential cred = credentialService.loadForScraping(req.credentialSeqNo());
        String bankCd = cred.bankCd();
        // Re-link every upserted account to the credential that scraped
        // it — legacy fnsh_0101_001_c001 sets BANK_CERT_SEQ on both the
        // new (line 257) and existing (line 321) branches. BANK_CERT_SEQ
        // is NUMERIC(10,0) → Long on the entity.
        Long bankCertSeq = req.credentialSeqNo() == null
                ? null
                : req.credentialSeqNo().longValue();

        String nowDtm = LocalDateTime.now(ZoneOffset.UTC).format(DTM14);

        int inserted = 0;
        int updated = 0;
        List<AcctMstr> newAccounts = new ArrayList<>(req.accounts().size());
        for (RegisterAccountsRequest.AccountEntry e : req.accounts()) {
            String acctNo = e.accountNo() == null ? null : e.accountNo().trim();
            if (acctNo == null || acctNo.isBlank()) {
                continue;
            }
            var existingOpt =
                    acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(bizKey, bankCd, acctNo);
            if (existingOpt.isPresent()) {
                // UPDATE — legacy fnsh_0101_001_c001 existing-account
                // branch (lines 298-359). Patch only the picker-editable
                // fields + re-link the credential + reactivate. REG_ID /
                // REG_DTM are preserved (only MOD_* change). ACCT_AUTH_INFO
                // is NOT touched — legacy persists auth only on insert.
                AcctMstr existing = existingOpt.get();
                existing.setAcctTypeCd(coalesceAcctType(e.acctTypeCd()));
                existing.setAcctNickNm(blankToNull(e.accountNickname()));
                existing.setCont(blankToNull(e.accountDetails()));
                if (e.acctNm() != null && !e.acctNm().isBlank()) {
                    existing.setAcctNm(blankToNull(e.acctNm()));
                }
                if (e.bal() != null) {
                    existing.setBal(e.bal());
                }
                existing.setBankCertSeq(bankCertSeq);
                existing.setActvYn("Y");
                existing.setModId(userId);
                existing.setModDtm(nowDtm);
                acctMstrRepo.save(existing);
                updated++;
                continue;
            }
            // INSERT — new account.
            AcctMstr row = new AcctMstr();
            row.setBizKey(bizKey);
            row.setBankCd(bankCd);
            row.setAcctNo(acctNo);
            row.setAcctTypeCd(coalesceAcctType(e.acctTypeCd()));
            row.setCcyCd(coalesceCcy(e.currency()));
            row.setAcctNickNm(blankToNull(e.accountNickname()));
            row.setAcctNm(blankToNull(e.acctNm()));
            row.setCont(blankToNull(e.accountDetails()));
            row.setBal(e.bal() == null ? BigDecimal.ZERO : e.bal());
            row.setRealAmt(BigDecimal.ZERO);
            row.setBankCertSeq(bankCertSeq);
            // Picker registrations are scraped-not-manual; HAND_REG_YN
            // stays 'N' so the legacy wb_format_acct_nick_nm function
            // routes to the scraped-bank case.
            row.setHandRegYn("N");
            row.setActvYn("Y");
            row.setAcctBalUpdDtm(nowDtm);
            row.setRegId(userId);
            row.setRegDtm(nowDtm);
            row.setModId(userId);
            row.setModDtm(nowDtm);
            newAccounts.add(row);
            inserted++;
        }

        if (!newAccounts.isEmpty()) {
            acctMstrRepo.saveAll(newAccounts);
            // Legacy ACCT_AUTH_INFO fan-out — ONLY for the freshly
            // inserted accounts. Existing accounts keep their permission
            // rows untouched, matching the legacy new-account branch which
            // was the only branch that persisted ACCT_AUTH_INFO.
            fanOutAccountAuth(bizKey, userId, newAccounts, nowDtm);
        }
        log.info("ACCT_MSTR upsert [bizKey={}, bankCd={}, credSeqNo={}, inserted={}, updated={}]",
                bizKey, bankCd, req.credentialSeqNo(), inserted, updated);
        return new RegisterAccountsResponse(inserted, updated);
    }

    /**
     * Restore the legacy {@code fnsh_0101_001_c001:267-297} ACCT_AUTH_INFO
     * fan-out: on a NEW account insert, seed a permission row for EVERY
     * active user in the tenant. {@code USE_YN = 'Y'} for the registering
     * user, {@code 'N'} for everyone else — so the registrant can use the
     * account immediately (including as an Own Account Transfer source,
     * which reads {@code USE_YN='Y'} rows) while other users surface as
     * toggleable in the Settings &gt; Bank Account permission dialog. This
     * is what lets a requester and an approver share the same bank account.
     *
     * <p><b>Refactor adaptation (intentional divergence from legacy):</b>
     * the refactor {@code ACCT_AUTH_INFO} PK carries {@code AUTH_TYPE}
     * (V/W) where the legacy insert left it implicit. We seed BOTH the
     * VIEW and WITHDRAWAL kinds per user so the registrant gains full use
     * and the permission dialog's two kinds both have a row to toggle.
     * Admins short-circuit in Own Account Transfer and are force-selected
     * in the dialog, so their dormant ({@code 'N'}) rows never limit them.
     *
     * <p>Runs inside the caller's {@code @Transactional} so the auth rows
     * are atomic with the {@code ACCT_MSTR} insert (legacy one-transaction
     * parity). Tenant user set comes from the active
     * ({@code DEL_YN='N'}) {@code USER_BUSI_CONN_INFM} links.
     */
    private void fanOutAccountAuth(String bizKey, String registrantId,
                                   List<AcctMstr> newAccounts, String nowDtm) {
        List<TenantUserManagementProjection> users =
                userBusiRepo.findTenantUsersForManagement(bizKey);
        if (users.isEmpty()) {
            return;
        }
        String[] authTypes = {
                BankAccountAuthType.VIEW.code(),
                BankAccountAuthType.WITHDRAWAL.code(),
        };
        List<AcctAuthInfo> authRows = new ArrayList<>(newAccounts.size() * users.size() * 2);
        for (AcctMstr acct : newAccounts) {
            for (TenantUserManagementProjection u : users) {
                String useYn = registrantId.equals(u.getUserId()) ? "Y" : "N";
                for (String authType : authTypes) {
                    AcctAuthInfo row = new AcctAuthInfo();
                    row.setBizKey(bizKey);
                    row.setUserId(u.getUserId());
                    row.setBankCd(acct.getBankCd());
                    row.setAcctNo(acct.getAcctNo());
                    row.setAuthType(authType);
                    row.setUseYn(useYn);
                    row.setRegId(registrantId);
                    row.setRegDtm(nowDtm);
                    row.setModId(registrantId);
                    row.setModDtm(nowDtm);
                    authRows.add(row);
                }
            }
        }
        acctAuthRepo.saveAll(authRows);
        log.info("ACCT_AUTH_INFO fan-out [bizKey={}, registrant={}, accounts={}, users={}, rows={}]",
                bizKey, registrantId, newAccounts.size(), users.size(), authRows.size());
    }

    /** Defaults missing / blank values to {@link #DEFAULT_ACCT_TYPE_CD}
     *  (current account). The picker frequently leaves this blank
     *  because the NXISAS scrape returned no specific type. */
    private static String coalesceAcctType(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_ACCT_TYPE_CD;
        // Whitelist guard — refuse arbitrary input. Legacy stores 02..08.
        String trimmed = raw.trim();
        return switch (trimmed) {
            case "02", "03", "04", "05", "06", "07", "08" -> trimmed;
            default -> DEFAULT_ACCT_TYPE_CD;
        };
    }

    /** Strict CCY guard — must be a non-blank 3-char ISO code. */
    private static String coalesceCcy(String raw) {
        if (raw == null) return "VND";
        String trimmed = raw.trim().toUpperCase();
        return trimmed.isBlank() ? "VND" : trimmed;
    }

    private static String blankToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
