package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsRequest;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsResponse;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
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
 * {@code fnsh_0101_001_c001} insert flow at
 * {@code WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0218_nxisas.ts:1188}.
 *
 * <p>Spike scope: writes the {@code ACCT_MSTR} row only. Legacy also
 * writes {@code ACCT_AUTH_INFO} (per-user View/Withdraw authority);
 * that table is not yet ported to local Flyway (PROD active list still
 * carries it). When the auth table lands, extend
 * {@link #register(RegisterAccountsRequest)} to write the matching
 * {@code ACCT_AUTH_INFO} rows in the same transaction.
 *
 * <p>Idempotency: the PK is {@code (BIZ_KEY, BANK_CD, ACCT_NO)} so a
 * second registration of the same account is a no-op (skipped count
 * surfaces on the response). The user can re-pick accounts without
 * fearing duplicate-key errors.
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

    /**
     * Register the picked accounts under the tenant's
     * {@code ACCT_MSTR}. The {@code BANK_CD} is resolved off the
     * credential row identified by {@code credentialSeqNo} — the
     * frontend cannot smuggle in a different bank by sending a
     * crafted payload.
     *
     * @return counts of inserted vs. already-existing rows
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

        String nowDtm = LocalDateTime.now(ZoneOffset.UTC).format(DTM14);

        int inserted = 0;
        int skipped = 0;
        List<AcctMstr> toSave = new ArrayList<>(req.accounts().size());
        for (RegisterAccountsRequest.AccountEntry e : req.accounts()) {
            String acctNo = e.accountNo() == null ? null : e.accountNo().trim();
            if (acctNo == null || acctNo.isBlank()) {
                continue;
            }
            // PK uniqueness — second registration of the same account
            // is a no-op (matches the legacy contract; ACCT_MSTR's PK
            // would otherwise throw on the duplicate row).
            if (acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(bizKey, bankCd, acctNo).isPresent()) {
                skipped++;
                continue;
            }
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
            toSave.add(row);
            inserted++;
        }

        if (inserted == 0 && skipped > 0) {
            // Everything the user picked was already registered. The
            // frontend would otherwise show a misleading success; raise
            // a business error so the dialog can render an "all dup"
            // message and the chain stays open.
            throw new BusinessException("WBE_BANK_ACCOUNT_REGISTER_ALL_DUP");
        }
        if (!toSave.isEmpty()) {
            acctMstrRepo.saveAll(toSave);
        }
        log.info("ACCT_MSTR register [bizKey={}, bankCd={}, credSeqNo={}, inserted={}, skipped={}]",
                bizKey, bankCd, req.credentialSeqNo(), inserted, skipped);
        return new RegisterAccountsResponse(inserted, skipped);
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
