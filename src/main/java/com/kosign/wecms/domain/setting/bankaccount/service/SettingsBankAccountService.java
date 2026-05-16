package com.kosign.wecms.domain.setting.bankaccount.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.AcctTypeCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountListResponse;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountRegisterRequest;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountRegisterResponse;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountRow;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountUpdateRequest;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import com.kosign.wecms.domain.setting.vasaccount.entity.BusiVasAcctInfm;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings &gt; Bank Account service — list / update / bulk-register
 * paths for the {@code ACCT_MSTR} table. Mirrors the legacy
 * {@code comm_0183_*} module:
 *
 * <ul>
 *   <li>{@link #list()} ↔ {@code comm_0183_001_r001} —
 *       enumerates all {@code ACCT_MSTR} rows for the tenant
 *       (active + inactive) with bank-name resolution.</li>
 *   <li>{@link #update(String, String, BankAccountUpdateRequest)} ↔
 *       {@code comm_0183_003_u001_act.jsp} — patches nickname /
 *       description / account-type / maturity, writes per-locale
 *       audit-log rows via the shared {@link AuditLogService}.</li>
 *   <li>{@link #register(BankAccountRegisterRequest)} — bulk INSERT
 *       used by the Login Credentials Add-chain after the user picks
 *       accounts from a successful Coocon scrape. Duplicate
 *       {@code (bankCd, acctNo)} rows are silently skipped (legacy
 *       parity — the scrape can re-surface the same account).</li>
 * </ul>
 *
 * <p>VAS-mapping fields, the per-row permission counts, and the
 * per-account audit-log read endpoint ship in Batch B.
 */
@Service
@RequiredArgsConstructor
public class SettingsBankAccountService {

    /** Refactor slug — written to {@code AUDIT_LOGS_INFM.WSVC_ID}. */
    public static final String WSVC_ID = "bank-account";

    /** {@code AUDIT_LOGS_INFM.REF_NO} — legacy parity (
     *  {@code comm_0183_003_u001_act.jsp:181}). */
    private static final String REF_NO = "ACCT_MSTR";

    /** Legacy `yyyyMMddHHmmss` audit-column shape. */
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Default {@code ACCT_TYPE_CD} when a register payload omits it
     *  AND the dialog dropdown didn't fire onChange. Mirrors the
     *  static-UI dropdown's "Current" default. */
    private static final String DEFAULT_ACCT_TYPE_CD = "02";

    private final AcctMstrRepository acctRepo;
    private final BankInfmRepository bankInfmRepo;
    private final BusiVasAcctInfmRepository vasAcctRepo;
    private final AcctAuthInfoRepository acctAuthRepo;
    private final AuditLogService auditLogService;

    /* -----------------------------------------------------------------
     *  LIST — every account for the tenant
     * ----------------------------------------------------------------- */

    /** Legacy default ORDER BY — {@code PRN_SEQ ASC, BANK_CD ASC,
     *  ACCT_NO ASC}. Mirrors {@code comm_0183_001_r001} and is also
     *  woven into the secondary order when a user-driven sort runs so
     *  equal-keyed rows keep a deterministic order across requests. */
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.asc("prnSeq"),
            Sort.Order.asc("bankCd"),
            Sort.Order.asc("acctNo"));

    @Transactional(readOnly = true)
    public BankAccountListResponse list() {
        return list(null, null);
    }

    /**
     * Sort-aware variant. {@code sortField} is a View Setting
     * {@code FIELD_ID} (e.g. {@code BANK}, {@code BALANCE}) resolved by
     * {@link BankAccountSortField}; unknown / blank values fall back to
     * {@link #DEFAULT_SORT}.
     */
    @Transactional(readOnly = true)
    public BankAccountListResponse list(String sortField, String sortDirection) {
        String bizKey = TenantContext.currentBizKey();
        // Pull every account (active + inactive — legacy
        // comm_0183_001_r001 doesn't filter by ACTV_YN; users can see
        // disabled rows so they can re-enable later).
        Sort sort = buildSort(
                BankAccountSortField.parse(sortField),
                SortDirection.parse(sortDirection));
        List<AcctMstr> rows = acctRepo.findByBizKey(bizKey, sort);

        Map<String, String> bankNames = resolveBankNames(rows);

        List<BankAccountRow> items = rows.stream()
                .map(r -> toRow(r, bankNames, resolvePermissionLabels(bizKey, r)))
                .toList();
        return new BankAccountListResponse(items);
    }

    /** Resolve the (viewLabel, withdrawalLabel) pair for one bank-account
     *  row by looking up the active grantee count + first name for both
     *  AUTH_TYPE codes. Mirrors legacy `comm_0183_001.ts:633` — first
     *  grantee's name, plus "+ N more" when more than one. Returns a
     *  two-element array: [viewLabel, withdrawalLabel]. Either can be
     *  null when the account has no active grantee of that kind. */
    private String[] resolvePermissionLabels(String bizKey, AcctMstr r) {
        return new String[] {
                composePermissionLabel(bizKey, r, "V"),
                composePermissionLabel(bizKey, r, "W"),
        };
    }

    private String composePermissionLabel(String bizKey, AcctMstr r, String authType) {
        long count = acctAuthRepo.countActiveByAccount(
                bizKey, r.getBankCd(), r.getAcctNo(), authType);
        if (count == 0) return null;
        List<String> firstName = acctAuthRepo.findFirstActiveUserName(
                bizKey, r.getBankCd(), r.getAcctNo(), authType);
        if (firstName.isEmpty()) return null;
        String head = firstName.get(0);
        if (count == 1) return head;
        return head + " + " + (count - 1) + " more";
    }

    /**
     * Build a Spring Data {@link Sort} from the validated whitelist enum +
     * direction. The user's primary sort is always followed by
     * {@link #DEFAULT_SORT} so equal-keyed rows stay deterministic across
     * requests. Mirrors {@code AccountService.buildSort}.
     */
    private static Sort buildSort(BankAccountSortField field, SortDirection direction) {
        if (field == null || direction == null) {
            return DEFAULT_SORT;
        }
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        return Sort.by(primary).and(DEFAULT_SORT);
    }

    /* -----------------------------------------------------------------
     *  UPDATE — one ACCT_MSTR row
     * ----------------------------------------------------------------- */

    @Transactional
    public void update(String bankCd, String acctNo, BankAccountUpdateRequest req) {
        if (req.nickname() == null
                && req.description() == null
                && req.accountTypeCd() == null
                && req.maturityDate() == null
                && req.vasAcctSeqNo() == null) {
            throw new BusinessException("WBE_VALIDATION");
        }
        String bizKey = TenantContext.currentBizKey();
        AcctMstr row = acctRepo.findByBizKeyAndBankCdAndAcctNo(bizKey, bankCd, acctNo)
                .orElseThrow(() -> new BusinessException("WBE_BANK_ACCOUNT_NOT_FOUND"));

        if (req.nickname() != null) {
            row.setAcctNickNm(req.nickname());
        }
        if (req.description() != null) {
            row.setCont(req.description());
        }
        if (req.accountTypeCd() != null) {
            // Reject unknown codes — keeps the column inside the
            // legacy 02..08 set (01 LOAN intentionally unmapped per
            // AcctTypeCd Javadoc).
            AcctTypeCd.fromCode(req.accountTypeCd())
                    .orElseThrow(() -> new BusinessException(
                            "WBE_BANK_ACCOUNT_INVALID_TYPE",
                            req.accountTypeCd()));
            row.setAcctTypeCd(req.accountTypeCd());
        }
        if (req.maturityDate() != null) {
            // Empty string = clear (saving → current). Otherwise
            // strip hyphens to convert ISO yyyy-MM-dd → legacy
            // VARCHAR(8) yyyyMMdd.
            String stripped = req.maturityDate().isBlank()
                    ? null
                    : req.maturityDate().replace("-", "");
            if (stripped != null && stripped.length() != 8) {
                throw new BusinessException(
                        "WBE_BANK_ACCOUNT_INVALID_DATE", req.maturityDate());
            }
            row.setExpiDt(stripped);
        }
        // VAS Account triple — frontend ships {seqNo, cd, nm} together.
        // seqNo == 0 (or null with cd/nm both blank) = clear the mapping;
        // a positive seqNo = set the mapping after FK validation. The
        // service is the authority on cd/nm — the wire's cd/nm fields
        // are echoed convenience values, not load-bearing data.
        if (req.vasAcctSeqNo() != null) {
            Long seqNo = req.vasAcctSeqNo();
            if (seqNo <= 0) {
                row.setVasAcctSeqNo(null);
                row.setVasAcctCd(null);
                row.setVasAcctNm(null);
            } else {
                BusiVasAcctInfm vas = vasAcctRepo
                        .findByBizKeyAndVasAcctSeqNoAndDelYn(bizKey, seqNo, "N")
                        .orElseThrow(() -> new BusinessException(
                                "WBE_BANK_ACCOUNT_INVALID_VAS_ACCT", String.valueOf(seqNo)));
                // Reject inactive VAS rows on write — picker only ever
                // offers active ones, but a stale browser tab could
                // still POST a now-inactive seqNo.
                if (!"A".equals(vas.getStts())) {
                    throw new BusinessException(
                            "WBE_BANK_ACCOUNT_INVALID_VAS_ACCT", String.valueOf(seqNo));
                }
                row.setVasAcctSeqNo(vas.getVasAcctSeqNo());
                row.setVasAcctCd(vas.getVasAcctCd());
                row.setVasAcctNm(vas.getVasAcctNm());
            }
        }
        row.setModId(TenantContext.currentUserId());
        row.setModDtm(nowDtm14());
        acctRepo.save(row);

        // Per-locale audit log — mirrors legacy
        // comm_0183_003_u001_act.jsp:154-191 loop.
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(bankCd)
                .refNo2(acctNo)
                .activityKey("audit.bank_account.activity")
                .detailKey("audit.bank_account.update.detail")
                .build());
    }

    /* -----------------------------------------------------------------
     *  REGISTER — bulk INSERT from the Add-chain
     * ----------------------------------------------------------------- */

    @Transactional
    public BankAccountRegisterResponse register(BankAccountRegisterRequest req) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String now = nowDtm14();
        int inserted = 0;
        int skipped = 0;

        for (BankAccountRegisterRequest.Item item : req.items()) {
            // Validate account-type code per item (default to Current
            // when blank — matches the legacy scrape-flow contract
            // where the dialog defaults to Current).
            String typeCd = (item.accountTypeCd() == null || item.accountTypeCd().isBlank())
                    ? DEFAULT_ACCT_TYPE_CD
                    : item.accountTypeCd();
            AcctTypeCd.fromCode(typeCd)
                    .orElseThrow(() -> new BusinessException(
                            "WBE_BANK_ACCOUNT_INVALID_TYPE", typeCd));

            // Skip duplicates silently — legacy parity. Re-saving
            // the same picked-account set after a re-scrape should
            // not blow up with a PK violation.
            if (acctRepo.findByBizKeyAndBankCdAndAcctNo(bizKey, req.bankCd(), item.acctNo()).isPresent()) {
                skipped++;
                continue;
            }

            AcctMstr row = new AcctMstr();
            row.setBizKey(bizKey);
            row.setBankCd(req.bankCd());
            row.setAcctNo(item.acctNo());
            row.setAcctTypeCd(typeCd);
            row.setCcyCd(item.currency());
            row.setAcctNickNm(item.nickname());
            row.setAcctNm(item.acctNm());
            row.setCont(item.description());
            row.setActvYn("Y");
            row.setHandRegYn("N");  // scraped, not manually registered
            row.setRegId(userId);
            row.setRegDtm(now);
            row.setModId(userId);
            row.setModDtm(now);
            acctRepo.save(row);

            auditLogService.append(AuditLogContext.builder()
                    .wsvcId(WSVC_ID)
                    .refNo(REF_NO)
                    .refNo1(req.bankCd())
                    .refNo2(item.acctNo())
                    .activityKey("audit.bank_account.activity")
                    .detailKey("audit.bank_account.create.detail")
                    .build());
            inserted++;
        }
        return new BankAccountRegisterResponse(inserted, skipped);
    }

    /* -----------------------------------------------------------------
     *  Helpers
     * ----------------------------------------------------------------- */

    private Map<String, String> resolveBankNames(List<AcctMstr> rows) {
        Map<String, String> out = new HashMap<>();
        for (AcctMstr row : rows) {
            if (row.getBankCd() != null) {
                out.putIfAbsent(row.getBankCd(), row.getBankCd());
            }
        }
        if (out.isEmpty()) return out;
        for (BankInfm bi : bankInfmRepo.findAll()) {
            if (out.containsKey(bi.getBankCd())) {
                out.put(bi.getBankCd(), bi.getRprsBankNm());
            }
        }
        return out;
    }

    private static BankAccountRow toRow(
            AcctMstr e,
            Map<String, String> bankNames,
            String[] permissionLabels) {
        // Legacy `EXPI_DT` is yyyyMMdd; surface ISO yyyy-MM-dd on the
        // wire so the frontend can render via the shared date pipeline.
        String maturity = null;
        if (e.getExpiDt() != null && e.getExpiDt().length() == 8) {
            maturity = e.getExpiDt().substring(0, 4) + "-"
                    + e.getExpiDt().substring(4, 6) + "-"
                    + e.getExpiDt().substring(6, 8);
        }
        return new BankAccountRow(
                e.getBankCd(),
                bankNames.getOrDefault(e.getBankCd(), e.getBankCd()),
                e.getAcctNo(),
                e.getCcyCd(),
                e.getAcctNickNm(),
                e.getCont(),
                e.getAcctTypeCd(),
                maturity,
                e.getVasAcctSeqNo(),
                e.getVasAcctCd(),
                e.getVasAcctNm(),
                // Composite "BANK(CCY)" label for the VAS Account Name
                // column — Batch B will join through BUSI_VAS_ACCT_INFM
                // for a richer label; for now echo the denormalised
                // copy + the currency in parens when both are present.
                composeVasAcctBank(e.getVasAcctNm(), e.getCcyCd()),
                e.getPrnSeq(),
                e.getBal(),
                e.getRealAmt(),
                e.getModId(),
                e.getModDtm(),
                permissionLabels[0],   // viewPermissionLabel
                permissionLabels[1]    // withdrawalPermissionLabel
        );
    }

    private static String composeVasAcctBank(String vasAcctNm, String ccyCd) {
        if (vasAcctNm == null || vasAcctNm.isBlank()) return null;
        if (ccyCd == null || ccyCd.isBlank()) return vasAcctNm;
        return vasAcctNm + "(" + ccyCd + ")";
    }

    private static String nowDtm14() {
        return LocalDateTime.now(ZoneOffset.UTC).format(DTM14);
    }
}
