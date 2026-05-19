package com.kosign.wecms.domain.setting.logincredentials.service;

import com.kosign.wecms.common.auditlog.entity.AuditLogEntry;
import com.kosign.wecms.common.auditlog.repository.AuditLogEntryRepository;
import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService;
import com.kosign.wecms.domain.banking.nxisas.BankScrapingService;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapeSessionResponse;
import com.kosign.wecms.domain.banking.nxisas.dto.VerifyCredentialsRequest;
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.setting.logincredentials.dto.BankFormInfoResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.BankFormInfoSubType;
import com.kosign.wecms.domain.setting.logincredentials.dto.BankPickerResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.BankPickerRow;
import com.kosign.wecms.domain.setting.logincredentials.repository.BankFormInfoRepository;
import com.kosign.wecms.domain.setting.logincredentials.repository.BankPickerRepository;
import com.kosign.wecms.domain.setting.logincredentials.dto.BankCredentialCreateRequest;
import com.kosign.wecms.domain.setting.logincredentials.dto.BankCredentialUpdateRequest;
import com.kosign.wecms.domain.setting.logincredentials.dto.CredentialCreateResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.GdtCredentialRequest;
import com.kosign.wecms.domain.setting.logincredentials.dto.GdtCredentialUpdateRequest;
import com.kosign.wecms.domain.setting.logincredentials.dto.LoginCredentialAccount;
import com.kosign.wecms.domain.setting.logincredentials.dto.LoginCredentialAuditResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.LoginCredentialAuditRow;
import com.kosign.wecms.domain.setting.logincredentials.dto.LoginCredentialGroup;
import com.kosign.wecms.domain.setting.logincredentials.dto.LoginCredentialListResponse;
import com.kosign.wecms.domain.setting.logincredentials.dto.RevealResponse;
import com.kosign.wecms.domain.setting.logincredentials.entity.GdtCertInfo;
import com.kosign.wecms.domain.setting.logincredentials.repository.GdtCertInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Unified service for the Login Credentials list page (legacy
 * {@code comm_0218_001}; refactor slug {@code "login-credentials"}).
 *
 * <p>Reads both {@code BANK_CERT_INFO} (via the existing
 * {@link BankCertInfoRepository}) and {@code GDT_CERT_INFO} (via
 * {@link GdtCertInfoRepository}), groups bank rows by {@code BANK_CD}
 * to mirror the legacy {@code IDO.BANK_CERT_INFO_R007.xml} shape, and
 * surfaces one credential row per tenant for GDT. No password
 * ciphertext leaves this layer except through the explicit
 * {@link #reveal(String, BigDecimal, String)} path.
 *
 * <p>Tenant scope rides on {@link TenantContext}; the route never
 * carries {@code bizKey}.
 *
 * <p>Delete writes audit-log rows via the shared
 * {@link AuditLogService} with {@code wsvcId = "login-credentials"}
 * — the slug, not the legacy {@code comm_0218_004} page id, per the
 * workspace identifier-naming rule.
 */
@Service
@RequiredArgsConstructor
public class LoginCredentialsService {

    /** Refactor slug — written to {@code AUDIT_LOGS_INFM.WSVC_ID}. */
    public static final String WSVC_ID = "login-credentials";

    public static final String TYPE_BANK = "BANK";
    public static final String TYPE_GDT  = "GDT";

    /** Legacy entity-name strings written to
     *  {@code AUDIT_LOGS_INFM.REF_NO}. Mirror the
     *  {@code comm_0218_001_d001_act.jsp:135,147} call site. */
    private static final String REF_NO_BANK = "BANK_CERT_INFO";
    private static final String REF_NO_GDT  = "GDT_CERT_INFO";

    /** Display name for the single GDT row. */
    private static final String GDT_INSTITUTION_LABEL = "GDT";

    /** {@code CERT_INFO_DV} discriminator for bank rows (legacy "01" =
     *  bank). Mirrors {@code BankCredentialService.CERT_INFO_DV_BANK}
     *  so the two services produce identical row shapes. */
    private static final String CERT_INFO_DV_BANK = "01";

    /** Same discriminator for GDT rows — legacy {@code GdtCertInfoDv}
     *  uses "01" for the standard tax-portal login. */
    private static final String CERT_INFO_DV_GDT = "01";

    /** Legacy {@code yyyyMMddHHmmss} audit-column shape — used for
     *  REG_DTM / MOD_DTM. */
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BankPickerRepository      bankPickerRepo;
    private final BankFormInfoRepository    bankFormInfoRepo;
    private final BankCertInfoRepository    bankRepo;
    private final GdtCertInfoRepository     gdtRepo;
    private final BankInfmRepository        bankInfmRepo;
    private final UserInfmRepository        userRepo;
    private final BankCertCryptoService     crypto;
    private final PasswordEncoder           passwordEncoder;
    private final AuditLogService           auditLogService;
    private final AuditLogEntryRepository   auditLogReadRepo;
    private final BankScrapingService       bankScrapingService;

    /* -----------------------------------------------------------------
     *  LIST — banking groups + single GDT row
     * ----------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public LoginCredentialListResponse list() {
        String bizKey = TenantContext.currentBizKey();

        List<LoginCredentialGroup> out = new ArrayList<>();

        // --- Banking: group BANK_CERT_INFO by BANK_CD ---
        // The legacy R007 UNION pre-aggregates with json_agg; we do the
        // same shape in Java. TreeMap so output order is stable per
        // BANK_CD (the live UI is sorted alphabetically by display name
        // in the table; bank code is a reasonable secondary key).
        Map<String, List<BankCertInfo>> byBank = new TreeMap<>();
        for (BankCertInfo row : bankRepo.findByBizKeyOrderBySeqNoAsc(bizKey)) {
            byBank.computeIfAbsent(row.getBankCd(), k -> new ArrayList<>()).add(row);
        }
        Map<String, String> bankNames = resolveBankNames(byBank.keySet());

        for (Map.Entry<String, List<BankCertInfo>> e : byBank.entrySet()) {
            String bankCd = e.getKey();
            List<BankCertInfo> rows = e.getValue();
            List<LoginCredentialAccount> accounts = rows.stream()
                    .map(LoginCredentialsService::toBankAccount)
                    .toList();
            String latestModBy = pickLatestModBy(rows);
            String latestModAt = pickLatestModAt(rows);

            out.add(new LoginCredentialGroup(
                    TYPE_BANK,
                    bankCd,
                    bankNames.getOrDefault(bankCd, bankCd),
                    latestModBy,
                    latestModAt,
                    accounts));
        }

        // --- GDT: zero or one group, accounts = whatever the tenant has ---
        List<GdtCertInfo> gdtRows = gdtRepo.findByBizKeyOrderBySeqNoAsc(bizKey);
        if (!gdtRows.isEmpty()) {
            List<LoginCredentialAccount> accounts = gdtRows.stream()
                    .map(LoginCredentialsService::toGdtAccount)
                    .toList();
            String latestModBy = gdtRows.stream()
                    .max(Comparator.comparing(g -> nullToEmpty(g.getModDtm())))
                    .map(GdtCertInfo::getModId)
                    .orElse(null);
            String latestModAt = gdtRows.stream()
                    .map(GdtCertInfo::getModDtm)
                    .filter(s -> s != null && !s.isBlank())
                    .max(String::compareTo)
                    .orElse(null);

            out.add(new LoginCredentialGroup(
                    TYPE_GDT,
                    null,
                    GDT_INSTITUTION_LABEL,
                    latestModBy,
                    latestModAt,
                    accounts));
        }

        return new LoginCredentialListResponse(out);
    }

    /* -----------------------------------------------------------------
     *  DELETE — one credential row, with audit-log fan-out
     * ----------------------------------------------------------------- */

    @Transactional
    public void delete(String type, BigDecimal seqNo) {
        String bizKey = TenantContext.currentBizKey();
        String normalizedType = normalizeType(type);

        if (TYPE_BANK.equals(normalizedType)) {
            BankCertInfo row = bankRepo.findByBizKeyAndSeqNo(bizKey, seqNo)
                    .orElseThrow(() -> new BusinessException("WBE2090"));
            bankRepo.delete(row);
            // Account rows are NOT cascade-deleted. A credential and the
            // accounts it once authenticated are independent concepts —
            // removing the credential just means "I can no longer log
            // into the bank online", not "delete my accounts from the
            // dashboard". The earlier refactor cascade (which read the
            // legacy `fnif_0001_001_d001` comments as intent-to-cascade)
            // was incorrect: removing it preserves real customer data
            // on Banking > Account Inquiry, and the per-bank
            // `certConnYn` derivation in `AccountService` now correctly
            // surfaces those rows as "disconnected" with the tooltip
            // until the user re-registers a credential.
            appendAudit(REF_NO_BANK, seqNo.toPlainString());
        } else {
            GdtCertInfo row = gdtRepo.findByBizKeyAndSeqNo(bizKey, seqNo)
                    .orElseThrow(() -> new BusinessException("WBE2090"));
            gdtRepo.delete(row);
            appendAudit(REF_NO_GDT, seqNo.toPlainString());
        }
    }

    /* -----------------------------------------------------------------
     *  BANK PICKER — list scraping-capable banks for the Add flow
     * ----------------------------------------------------------------- */

    /**
     * Return the banks the user can pick from in the Add Credentials
     * flow. Sources real {@code BANK_CD} + display name + icon path
     * from {@code BANK_INFM_DETL}, mirroring the legacy
     * {@code nxisas_acctList_r002} response (V121 supplies the
     * supporting seed rows).
     *
     * <p>Tenant-agnostic — bank registry is global. {@code search}
     * filters the {@code BANK_NM} column case-insensitively; null or
     * blank returns the full list.
     */
    @Transactional(readOnly = true)
    public BankPickerResponse banks(String search) {
        List<BankPickerRow> rows = bankPickerRepo.list(search);
        return new BankPickerResponse(rows);
    }

    /**
     * Per-bank form info — one sub-type entry per {@code SEQ_NO}
     * configured for the bank in {@code BANK_INFM_DETL}. Drives the
     * Login ID/PW dialog's radio buttons + localised field labels +
     * optional Corporate-ID input. Mirrors the legacy
     * {@code nxisas_getCertFormInfo} response.
     *
     * <p>Returns an empty {@code subTypes} list for unknown banks
     * (the frontend then falls back to a default single-sub-type
     * form with generic English labels).
     */
    @Transactional(readOnly = true)
    public BankFormInfoResponse formInfo(String bankCd) {
        List<BankFormInfoSubType> subTypes = bankFormInfoRepo.formInfo(bankCd);
        return new BankFormInfoResponse(bankCd, subTypes);
    }

    /* -----------------------------------------------------------------
     *  CREATE — one bank credential row, with audit fan-out
     * ----------------------------------------------------------------- */

    @Transactional
    public CredentialCreateResponse createBank(BankCredentialCreateRequest req) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        BigDecimal nextSeq = bankRepo.findMaxSeqNo(bizKey).add(BigDecimal.ONE);
        String now = nowDtm14();

        BankCertInfo entity = new BankCertInfo();
        entity.setBizKey(bizKey);
        entity.setSeqNo(nextSeq);
        entity.setBankCd(req.bankCd());
        entity.setCertInfoDv(CERT_INFO_DV_BANK);
        entity.setCertInfoCon(req.loginId());
        entity.setCertInfoDetlCon(crypto.encrypt(req.password()));
        // Per-bank extras (mirrors legacy comm_0218_003_c001):
        //   CERT_INFO_DETL_CON2 ← Corporate ID / extra field
        //   CERT_INFO_DETL_CON3 ← Login Type (ABBANK / Vietcom Personal)
        //   NXISAS_GB_SEQ_NO    ← sub-type discriminator (radio value)
        // Both DETL_CON2/3 are stored plain (legacy parity — only the
        // password slot is encrypted at rest).
        if (req.extra() != null)     entity.setCertInfoDetlCon2(req.extra());
        if (req.loginType() != null) entity.setCertInfoDetlCon3(req.loginType());
        entity.setNxisasGbSeqNo(
                req.nxisasGbSeqNo() != null ? req.nxisasGbSeqNo() : BigDecimal.ZERO);
        entity.setRegId(userId);
        entity.setRegDtm(now);
        entity.setModId(userId);
        entity.setModDtm(now);
        BankCertInfo saved = bankRepo.save(entity);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO_BANK)
                .refNo1(saved.getSeqNo().toPlainString())
                .activityKey("audit.login_credentials.activity")
                .detailKey("audit.login_credentials.create.detail")
                .build());

        return new CredentialCreateResponse(saved.getSeqNo());
    }

    /* -----------------------------------------------------------------
     *  UPDATE — bank credential (loginId / password / extra)
     * ----------------------------------------------------------------- */

    @Transactional
    public void updateBank(BigDecimal seqNo, BankCredentialUpdateRequest req) {
        if (req.loginId() == null
                && req.password() == null
                && req.extra() == null
                && req.loginType() == null
                && req.nxisasGbSeqNo() == null) {
            // Empty patch — nothing to do. Mirrors legacy
            // `comm_0218_009_u001` which short-circuits when every
            // field is missing. Surface as a validation-like error
            // so the client knows it didn't no-op silently.
            throw new BusinessException("WBE_VALIDATION");
        }
        String bizKey = TenantContext.currentBizKey();
        BankCertInfo entity = bankRepo.findByBizKeyAndSeqNo(bizKey, seqNo)
                .orElseThrow(() -> new BusinessException("WBE2090"));
        // Optional current-password guard. Legacy parity:
        // `comm_0218_004_u001_act.jsp:55-57` puts
        // `decrypt(cert_info_detl_con) = ?` in the IDODynamic WHERE
        // clause so the UPDATE silently affects zero rows on mismatch.
        // Refactor surfaces the same outcome explicitly with WBE2091
        // (the same code the reveal endpoint uses for a bad re-auth).
        if (req.oldPassword() != null) {
            String storedPlain = crypto.decrypt(entity.getCertInfoDetlCon());
            if (!req.oldPassword().equals(storedPlain)) {
                throw new BusinessException("WBE2091");
            }
        }
        if (req.loginId() != null)        entity.setCertInfoCon(req.loginId());
        if (req.password() != null)       entity.setCertInfoDetlCon(crypto.encrypt(req.password()));
        if (req.extra() != null)          entity.setCertInfoDetlCon2(req.extra());
        if (req.loginType() != null)      entity.setCertInfoDetlCon3(req.loginType());
        if (req.nxisasGbSeqNo() != null)  entity.setNxisasGbSeqNo(req.nxisasGbSeqNo());
        entity.setModId(TenantContext.currentUserId());
        entity.setModDtm(nowDtm14());
        bankRepo.save(entity);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO_BANK)
                .refNo1(seqNo.toPlainString())
                .activityKey("audit.login_credentials.activity")
                .detailKey("audit.login_credentials.update.detail")
                .build());
    }

    /**
     * Edit flow's pre-update bank-side verification. Loads the
     * BANK_CERT_INFO row by SEQ_NO, runs an inline NXISAS LOGIN with
     * the stored bank context + the user's typed NEW password, and
     * returns the resulting {@link ScrapeSessionResponse}. NEVER
     * writes to the DB — the existing row stays untouched regardless
     * of outcome.
     *
     * <p>Frontend contract: on {@code COMPLETED}, the caller follows
     * up with the standard {@code PUT /api/v1/login-credentials/bank/{seqNo}}
     * carrying the new password to persist it. On any challenge
     * ({@code OTP_REQUIRED} / {@code QNA_REQUIRED} /
     * {@code CAPTCHA_REQUIRED}), the caller resumes via the existing
     * scraping endpoints; the persist happens only after a final
     * COMPLETED. On {@code ERROR}, the caller surfaces the bank-side
     * error and does NOT call PUT — the user's existing password is
     * preserved.
     *
     * <p>Tenant scope: the {@code TenantContext} guard inside
     * {@link BankCertInfoRepository#findByBizKeyAndSeqNo} prevents
     * cross-tenant access; a malicious / stale seqNo from a different
     * BIZ_KEY surfaces as {@code WBE2090} (not found).
     */
    @Transactional(readOnly = true)
    public ScrapeSessionResponse verifyUpdate(BigDecimal seqNo, String newPassword) {
        String bizKey = TenantContext.currentBizKey();
        BankCertInfo entity = bankRepo.findByBizKeyAndSeqNo(bizKey, seqNo)
                .orElseThrow(() -> new BusinessException("WBE2090"));
        VerifyCredentialsRequest req = new VerifyCredentialsRequest(
                entity.getBankCd(),
                entity.getCertInfoCon(),
                newPassword,
                entity.getNxisasGbSeqNo(),
                entity.getCertInfoDetlCon2(),
                entity.getCertInfoDetlCon3(),
                null /* scrapeKind — defaults to ACCT_LIST */);
        return bankScrapingService.verify(req);
    }

    /* -----------------------------------------------------------------
     *  CREATE / UPDATE — GDT credential
     * ----------------------------------------------------------------- */

    @Transactional
    public CredentialCreateResponse createGdt(GdtCredentialRequest req) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        BigDecimal nextSeq = gdtRepo.findMaxSeqNo(bizKey).add(BigDecimal.ONE);
        String now = nowDtm14();

        GdtCertInfo entity = new GdtCertInfo();
        entity.setBizKey(bizKey);
        entity.setSeqNo(nextSeq);
        entity.setCertInfoDv(CERT_INFO_DV_GDT);
        entity.setCertInfoCon(req.loginId());
        entity.setCertInfoDetlCon(crypto.encrypt(req.password()));
        if (req.extra() != null) entity.setCertInfoDetlCon2(req.extra());
        entity.setRegId(userId);
        entity.setRegDtm(now);
        entity.setModId(userId);
        entity.setModDtm(now);
        GdtCertInfo saved = gdtRepo.save(entity);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO_GDT)
                .refNo1(saved.getSeqNo().toPlainString())
                .activityKey("audit.login_credentials.activity")
                .detailKey("audit.login_credentials.create.detail")
                .build());

        return new CredentialCreateResponse(saved.getSeqNo());
    }

    @Transactional
    public void updateGdt(BigDecimal seqNo, GdtCredentialUpdateRequest req) {
        if (req.loginId() == null && req.password() == null && req.extra() == null) {
            throw new BusinessException("WBE_VALIDATION");
        }
        String bizKey = TenantContext.currentBizKey();
        GdtCertInfo entity = gdtRepo.findByBizKeyAndSeqNo(bizKey, seqNo)
                .orElseThrow(() -> new BusinessException("WBE2090"));
        if (req.loginId() != null) entity.setCertInfoCon(req.loginId());
        if (req.password() != null) entity.setCertInfoDetlCon(crypto.encrypt(req.password()));
        if (req.extra() != null)    entity.setCertInfoDetlCon2(req.extra());
        entity.setModId(TenantContext.currentUserId());
        entity.setModDtm(nowDtm14());
        gdtRepo.save(entity);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO_GDT)
                .refNo1(seqNo.toPlainString())
                .activityKey("audit.login_credentials.activity")
                .detailKey("audit.login_credentials.update.detail")
                .build());
    }

    /* -----------------------------------------------------------------
     *  REVEAL — decrypt password after re-auth
     * ----------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public RevealResponse reveal(String type, BigDecimal seqNo, String userPassword) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String normalizedType = normalizeType(type);

        UserInfm user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException("WBE1009"));
        if (!passwordEncoder.matches(userPassword, user.getUserPw())) {
            throw new BusinessException("WBE2091");
        }

        if (TYPE_BANK.equals(normalizedType)) {
            BankCertInfo row = bankRepo.findByBizKeyAndSeqNo(bizKey, seqNo)
                    .orElseThrow(() -> new BusinessException("WBE2090"));
            return new RevealResponse(
                    row.getCertInfoCon(),
                    crypto.decrypt(row.getCertInfoDetlCon()));
        } else {
            GdtCertInfo row = gdtRepo.findByBizKeyAndSeqNo(bizKey, seqNo)
                    .orElseThrow(() -> new BusinessException("WBE2090"));
            return new RevealResponse(
                    row.getCertInfoCon(),
                    crypto.decrypt(row.getCertInfoDetlCon()));
        }
    }

    /* -----------------------------------------------------------------
     *  AUDIT — history popup
     * ----------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public LoginCredentialAuditResponse auditLogs(String type, BigDecimal seqNo) {
        String bizKey = TenantContext.currentBizKey();
        String normalizedType = normalizeType(type);
        String refNo = TYPE_BANK.equals(normalizedType) ? REF_NO_BANK : REF_NO_GDT;

        LangCd lang = TenantContext.currentLangCd();
        if (lang == null) lang = LangCd.EN;

        // The Login Credentials audit rows always carry REF_NO2 = NULL
        // (single-column SEQ_NO PK). Use the dedicated finder so we
        // match rows with NULL REF_NO2 instead of equality on null.
        List<AuditLogEntry> rows = auditLogReadRepo
                .findByBizKeyAndRefNoAndRefNo1AndWsvcIdAndLangCdAndRefNo2IsNullOrderByRegDtmDesc(
                        bizKey, refNo, seqNo.toPlainString(), WSVC_ID, lang.name());

        List<LoginCredentialAuditRow> items = rows.stream()
                .map(r -> new LoginCredentialAuditRow(
                        r.getAuditLogsSeqNo(),
                        r.getAuditLogsActv(),
                        r.getAuditLogsActvDtl(),
                        r.getRegId(),
                        r.getRegNm(),
                        r.getRegDtm()))
                .toList();
        return new LoginCredentialAuditResponse(items);
    }

    /* -----------------------------------------------------------------
     *  Helpers
     * ----------------------------------------------------------------- */

    /** Server-clock UTC stamp in the legacy {@code yyyyMMddHHmmss}
     *  shape. Used for every REG_DTM / MOD_DTM write on this slice. */
    private static String nowDtm14() {
        return LocalDateTime.now(ZoneOffset.UTC).format(DTM14);
    }

    private void appendAudit(String refNo, String refNo1) {
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(refNo)
                .refNo1(refNo1)
                .activityKey("audit.login_credentials.activity")
                .detailKey("audit.login_credentials.delete.detail")
                .build());
    }

    /**
     * Resolve BANK_CD → display name via {@link BankInfmRepository}.
     * Falls back to the bank code itself when the registry has no
     * entry. Cross-domain coupling is intentional — every BANK_CD on
     * disk is sourced from the same registry the Banking module uses,
     * and {@code BankCdResolver} (in {@code domain.banking.service}) is
     * package-private so we re-query rather than cross the package
     * boundary.
     */
    private Map<String, String> resolveBankNames(Iterable<String> bankCds) {
        Map<String, String> out = new HashMap<>();
        for (String code : bankCds) {
            if (code == null || code.isBlank()) continue;
            out.putIfAbsent(code, code);
        }
        if (out.isEmpty()) return out;
        for (BankInfm row : bankInfmRepo.findAll()) {
            if (out.containsKey(row.getBankCd())) {
                out.put(row.getBankCd(), row.getRprsBankNm());
            }
        }
        return out;
    }

    private static LoginCredentialAccount toBankAccount(BankCertInfo e) {
        return new LoginCredentialAccount(
                e.getSeqNo(),
                e.getCertInfoCon(),
                e.getNxisasGbSeqNo());
    }

    private static LoginCredentialAccount toGdtAccount(GdtCertInfo e) {
        return new LoginCredentialAccount(
                e.getSeqNo(),
                e.getCertInfoCon(),
                null);
    }

    private static String pickLatestModBy(List<BankCertInfo> rows) {
        return rows.stream()
                .max(Comparator.comparing(r -> nullToEmpty(r.getModDtm())))
                .map(BankCertInfo::getModId)
                .orElse(null);
    }

    private static String pickLatestModAt(List<BankCertInfo> rows) {
        return rows.stream()
                .map(BankCertInfo::getModDtm)
                .filter(s -> s != null && !s.isBlank())
                .max(String::compareTo)
                .orElse(null);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /** Normalise the path segment ({@code "bank"} / {@code "gdt"})
     *  to the canonical type constant; throws on unknown input. */
    private static String normalizeType(String type) {
        if (type == null) throw new BusinessException("WBE2092");
        String lower = type.toLowerCase();
        if ("bank".equals(lower)) return TYPE_BANK;
        if ("gdt".equals(lower))  return TYPE_GDT;
        throw new BusinessException("WBE2092");
    }
}
