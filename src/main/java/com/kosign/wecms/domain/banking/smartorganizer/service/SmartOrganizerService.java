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
import com.kosign.wecms.domain.banking.smartorganizer.dto.ConfirmRequest;
import com.kosign.wecms.domain.banking.smartorganizer.dto.ConfirmResponse;
import com.kosign.wecms.domain.banking.smartorganizer.dto.EligibleTransactionListResponse;
import com.kosign.wecms.domain.banking.smartorganizer.dto.EligibleTransactionRow;
import com.kosign.wecms.domain.banking.smartorganizer.dto.SmartOrganizerSortField;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    @PersistenceContext
    private EntityManager em;

    private final AcctTrnsHstrRepository acctTrnsHstrRepo;
    private final AcctTrnsRcvPayInfmRepository rcvPayRepo;
    private final LegacyFunctionRepository legacy;

    /**
     * Native eligibility query — returns one row per
     * (ACCT_TRNS_HSTR, matched RMRK_ORG_INFM rule). Bound to
     * {@link SmartOrganizerSortField} whitelist so wire sort keys
     * cannot leak into the SQL string.
     */
    @Transactional(readOnly = true)
    public EligibleTransactionListResponse listEligible(
            String fromDt, String toDt, String search,
            String sortFieldRaw, String sortDirRaw,
            int page, int size) {

        String bizKey = requireBizKey();
        String from = normaliseDate8(fromDt);
        String to   = normaliseDate8(toDt);

        SmartOrganizerSortField sortField =
                SmartOrganizerSortField.parse(sortFieldRaw).orElse(null);
        SortDirection sortDir = SortDirection.parse(sortDirRaw);

        StringBuilder where = new StringBuilder();
        where.append(" WHERE h.biz_key = :bizKey ");
        where.append("   AND h.trsc_dt BETWEEN :fromDt AND :toDt ");
        // Loan / time-deposit accounts excluded (legacy parity).
        where.append("   AND EXISTS ( ");
        where.append("       SELECT 1 FROM acct_mstr m ");
        where.append("        WHERE m.biz_key = h.biz_key ");
        where.append("          AND m.bank_cd = h.bank_cd ");
        where.append("          AND m.acct_no = h.acct_no ");
        where.append("          AND m.acct_type_cd IN ('03','04','05','06') ");
        where.append("   ) ");
        // Not yet confirmed.
        where.append("   AND NOT EXISTS ( ");
        where.append("       SELECT 1 FROM acct_trns_rcv_pay_infm c ");
        where.append("        WHERE c.biz_key = h.biz_key ");
        where.append("          AND c.acct_seq_no = h.acct_seq_no ");
        where.append("   ) ");
        if (search != null && !search.isBlank()) {
            where.append("   AND h.rmrk ILIKE '%' || :search || '%' ");
        }

        // The lateral join picks the lowest-DISP_ORD matching rule —
        // the same precedence the legacy IDO orders by.
        String ruleJoin =
                " JOIN LATERAL ( " +
                "   SELECT r.rmrk_seq_no, r.disp_ord, r.match_type, " +
                "          r.rcv_pay_dv, r.use_usag_cd, r.vas_acct_seq_no, " +
                "          r.trco_key, r.chart_acct_cd, r.chart_acct_seq_no " +
                "     FROM rmrk_org_infm r " +
                "    WHERE r.biz_key = h.biz_key " +
                "      AND r.rcv_pay_dv = CASE WHEN h.inout_dv = '1' THEN 'RCV' ELSE 'PAY' END " +
                "      AND ( (r.match_type = 'E' AND r.rmrk = h.rmrk) " +
                "         OR (r.match_type <> 'E' AND h.rmrk ILIKE '%' || r.rmrk || '%') ) " +
                "    ORDER BY r.disp_ord ASC, r.rmrk_seq_no ASC " +
                "    LIMIT 1 " +
                " ) r ON TRUE ";

        // ---- count -----
        String countSql = "SELECT COUNT(*) FROM acct_trns_hstr h " + ruleJoin + where;
        Query countQ = em.createNativeQuery(countSql);
        countQ.setParameter("bizKey", bizKey);
        countQ.setParameter("fromDt", from);
        countQ.setParameter("toDt", to);
        if (search != null && !search.isBlank()) countQ.setParameter("search", search.trim());
        long total = ((Number) countQ.getSingleResult()).longValue();

        if (total == 0) {
            return new EligibleTransactionListResponse(0L, List.of());
        }

        // ---- page -----
        String orderBy = " ORDER BY " +
                (sortField != null ? sortField.sqlExpression() : "h.trsc_dt") +
                " " + (sortDir == SortDirection.ASC ? "ASC" : "DESC") +
                ", h.acct_seq_no ASC ";

        String selectSql =
                "SELECT h.acct_seq_no, h.bank_cd, h.acct_no, " +
                "       h.trsc_dt, h.trsc_tm, h.inout_dv, " +
                "       CASE WHEN h.inout_dv = '1' THEN h.trsc_amt ELSE NULL END AS in_amt, " +
                "       CASE WHEN h.inout_dv = '2' THEN h.trsc_amt ELSE NULL END AS out_amt, " +
                "       h.rmrk, " +
                "       r.rmrk_seq_no, r.chart_acct_cd, r.chart_acct_seq_no, " +
                "       r.use_usag_cd, r.vas_acct_seq_no, r.trco_key " +
                "  FROM acct_trns_hstr h " + ruleJoin + where + orderBy;

        int pageSize = clampPageSize(size);
        int pageNo = Math.max(1, page);

        Query q = em.createNativeQuery(selectSql);
        q.setParameter("bizKey", bizKey);
        q.setParameter("fromDt", from);
        q.setParameter("toDt", to);
        if (search != null && !search.isBlank()) q.setParameter("search", search.trim());
        q.setFirstResult((pageNo - 1) * pageSize);
        q.setMaxResults(pageSize);

        @SuppressWarnings("unchecked")
        List<Object[]> raw = q.getResultList();

        List<EligibleTransactionRow> rows = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            rows.add(new EligibleTransactionRow(
                    asString(r[0]),
                    asString(r[1]),
                    asString(r[2]),
                    asString(r[3]),
                    asString(r[4]),
                    asString(r[5]),
                    asBigDecimal(r[6]),
                    asBigDecimal(r[7]),
                    asString(r[8]),
                    asLong(r[9]),
                    asString(r[10]),
                    asString(r[11]),
                    asString(r[12]),
                    asLong(r[13]),
                    asString(r[14])
            ));
        }
        return new EligibleTransactionListResponse(total, rows);
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

            if (rcvPayRepo.existsByBizKeyAndAcctSeqNoAndCnfmYn(bizKey, acctSeqNo, "Y")) {
                throw new BusinessException("WBE_SMART_ORGANIZER_ALREADY_CONFIRMED");
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
            c.setRcvPaySeqNo(seqNoRaw.trim());
            c.setBankCd(bank.getBankCd());
            c.setAcctNo(bank.getAcctNo());
            c.setTrscDt(bank.getTrscDt());
            c.setRcvPayAmt(bank.getTrscAmt());
            c.setRcvPayTrcoKey(blankToNull(r.trcoKey()));
            c.setRcvPayUsagCd(blankToNull(r.useUsagCd()));
            // VAS_ACCT_CD on the confirmation row carries the BUSI_VAS_ACCT_INFM
            // surrogate cast to string (legacy parity). Frontend re-resolves
            // the display name from a separate VAS lookup.
            c.setVasAcctCd(r.vasAcctSeqNo() == null ? null : String.valueOf(r.vasAcctSeqNo()));
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
