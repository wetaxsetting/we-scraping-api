package com.kosign.wecms.domain.banking.smartorganizer.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfm;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.service.AccountAccessGuard;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSetInfm;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository;
import com.kosign.wecms.domain.setting.vasaccount.entity.BusiVasAcctInfm;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import com.kosign.wecms.domain.banking.smartorganizer.dto.ConfirmRequest;
import com.kosign.wecms.domain.banking.smartorganizer.dto.ConfirmResponse;
import com.kosign.wecms.domain.banking.smartorganizer.dto.EligibleTransactionListResponse;
import com.kosign.wecms.domain.banking.smartorganizer.dto.EligibleTransactionRow;
import com.kosign.wecms.domain.banking.smartorganizer.dto.SmartOrganizerSortField;
import com.kosign.wecms.domain.banking.smartorganizer.repository.SmartOrganizerEligibleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Banking &gt; Smart Organizer — list eligible transactions
 * (rule-matched, unconfirmed) and confirm them into
 * {@code ACCT_TRNS_RCV_PAY_INFM}. Mirrors legacy
 * {@code fnsh_0200_001_r001} (list) + {@code fnsh_0200_001_c001}
 * (confirm).
 *
 * <p>Eligibility query mirrors legacy {@code ACCT_TRNS_HSTR_R028}:
 * <ul>
 *   <li>tenant-scoped on {@code ATH.BIZ_KEY};</li>
 *   <li>filtered on {@code TRSC_DT BETWEEN :fromDt AND :toDt};</li>
 *   <li>filtered on {@code ACCT_TYPE_CD IN ('03','04','05','06')}
 *       (legacy excludes loan / time-deposit accounts);</li>
 *   <li>excludes rows already confirmed in
 *       {@code ACCT_TRNS_RCV_PAY_INFM};</li>
 *   <li>requires a matching rule in {@code RMRK_ORG_INFM} where
 *       {@code RCV_PAY_DV} aligns with {@code INOUT_DV}
 *       (1 → RCV, 2 → PAY) AND ({@code MATCH_TYPE = 'E'} AND
 *       {@code RMRK = ATH.RMRK}) OR ({@code MATCH_TYPE != 'E'} AND
 *       {@code ATH.RMRK ILIKE '%' || RMRK || '%'});</li>
 *   <li>usage-group direction filter (legacy {@code getDynamic1}):
 *       when the winning rule has a {@code USE_USAG_CD}, that usage's
 *       {@code USAG_GRP_CD} must match the direction (1 → '4', 2 → '6');
 *       VAS-only / chart-only rules skip it (Option B);</li>
 *   <li>optional keyword filter on {@code ATH.RMRK} (ILIKE).</li>
 * </ul>
 *
 * <p>The query projects the matched rule's classification targets
 * (CHART_ACCT_*, USE_USAG_CD, VAS_ACCT_SEQ_NO, TRCO_KEY) so the
 * frontend can pre-populate the table without a second round-trip.
 * When multiple rules match the same bank row, the lowest
 * {@code DISP_ORD} wins (LIMIT 1 in the lateral subquery).
 */
@Service
@RequiredArgsConstructor
public class SmartOrganizerService {

    private static final DateTimeFormatter LEGACY_DTM =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 15;

    private final AcctTrnsHstrRepository acctTrnsHstrRepo;
    private final AcctTrnsRcvPayInfmRepository rcvPayRepo;
    private final LegacyFunctionRepository legacy;
    private final AccountAccessGuard accountAccessGuard;

    // R05 / R03 / R06 / R07 — master-data resolution + confirm validation.
    private final BusiVasAcctInfmRepository vasRepo;
    private final TrcoInfmRepository trcoRepo;
    private final PurposeSettingsRepository purposeRepo;
    private final ChartOfAccountRepository chartRepo;
    private final AcctMstrRepository acctMstrRepo;
    private final BankCdResolver bankCdResolver;
    private final SmartOrganizerEligibleRepository eligibleRepo;

    /**
     * Native eligibility query — returns one row per
     * (ACCT_TRNS_HSTR, matched RMRK_ORG_INFM rule). Bound to
     * {@link SmartOrganizerSortField} whitelist so wire sort keys
     * cannot leak into the SQL string.
     */
    @Transactional(readOnly = true)
    public EligibleTransactionListResponse listEligible(
            String bankCd, String acctNo,
            String fromDt, String toDt, String search,
            String sortFieldRaw, String sortDirRaw,
            int page, int size) {

        String bizKey = requireBizKey();
        requireAccount(bankCd, acctNo);
        // Final PO decision (R11, 2026-06-06): Smart Organizer must operate
        // ONLY on the account selected in Transaction History — never
        // tenant-wide. Per-account scope + the same View-grant authorization
        // as the transaction list (audit #29 parity).
        accountAccessGuard.assertCanView(bizKey, bankCd, acctNo);

        String from = normaliseDate8(fromDt);
        String to   = normaliseDate8(toDt);

        SmartOrganizerSortField sortField =
                SmartOrganizerSortField.parse(sortFieldRaw).orElse(null);
        SortDirection sortDir = SortDirection.parse(sortDirRaw);

        // Sort column resolved through the whitelist — only the validated SQL
        // expression is handed to the repository; raw client input never
        // reaches the ORDER BY.
        String orderByExpr = sortField != null ? sortField.sqlExpression() : "h.trsc_dt";

        long total = eligibleRepo.countEligible(bizKey, bankCd, acctNo, from, to, search);
        if (total == 0) {
            return new EligibleTransactionListResponse(0L, List.of());
        }

        int pageSize = clampPageSize(size);
        int pageNo   = Math.max(1, page);
        List<Object[]> raw = eligibleRepo.pageEligible(
                bizKey, bankCd, acctNo, from, to, search,
                orderByExpr, sortDir == SortDirection.ASC,
                (pageNo - 1) * pageSize, pageSize);

        return new EligibleTransactionListResponse(total, resolveRows(bizKey, raw));
    }

    /**
     * R07 — resolve the matched rule's classification codes into the
     * display names the legacy R028 returned (bank name, nickname,
     * reporting-account / purpose / VAS / contact names). Lookups are
     * memoised per page so a repeated code is resolved once. Projection
     * column order matches {@code selectSql} above.
     */
    private List<EligibleTransactionRow> resolveRows(String bizKey, List<Object[]> raw) {
        Map<String, String> bankNmCache  = new HashMap<>();
        Map<String, String> nickCache    = new HashMap<>();
        Map<String, String> chartNmCache = new HashMap<>();
        Map<String, String> usagNmCache  = new HashMap<>();
        Map<Long, String>   vasNmCache   = new HashMap<>();
        Map<String, String> trcoNmCache  = new HashMap<>();

        List<EligibleTransactionRow> rows = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            String acctSeqNo = asString(r[0]);
            String bankCd    = asString(r[1]);
            String acctNo    = asString(r[2]);
            String trscDt    = asString(r[3]);
            String trscTm    = asString(r[4]);
            String inoutDv   = asString(r[5]);
            BigDecimal inAmt  = asBigDecimal(r[6]);
            BigDecimal outAmt = asBigDecimal(r[7]);
            BigDecimal bal    = asBigDecimal(r[8]);
            String ccyCd     = asString(r[9]);
            String rmrk      = asString(r[10]);
            Long matchedSeq  = asLong(r[11]);
            String chartCd   = asString(r[12]);
            String chartSeq  = asString(r[13]);
            String useUsagCd = asString(r[14]);
            Long vasSeq      = asLong(r[15]);
            String trcoKey   = asString(r[16]);

            String bankNm = bankCd == null ? null
                    : bankNmCache.computeIfAbsent(bankCd, bankCdResolver::resolveBankNm);
            String acctNickNm = (bankCd == null || acctNo == null) ? null
                    : nickCache.computeIfAbsent(bankCd + "|" + acctNo, k ->
                        acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(bizKey, bankCd, acctNo)
                                .map(AcctMstr::getAcctNickNm).orElse(null));
            String chartNm = chartCd == null ? null
                    : chartNmCache.computeIfAbsent(chartCd, k ->
                        chartRepo.findByBizKeyAndChartAcctCdAndDelYn(bizKey, k, "N")
                                .map(ChartAcctInfm::getChartAcctNmEn).orElse(null));
            String dispUsagNm = useUsagCd == null ? null
                    : usagNmCache.computeIfAbsent(useUsagCd, k ->
                        purposeRepo.findByBizKeyAndUseUsagCd(bizKey, k)
                                .map(SmartOrganizerService::usagLabel).orElse(null));
            String dispVasNm = vasSeq == null ? null
                    : vasNmCache.computeIfAbsent(vasSeq, k ->
                        vasRepo.findByBizKeyAndVasAcctSeqNoAndDelYn(bizKey, k, "N")
                                .map(SmartOrganizerService::vasLabel).orElse(null));
            String dispTrcoNm = trcoKey == null ? null
                    : trcoNmCache.computeIfAbsent(trcoKey, k ->
                        trcoRepo.findByTrcoKeyAndBizKey(k, bizKey)
                                .map(SmartOrganizerService::trcoLabel).orElse(null));

            rows.add(new EligibleTransactionRow(
                    acctSeqNo, bankCd, bankNm, acctNo, acctNickNm,
                    trscDt, trscTm, inoutDv, inAmt, outAmt, bal, ccyCd, rmrk,
                    matchedSeq, chartCd, chartSeq, chartNm,
                    // chartAcctLevelNm — leaf name for now (full breadcrumb is an R07 follow-up).
                    chartNm,
                    useUsagCd, dispUsagNm, vasSeq, dispVasNm, trcoKey, dispTrcoNm));
        }
        return rows;
    }

    private static String usagLabel(BusiUsagSetInfm u) {
        return joinLabel(u.getHandUseUsagCd(), u.getUseUsagNm());
    }

    private static String vasLabel(BusiVasAcctInfm v) {
        return joinLabel(v.getVasAcctCd(), v.getVasAcctNm());
    }

    private static String trcoLabel(TrcoInfm t) {
        String name = t.getTrcoCmenm() != null ? t.getTrcoCmenm() : t.getTrcoCmnm();
        return joinLabel(t.getHandTrcoKey(), name);
    }

    private static String joinLabel(String code, String name) {
        String c = blankToNull(code);
        String n = blankToNull(name);
        if (c != null && n != null) return c + " " + n;
        if (n != null) return n;
        return c;
    }

    /**
     * Confirm one or more eligible transactions into
     * {@code ACCT_TRNS_RCV_PAY_INFM}. Mirrors legacy
     * {@code fnsh_0200_001_c001}. Each row:
     * <ol>
     *   <li>404 if {@code ACCT_TRNS_HSTR} row missing in tenant;</li>
     *   <li>conflict if a confirmation row already exists for the
     *       same {@code (BIZ_KEY, ACCT_SEQ_NO)};</li>
     *   <li>allocates {@code RCV_PAY_SEQ_NO} via
     *       {@code wb_get_next_val};</li>
     *   <li>writes the confirmation row with the caller as
     *       {@code REG_ID} / {@code CNFM_BY_USER_*} and
     *       {@code CNFM_YN='Y'}.</li>
     * </ol>
     */
    @Transactional
    public ConfirmResponse confirm(ConfirmRequest req) {
        String bizKey = requireBizKey();
        String bankCd = req.bankCd();
        String acctNo = req.acctNo();
        requireAccount(bankCd, acctNo);
        // R11 — confirm is part of Smart Organizer and is scoped to the
        // selected account. Authorize the account, then verify every row
        // belongs to it so a request cannot smuggle in transactions from
        // another account.
        accountAccessGuard.assertCanView(bizKey, bankCd, acctNo);

        String userId = TenantContext.currentUserId();
        String userNm = TenantContext.currentUserNm();
        String nowDtm = nowYyyyMMddHHmmss();

        int confirmed = 0;
        for (ConfirmRequest.Row r : req.rows()) {
            String acctSeqNo = r.acctSeqNo();

            AcctTrnsHstrId id = new AcctTrnsHstrId();
            id.setBizKey(bizKey);
            id.setAcctSeqNo(acctSeqNo);
            AcctTrnsHstr bank = acctTrnsHstrRepo.findById(id).orElseThrow(
                    () -> new BusinessException("WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND"));

            // R11 — reject rows that don't belong to the scoped account.
            if (!bankCd.equals(bank.getBankCd()) || !acctNo.equals(bank.getAcctNo())) {
                throw new BusinessException("WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND");
            }

            if (rcvPayRepo.existsByBizKeyAndAcctSeqNoAndCnfmYn(bizKey, acctSeqNo, "Y")) {
                throw new BusinessException("WBE_SMART_ORGANIZER_ALREADY_CONFIRMED");
            }

            // R03 — validate the classification targets exist in the tenant AND
            // enforce the legacy USAG_GRP_CD↔INOUT_DV direction rule
            // (c001_act.jsp:96-116): a deposit (INOUT_DV '1') requires a group-'4'
            // (income / RCV) usage, a withdrawal ('2') requires a group-'6'
            // (expense / PAY) usage; a mismatch rolls the whole confirm back.
            // USAG_GRP_CD is populated in both JEX DEV (99.99%) and the
            // refactor/SIT DB, so the prior presence-only degradation is removed.
            // Directions other than '1'/'2' (e.g. ETC '0') are not validated,
            // matching legacy which only branches on IN / OUT.
            String useUsagCd = blankToNull(r.useUsagCd());
            if (useUsagCd != null) {
                BusiUsagSetInfm usage = purposeRepo.findByBizKeyAndUseUsagCd(bizKey, useUsagCd)
                        .orElseThrow(() -> new BusinessException("WBE_SMART_ORGANIZER_USAGE_NOT_FOUND"));
                String expectedGrp = "1".equals(bank.getInoutDv()) ? "4"
                        : "2".equals(bank.getInoutDv()) ? "6" : null;
                if (expectedGrp != null && !expectedGrp.equals(usage.getUsagGrpCd())) {
                    throw new BusinessException("WBE_SMART_ORGANIZER_USAGE_DIRECTION_MISMATCH");
                }
            }
            BusiVasAcctInfm vas = null;
            if (r.vasAcctSeqNo() != null) {
                vas = vasRepo.findByBizKeyAndVasAcctSeqNoAndDelYn(bizKey, r.vasAcctSeqNo(), "N")
                        .orElseThrow(() -> new BusinessException("WBE_SMART_ORGANIZER_VAS_NOT_FOUND"));
            }
            String rcvPayTrcoKey = blankToNull(r.trcoKey());
            TrcoInfm trco = null;
            if (rcvPayTrcoKey != null) {
                trco = trcoRepo.findByTrcoKeyAndBizKey(rcvPayTrcoKey, bizKey)
                        .orElseThrow(() -> new BusinessException("WBE_SMART_ORGANIZER_CONTACT_NOT_FOUND"));
            }

            String seqNoRaw = legacy.nextVal(
                    "acct_trns_rcv_pay_infm", "rcv_pay_seq_no",
                    bizKey, acctSeqNo, null, null, null, null);
            if (seqNoRaw == null || seqNoRaw.isBlank()) {
                throw new BusinessException("WBE9999");
            }

            AcctTrnsRcvPayInfm c = new AcctTrnsRcvPayInfm();
            c.setBizKey(bizKey);
            c.setAcctSeqNo(acctSeqNo);
            c.setRcvPaySeqNo(new BigDecimal(seqNoRaw.trim()));
            c.setBankCd(bank.getBankCd());
            c.setAcctNo(bank.getAcctNo());
            c.setTrscDt(bank.getTrscDt());
            c.setRcvPayAmt(bank.getTrscAmt());
            c.setRcvPayUsagCd(useUsagCd);
            // R05 — contact: denormalize the resolved names at write (legacy c001_act.jsp:182-183).
            c.setRcvPayTrcoKey(rcvPayTrcoKey);
            c.setRcvPayTrcoNm(trco == null ? null : trco.getTrcoCmnm());
            c.setRcvPayTrcoEnm(trco == null ? null : trco.getTrcoCmenm());
            // R05 — VAS: store the surrogate + the REAL code + name (legacy c001_act.jsp:187-188).
            // The pre-remediation code wrote the surrogate into VAS_ACCT_CD and dropped the name.
            c.setVasAcctSeqNo(r.vasAcctSeqNo() == null ? null : BigDecimal.valueOf(r.vasAcctSeqNo()));
            c.setVasAcctCd(vas == null ? null : vas.getVasAcctCd());
            c.setVasAcctNm(vas == null ? null : vas.getVasAcctNm());
            c.setCnfmYn("Y");
            c.setCnfmByUserId(userId);
            c.setCnfmByUserNm(userNm);
            c.setCnfmDtm(nowDtm);
            c.setRegId(userId);
            c.setRegDtm(nowDtm);
            c.setModId(userId);
            c.setModDtm(nowDtm);

            rcvPayRepo.save(c);
            confirmed++;
        }
        return new ConfirmResponse(confirmed);
    }

    // ---- helpers ----------------------------------------------------

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE1009");
        }
        return bizKey;
    }

    /** R11 — the selected-account scope is mandatory on every Smart
     *  Organizer call; a missing bank/account is a client error, never a
     *  silent tenant-wide fallback. */
    private static void requireAccount(String bankCd, String acctNo) {
        if (bankCd == null || bankCd.isBlank() || acctNo == null || acctNo.isBlank()) {
            throw new BusinessException("WBE_VALIDATION");
        }
    }

    private static String normaliseDate8(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("WBE_VALIDATION");
        }
        String trimmed = raw.trim();
        if (trimmed.length() == 10 && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
            return trimmed.substring(0, 4) + trimmed.substring(5, 7) + trimmed.substring(8, 10);
        }
        if (trimmed.length() != 8) {
            throw new BusinessException("WBE_VALIDATION");
        }
        return trimmed;
    }

    private static int clampPageSize(int size) {
        if (size <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String asString(Object o) { return o == null ? null : o.toString(); }
    private static Long asLong(Object o) { return o == null ? null : ((Number) o).longValue(); }
    private static BigDecimal asBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(o.toString());
    }

    private static String nowYyyyMMddHHmmss() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(LEGACY_DTM);
    }
}
