package com.kosign.wecms.domain.banking.bankcredentials.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.dto.BankCredentialCreateRequest;
import com.kosign.wecms.domain.banking.bankcredentials.dto.BankCredentialListResponse;
import com.kosign.wecms.domain.banking.bankcredentials.dto.BankCredentialRow;
import com.kosign.wecms.domain.banking.bankcredentials.dto.BankCredentialUpdateRequest;
import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BankCredentialService {

    /** Legacy {@code yyyyMMddHHmmss} audit-column shape. */
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String CERT_INFO_DV_BANK = "1";

    private final BankCertInfoRepository repository;
    private final com.kosign.wecms.domain.banking.repository.AcctMstrRepository acctMstrRepository;
    private final BankCertCryptoService crypto;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    @Transactional(readOnly = true)
    public BankCredentialListResponse list() {
        String bizKey = TenantContext.currentBizKey();
        List<BankCredentialRow> items = repository.findByBizKeyOrderBySeqNoAsc(bizKey).stream()
                .map(BankCredentialService::toRow)
                .toList();
        return new BankCredentialListResponse(items);
    }

    @Transactional
    public BankCredentialRow create(BankCredentialCreateRequest req) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        // SEQ_NO via the single wb_get_next_val funnel (V140 #23 — per-tenant MAX+1).
        BigDecimal nextSeq = new BigDecimal(legacyFunctions.nextBankCertSeqNo(bizKey));
        String now = nowDtm14();

        BankCertInfo entity = new BankCertInfo();
        entity.setBizKey(bizKey);
        entity.setSeqNo(nextSeq);
        entity.setBankCd(req.bankCd());
        entity.setCertInfoDv(CERT_INFO_DV_BANK);
        entity.setCertInfoCon(req.username());
        entity.setCertInfoDetlCon(crypto.encrypt(req.password()));
        entity.setRegId(userId);
        entity.setRegDtm(now);
        entity.setModId(userId);
        entity.setModDtm(now);

        return toRow(repository.save(entity));
    }

    @Transactional
    public BankCredentialRow update(BigDecimal seqNo, BankCredentialUpdateRequest req) {
        BankCertInfo entity = loadOrThrow(seqNo);
        if (req.username() != null) entity.setCertInfoCon(req.username());
        if (req.password() != null) entity.setCertInfoDetlCon(crypto.encrypt(req.password()));
        entity.setModId(TenantContext.currentUserId());
        entity.setModDtm(nowDtm14());
        return toRow(repository.save(entity));
    }

    /**
     * Delete one bank credential AND drop the account linkage it owned.
     *
     * <p>Legacy ({@code comm_0218_001_d001_act.jsp}) deletes the
     * {@code BANK_CERT_INFO} row only, leaving every
     * {@code ACCT_MSTR.BANK_CERT_SEQ} that referenced it pointing at a row
     * that no longer exists. That dangling pointer is what surfaced as
     * "Bank login credentials were deleted" on Own Account Transfer with the
     * credential visibly present in the table (re-registering allocates a NEW
     * seq; the accounts kept the old one). It is also a wrong-bank hazard:
     * {@code SEQ_NO} is tenant-global, so a stale pointer can collide with a
     * different bank's future credential.
     *
     * <p>The accounts themselves are <b>kept</b> — they belong to Bank Account
     * Management, not to the credential. Only the pointer is cleared, so
     * {@code NULL} means "not registered against a credential" and
     * re-registering through the Account Picker re-links them
     * ({@link com.kosign.wecms.domain.banking.service.AccountRegistrationService}).
     *
     * <p>Approved divergence from legacy (2026-08-19) — see
     * {@code docs/bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md},
     * including the live-JEX-DB coexistence note.
     */
    @Transactional
    public void delete(BigDecimal seqNo) {
        final BankCertInfo entity = loadOrThrow(seqNo);   // tenant-checked
        final String bizKey = entity.getBizKey();
        repository.delete(entity);

        // Same transaction: the credential and its linkage go together, so a
        // failure here cannot leave the pointer behind.
        final int unlinked = acctMstrRepository.clearBankCertSeq(
                bizKey,
                entity.getSeqNo().longValueExact(),
                TenantContext.currentUserId(),
                nowDtm14());
        if (unlinked > 0) {
            log.info("bank credential seq={} deleted — unlinked {} account(s) (BANK_CERT_SEQ → NULL)",
                    entity.getSeqNo(), unlinked);
        }
    }

    /** Internal accessor for the scraping flow — returns the plaintext
     *  password for one specific credential plus the per-bank extras
     *  the gateway needs (Corporate ID, Login Type, sub-type seq_no).
     *  Never exposed on the wire. Throws if the row belongs to a
     *  different tenant. */
    @Transactional(readOnly = true)
    public DecryptedCredential loadForScraping(BigDecimal seqNo) {
        BankCertInfo entity = loadOrThrow(seqNo);
        return toDecrypted(entity);
    }

    /** Bank-level variant of {@link #loadForScraping(BigDecimal)} for the
     *  Bank Transaction Sync orchestrator. The sync flow picks accounts
     *  by {@code (bank_cd, acct_no)} (the Account Inquiry table's
     *  natural key) — multiple accounts at one bank share the same
     *  registered credential row, so resolving by {@code BANK_CD} is
     *  enough. Throws {@code WBE2050} when no credential is registered
     *  for the bank (matches the by-seq variant; frontend prompts the
     *  user to register via Settings > Login Credentials first).
     *
     *  <p>Returns the lowest {@code SEQ_NO}'s credential when multiple
     *  exist — deterministic, mirrors legacy behaviour (the JEX picker
     *  surfaces the most recent registration but the sync helper
     *  picks the first match it finds in {@code BANK_CERT_INFO}).
     */
    @Transactional(readOnly = true)
    public DecryptedCredential loadForScrapingByBank(String bankCd) {
        String bizKey = TenantContext.currentBizKey();
        BankCertInfo entity = repository.findFirstByBizKeyAndBankCdOrderBySeqNoAsc(bizKey, bankCd)
                .orElseThrow(() -> new BusinessException("WBE2050"));
        return toDecrypted(entity);
    }

    private DecryptedCredential toDecrypted(BankCertInfo entity) {
        // STRICT decrypt for the password: this is the plaintext credential
        // surface forwarded to the NXiSAS engine for scraping/login. A value
        // that doesn't decrypt under the legacy key (corrupt / foreign-cipher
        // blob) yields null rather than the echoed ciphertext, so the engine
        // is never sent an undecryptable value that the bank rejects with an
        // opaque institution error. Re-save the credential via Settings >
        // Login Credentials to cure a null here.
        return new DecryptedCredential(
                entity.getBankCd(),
                entity.getCertInfoCon(),
                crypto.decryptStrict(entity.getCertInfoDetlCon()),
                entity.getCertInfoDetlCon2(),
                entity.getCertInfoDetlCon3(),
                entity.getNxisasGbSeqNo());
    }

    private BankCertInfo loadOrThrow(BigDecimal seqNo) {
        String bizKey = TenantContext.currentBizKey();
        return repository.findByBizKeyAndSeqNo(bizKey, seqNo)
                .orElseThrow(() -> new BusinessException("WBE2050"));
    }

    private static String nowDtm14() {
        return LocalDateTime.now(ZoneOffset.UTC).format(DTM14);
    }

    private static BankCredentialRow toRow(BankCertInfo e) {
        return new BankCredentialRow(
                e.getSeqNo(),
                e.getBankCd(),
                e.getCertInfoCon(),
                e.getAcctListInqStts(),
                e.getAcctListInqMsg(),
                e.getLstAcctListInqDt(),
                e.getRegDtm(),
                e.getModDtm()
        );
    }

    /**
     * Plaintext credential surface for the NXISAS scraping flow.
     * {@code corporateId} maps to {@code CERT_INFO_DETL_CON2}
     * (Agribank's Corporate ID input, etc.); {@code loginType} maps
     * to {@code CERT_INFO_DETL_CON3} (ABBANK/Vietcom Personal OTP
     * selector). Both may be null when the bank doesn't use them.
     * {@code nxisasGbSeqNo} is the sub-type selector (0 = Corporate,
     * 1 = Personal, 2 = VCB DigiBiz, ...); defaults to 0 when null.
     */
    public record DecryptedCredential(
            String     bankCd,
            String     username,
            String     password,
            String     corporateId,
            String     loginType,
            BigDecimal nxisasGbSeqNo) { }
}
