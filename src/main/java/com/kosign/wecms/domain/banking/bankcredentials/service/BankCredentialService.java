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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BankCredentialService {

    /** Legacy {@code yyyyMMddHHmmss} audit-column shape. */
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String CERT_INFO_DV_BANK = "01";

    private final BankCertInfoRepository repository;
    private final BankCertCryptoService crypto;

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
        BigDecimal nextSeq = repository.findMaxSeqNo(bizKey).add(BigDecimal.ONE);
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

    @Transactional
    public void delete(BigDecimal seqNo) {
        repository.delete(loadOrThrow(seqNo));
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
        return new DecryptedCredential(
                entity.getBankCd(),
                entity.getCertInfoCon(),
                crypto.decrypt(entity.getCertInfoDetlCon()),
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
