package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.CreateMatchRequest;
import com.kosign.wecms.domain.banking.dto.MatchCandidateListResponse;
import com.kosign.wecms.domain.banking.dto.MatchCandidateRow;
import com.kosign.wecms.domain.banking.dto.MatchResultResponse;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrId;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smart Organizer (legacy {@code comm_0196_001} match popup).
 *
 * <p>Owns three operations on the bank-transaction ↔ payment-line link
 * pair stored across {@link AcctTrnsHstr#getLinkMatchYn()} and
 * {@link ScdulPymnSumrDetl#getLinkMatchYn()}:
 *
 * <ol>
 *   <li>{@link #listCandidates} — search the tenant's unmatched
 *       domestic scheduled payment lines for the popup table.</li>
 *   <li>{@link #create} — atomically write the three-table state:
 *       {@code BUSI_MATCH_LOG} (insert or unlock-then-update),
 *       {@code ACCT_TRNS_HSTR.LINK_MATCH_YN='Y'} + payment-FK columns,
 *       {@code SCDUL_PYMN_SUMR_DETL.LINK_MATCH_YN='Y'} + bank-FK
 *       back-references.</li>
 *   <li>{@link #undo} — inverse flow: flip {@code LINK_MATCH_YN='N'}
 *       on both sides and {@code LOCKED_YN='N'} on the audit row.</li>
 * </ol>
 *
 * <p>Overseas payments ({@code OVRS_PYMN_SUMR_DETL}) are out of scope
 * in the first refactor slice — the popup only lists domestic
 * candidates. The {@link CreateMatchRequest#payTypeCd()} field is
 * validated server-side so a future {@code 'O'} client can ship
 * without an API change.
 *
 * <p>Tenant scoping: every read / write constrains on
 * {@link TenantContext#currentBizKey()}.
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    private static final String CODE_BAD_REQUEST = "WBE2002";
    private static final String CODE_TRNS_NOT_FOUND = "WBE_TRANSACTION_NOT_FOUND";
    private static final String CODE_PAY_NOT_FOUND = "WBE_MATCH_PAY_NOT_FOUND";
    private static final String CODE_ALREADY_MATCHED = "WBE_MATCH_ALREADY_MATCHED";
    private static final String CODE_PAY_ALREADY_MATCHED = "WBE_MATCH_PAY_ALREADY_MATCHED";
    private static final String CODE_NOT_MATCHED = "WBE_MATCH_NOT_MATCHED";

    private static final DateTimeFormatter DTM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String PAY_TYPE_DOMESTIC = "S";
    /** Pre-built composite ID delimiter (`SCDUL_PYMN_NO + "_" + SCDUL_PYMN_DETL_NO`). */
    private static final String PAY_TRNS_ID_SEP = "_";

    private final AcctTrnsHstrRepository txnRepo;
    private final BusiMatchLogRepository matchLogRepo;
    private final ScdulPymnSumrRepository sumrRepo;
    private final ScdulPymnSumrDetlRepository detlRepo;
    private final AccountAccessGuard accountAccessGuard;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    /* ---------------------------------------------------------- list ---- */

    /**
     * Search the tenant's unmatched domestic scheduled payment lines.
     * The bank transaction is identified by {@code acctSeqNo} +
     * {@code trnsSeqNo} (the synthesised surrogate the
     * Transaction History list emits per row). The popup uses the
     * lookup result for an amount / currency cue in its header
     * (frontend does the visual comparison — no backend amount
     * tolerance enforced, mirroring legacy {@code comm_0196_001}).
     */
    @Transactional(readOnly = true)
    public MatchCandidateListResponse listCandidates(
            String acctSeqNo, String trnsSeqNo, String fromDt, String toDt, String keyword) {
        String bizKey = TenantContext.currentBizKey();
        requireBankRow(bizKey, trnsSeqNo);  // sanity — also blocks cross-tenant probes.

        List<ScdulPymnSumrDetl> details = detlRepo.findUnmatchedForTenant(bizKey);
        if (details.isEmpty()) {
            return new MatchCandidateListResponse(List.of(), 0);
        }

        // Look up parent batches in one round-trip — uses Spring Data's
        // findAllById against a Set of composite keys built from the
        // detail rows' SCDUL_PYMN_NO column.
        List<ScdulPymnSumrId> parentIds = details.stream()
                .map(d -> new ScdulPymnSumrId(d.getBizKey(), d.getScdulPymnNo()))
                .distinct()
                .toList();
        Map<String, ScdulPymnSumr> parents = new HashMap<>();
        for (ScdulPymnSumr s : sumrRepo.findAllById(parentIds)) {
            parents.put(s.getScdulPymnNo(), s);
        }

        String needle = keyword == null ? null : keyword.trim().toLowerCase();
        if (needle != null && needle.isEmpty()) needle = null;

        List<MatchCandidateRow> rows = new ArrayList<>(details.size());
        for (ScdulPymnSumrDetl d : details) {
            ScdulPymnSumr parent = parents.get(d.getScdulPymnNo());
            if (parent == null) continue;  // detail row with no parent — defensive skip.

            // Legacy parity: comm_0196_002_r001 does NOT filter on
            // TRNF_YN — every unmatched payment line is a candidate
            // regardless of dispatch state. Mirroring that here so the
            // popup surfaces the same set legacy users see.

            String trnfDt = trnfDt(parent);
            if (!matchesDateRange(trnfDt, fromDt, toDt)) continue;
            if (needle != null && !matchesKeyword(d, parent, needle)) continue;

            rows.add(toCandidate(d, parent, trnfDt));
        }
        return new MatchCandidateListResponse(rows, rows.size());
    }

    /* -------------------------------------------------------- create ---- */

    /**
     * Match the bank transaction to the selected payment line.
     * Atomically writes {@code BUSI_MATCH_LOG} (insert or unlock-then-
     * update) + flips {@code LINK_MATCH_YN='Y'} on both sides.
     */
    @Transactional
    public MatchResultResponse create(String acctSeqNo, String trnsSeqNo, CreateMatchRequest req) {
        if (req == null) throw new BusinessException(CODE_BAD_REQUEST);
        if (!PAY_TYPE_DOMESTIC.equals(req.payTypeCd())) {
            // Overseas deferred — fail closed.
            throw new BusinessException(CODE_BAD_REQUEST);
        }

        String bizKey = TenantContext.currentBizKey();
        AcctTrnsHstr bank = requireBankRow(bizKey, trnsSeqNo);
        if ("Y".equals(bank.getLinkMatchYn())) {
            throw new BusinessException(CODE_ALREADY_MATCHED);
        }

        ScdulPymnSumrDetl detail = parsePayTrnsId(req.payTrnsId())
                .map(parts -> detlRepo.findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(
                        bizKey, parts[0], parts[1]).orElse(null))
                .orElse(null);
        if (detail == null) throw new BusinessException(CODE_PAY_NOT_FOUND);
        if ("Y".equals(detail.getLinkMatchYn())) {
            throw new BusinessException(CODE_PAY_ALREADY_MATCHED);
        }

        // Cross-tenant probe defence — `findByBizKeyAnd*` already
        // constrains, but the lock-row check below is keyed by
        // (bizKey, payTrnsId), so a malicious replay across tenants
        // can never collide.
        matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(bizKey, req.payTrnsId(), "Y")
                .ifPresent(rp -> { throw new BusinessException(CODE_PAY_ALREADY_MATCHED); });

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);

        // 1. BUSI_MATCH_LOG — insert new, or unlock-then-update if a
        //    historical row for the same pair exists.
        BusiMatchLog log = matchLogRepo
                .findFirstByBizKeyAndBankTrnsShortIdAndLockedYn(
                        bizKey, nullToEmpty(bank.getBankTrnsShortId()), "N")
                .orElseGet(BusiMatchLog::new);
        boolean isFresh = log.getMatchLogSeq() == null;
        if (isFresh) {
            log.setBizKey(bizKey);
            log.setMatchLogSeq(nextMatchLogSeq(bizKey));
            log.setBankTrnsShortId(nullToEmpty(bank.getBankTrnsShortId()));
            log.setBankTrnsFullId(nullToEmpty(bank.getBankTrnsFullId()));
            log.setPayTrnsId(req.payTrnsId());
            log.setRegId(userId);
            log.setRegDtm(nowDtm);
        }
        log.setMatchTypeCd("M");
        log.setMemo(req.memo());
        log.setMatchedAt(nowDtm);
        log.setMatchedBy(userId);
        log.setLastMatchedAt(nowDtm);
        log.setLastMatchedBy(userId);
        log.setLockedYn("Y");
        log.setModId(userId);
        log.setModDtm(nowDtm);
        matchLogRepo.save(log);

        // 2. ACCT_TRNS_HSTR — set LINK_MATCH_YN='Y' + payment FKs.
        int bankUpdated = txnRepo.markBankRowMatched(
                bizKey, trnsSeqNo,
                detail.getScdulPymnNo(), detail.getScdulPymnDetlNo(),
                userId, nowDtm);
        if (bankUpdated == 0) throw new BusinessException(CODE_TRNS_NOT_FOUND);

        // 3. SCDUL_PYMN_SUMR_DETL — set LINK_MATCH_YN='Y' + bank refs.
        int detlUpdated = detlRepo.markDetailMatched(
                bizKey, detail.getScdulPymnNo(), detail.getScdulPymnDetlNo(),
                trnsSeqNo,
                nullToEmpty(bank.getBankTrnsShortId()),
                nullToEmpty(bank.getBankTrnsFullId()),
                userId, nowDtm);
        if (detlUpdated == 0) throw new BusinessException(CODE_PAY_NOT_FOUND);

        return new MatchResultResponse(trnsSeqNo, "Y", req.payTrnsId(), log.getMatchLogSeq());
    }

    /* ---------------------------------------------------------- undo ---- */

    /** Undo a match — flips {@code LINK_MATCH_YN='N'} on both sides and
     *  {@code LOCKED_YN='N'} on the audit row (preserves history). */
    @Transactional
    public MatchResultResponse undo(String acctSeqNo, String trnsSeqNo) {
        String bizKey = TenantContext.currentBizKey();
        AcctTrnsHstr bank = requireBankRow(bizKey, trnsSeqNo);
        if (!"Y".equals(bank.getLinkMatchYn())) {
            throw new BusinessException(CODE_NOT_MATCHED);
        }

        BusiMatchLog log = matchLogRepo
                .findFirstByBizKeyAndBankTrnsShortIdAndLockedYn(
                        bizKey, nullToEmpty(bank.getBankTrnsShortId()), "Y")
                .orElseThrow(() -> new BusinessException(CODE_NOT_MATCHED));

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);

        // 1. Unlock the audit row (don't delete — preserves history).
        log.setLockedYn("N");
        log.setLastMatchedAt(nowDtm);
        log.setLastMatchedBy(userId);
        log.setModId(userId);
        log.setModDtm(nowDtm);
        matchLogRepo.save(log);

        // 2. Clear payment-side link (use the FK stored on the bank row).
        String[] parts = parsePayTrnsId(log.getPayTrnsId()).orElse(null);
        if (parts != null) {
            detlRepo.clearDetailMatch(bizKey, parts[0], parts[1], userId, nowDtm);
        }

        // 3. Clear bank-side link.
        int updated = txnRepo.clearBankRowMatch(bizKey, trnsSeqNo, userId, nowDtm);
        if (updated == 0) throw new BusinessException(CODE_TRNS_NOT_FOUND);

        return new MatchResultResponse(trnsSeqNo, "N", log.getPayTrnsId(), log.getMatchLogSeq());
    }

    /* -------------------------------------------------------- helpers --- */

    private AcctTrnsHstr requireBankRow(String bizKey, String trnsSeqNo) {
        if (trnsSeqNo == null || trnsSeqNo.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST);
        }
        AcctTrnsHstr bank = txnRepo.findById(new AcctTrnsHstrId(bizKey, trnsSeqNo))
                .orElseThrow(() -> new BusinessException(CODE_TRNS_NOT_FOUND));
        // Audit #29 — per-account data scope on the match/unmatch/candidates
        // paths (Smart Organizer). Non-admins may only act on accounts they
        // hold an active View grant for; throws 403 otherwise.
        accountAccessGuard.assertCanView(bizKey, bank.getBankCd(), bank.getAcctNo());
        return bank;
    }

    private static java.util.Optional<String[]> parsePayTrnsId(String payTrnsId) {
        if (payTrnsId == null) return java.util.Optional.empty();
        int sep = payTrnsId.indexOf(PAY_TRNS_ID_SEP);
        if (sep <= 0 || sep == payTrnsId.length() - 1) return java.util.Optional.empty();
        return java.util.Optional.of(new String[]{
                payTrnsId.substring(0, sep),
                payTrnsId.substring(sep + 1)
        });
    }

    /** MATCH_LOG_SEQ via the single wb_get_next_val funnel (V140 #24 —
     *  per-tenant MAX+1, verbatim from the live JEX function). */
    private String nextMatchLogSeq(String bizKey) {
        return legacyFunctions.nextBusiMatchLogSeq(bizKey);
    }

    /** {@code yyyyMMddHHmmss} → {@code yyyyMMdd} for filter matching;
     *  falls back to {@code REG_DTM} when {@code TRNF_DTM} is null
     *  (pre-dispatch parity). */
    private static String trnfDt(ScdulPymnSumr parent) {
        String src = parent.getTrnfDtm() != null && parent.getTrnfDtm().length() >= 8
                ? parent.getTrnfDtm()
                : parent.getRegDtm() != null ? parent.getRegDtm() : "";
        return src.length() >= 8 ? src.substring(0, 8) : src;
    }

    private static boolean matchesDateRange(String trnfDt, String fromDt, String toDt) {
        if (fromDt != null && !fromDt.isBlank() && trnfDt.compareTo(fromDt) < 0) return false;
        if (toDt != null && !toDt.isBlank() && trnfDt.compareTo(toDt) > 0) return false;
        return true;
    }

    private static boolean matchesKeyword(ScdulPymnSumrDetl d, ScdulPymnSumr parent, String lcNeedle) {
        return containsLc(d.getRcptNm(), lcNeedle)
                || containsLc(d.getTranUsag(), lcNeedle)
                || containsLc(d.getTrcoCmnm(), lcNeedle)
                || containsLc(parent.getTitl(), lcNeedle);
    }

    private static boolean containsLc(String haystack, String lcNeedle) {
        return haystack != null && haystack.toLowerCase().contains(lcNeedle);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private MatchCandidateRow toCandidate(
            ScdulPymnSumrDetl d, ScdulPymnSumr parent, String trnfDt) {
        BigDecimal amt = d.getTranAmt() != null ? d.getTranAmt() : BigDecimal.ZERO;
        return new MatchCandidateRow(
                d.getScdulPymnNo() + PAY_TRNS_ID_SEP + d.getScdulPymnDetlNo(),
                PAY_TYPE_DOMESTIC,
                d.getScdulPymnNo(),
                d.getScdulPymnDetlNo(),
                parent.getScdulPymnTypeCd(),
                trnfDt,
                parent.getTrnfDtm(),
                AmountFormatter.wire(amt),
                d.getTrnfCcyCd() != null ? d.getTrnfCcyCd() : parent.getCcyCd(),
                d.getRcptNm() != null ? d.getRcptNm() : (d.getTrcoCmnm() != null ? d.getTrcoCmnm() : ""),
                d.getTarBankCd(),
                null,  // BNFC_BANK_NM — resolved on the frontend via the existing bank-name map.
                d.getFincMdaNo(),
                d.getTranUsag(),
                parent.getTitl()
        );
    }
}
