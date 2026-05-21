package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.payment.cardtransactions.dto.ActiveCard;
import com.kosign.wecms.domain.payment.cardtransactions.dto.ApproverRef;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionListFilters;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionPatchRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionRow;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionTotals;
import com.kosign.wecms.domain.payment.cardtransactions.dto.LinkedDocRef;
import com.kosign.wecms.domain.payment.cardtransactions.dto.PatchDescriptionRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.PatchPurposeRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.UpdateReportingAccountRequest;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprDv;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardMstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository.LedgerTotalsProjection;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLineApprover;
import com.kosign.wecms.domain.setting.approvalline.repository.ApprovalLineApproverRepository;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Payment &gt; Card Transactions — read + classify service. Mirrors
 * the legacy {@code card_0001_001_*} family:
 *
 * <ul>
 *   <li>{@link #list} — {@code card_0001_001_r001} (ledger + totals).</li>
 *   <li>{@link #cards} — {@code card_0001_001_r002} (filter dropdown).</li>
 *   <li>{@link #patch} — {@code card_0001_001_u001} (single-field
 *       description / purpose).</li>
 *   <li>{@link #updateReportingAccount} — {@code card_0001_001_u002}
 *       (always-paired CHART_ACCT update).</li>
 *   <li>{@link #listAllRows} — un-paginated read for the export
 *       provider.</li>
 * </ul>
 *
 * <h2>Identity rules</h2>
 * <ul>
 *   <li>Audit {@code WSVC_ID} = {@code "card-transactions"} (slug, not
 *       {@code card_0001_001}) per workspace {@code CLAUDE.md}
 *       §"Refactor identifier naming".</li>
 *   <li>No row create / delete — scraping stays on the JEX side. The
 *       refactor never allocates a {@code CARD_SEQ_NO}, so no
 *       {@code wb_get_next_val} branch is added in V176.</li>
 * </ul>
 *
 * <h2>Tenant scope</h2>
 *
 * <p>Every method reads {@link TenantContext#currentBizKey()} and
 * applies it as a predicate of every query / write — no method on this
 * service accepts a wire-supplied {@code bizKey}.
 *
 * <h2>Phase B placeholders</h2>
 *
 * <p>Feature-doc OQ-1 / OQ-7 defer the linked-doc and approver-chain
 * joins ({@code BUSA_DOC_RCV_PAY_HIS}, {@code EXPT_RESOL_APPRVR_INFM}).
 * The service emits empty {@code linked_docs[]} / {@code approvers[]}
 * arrays for every row in Sprint 0 — same shape Internal Transfer used
 * during its Phase A. Frontend renders against the published wire
 * contract; the lists fill in when those tables are ported.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardTransactionsService {

    /** Audit-log {@code WSVC_ID} for every mutating endpoint. */
    static final String WSVC_ID = "card-transactions";

    /** {@code REF_NO} column on every audit row this service writes. */
    static final String REF_NO  = "CARD_TRNS_HSTR";

    static final String AUDIT_ACTIVITY_KEY                  = "audit.card_transactions.activity";
    static final String AUDIT_PATCH_DESCRIPTION_KEY         = "audit.card_transactions.patch_description.detail";
    static final String AUDIT_PATCH_PURPOSE_KEY             = "audit.card_transactions.patch_purpose.detail";
    static final String AUDIT_UPDATE_REPORTING_ACCOUNT_KEY  = "audit.card_transactions.update_reporting_account.detail";

    /** {@code REF_NO} on the Card Management Setting audit rows. The
     *  legacy {@code card_0001_003} sub-feature mutates {@code CARD_MSTR}
     *  rows, not {@code CARD_TRNS_HSTR} — splitting the ref keeps audit
     *  queries cleanly filterable by which table the user edited. */
    static final String REF_NO_CARD_MSTR = "CARD_MSTR";

    static final String AUDIT_PATCH_CARD_ACTIVE_KEY = "audit.card_transactions.patch_card_active.detail";
    static final String AUDIT_PATCH_CARD_INFO_KEY   = "audit.card_transactions.patch_card_info.detail";
    static final String AUDIT_DELETE_CARD_KEY       = "audit.card_transactions.delete_card.detail";
    static final String AUDIT_CREATE_CARDS_KEY      = "audit.card_transactions.create_cards.detail";
    /** Mirrors the matching constant on Internal Transfer / Expense /
     *  Vendor / Payroll — every Request Approval audit row uses the
     *  same detail key per module so the audit grid groups cleanly. */
    static final String AUDIT_REQUEST_APPROVAL_KEY  = "audit.card_transactions.request_approval.detail";

    /** {@code yyyyMMddHHmmss} formatter shared with the audit writer. */
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Default ORDER BY when the wire {@code sort_field} is missing /
     *  unknown — latest-first by transaction date, deterministic via
     *  CARD_SEQ_NO as the tiebreaker. Mirrors the legacy default of
     *  {@code card_0001_001_r001}. */
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("trscDt"),
            Sort.Order.desc("trscTm"),
            Sort.Order.desc("cardSeqNo"));

    private static final int    DEFAULT_PAGE_SIZE = 15;
    private static final int    MAX_PAGE_SIZE     = 200;
    // Period cap removed 2026-05-20 — legacy has no 365-day limit on
    // the list filter (only start <= end). The earlier MAX_PERIOD_DAYS
    // = 365 constant was a refactor-only addition that has been
    // dropped for legacy parity.
    /** i18n key prefix for the {@code stts_key} hint emitted alongside
     *  every row's raw {@code stts} code. The frontend resolves the key
     *  via {@code t(...)} with fallback to the raw code. */
    static final String         STTS_KEY_PREFIX = "payment.card_transactions.status.";

    private final CardTrnsHstrRepository           cardTrnsRepo;
    private final CardMstrRepository               cardMstrRepo;
    private final BankInfmRepository               bankInfmRepo;
    private final BankCertInfoRepository           bankCertInfoRepo;
    private final ChartOfAccountRepository         chartAcctRepo;
    private final AuditLogService                  auditLogService;
    /** Per-tenant MAX(APPR_SEQ_NO::bigint) allocator — shared with
     *  every other Payment module so the Approval Box id space is
     *  globally contiguous (legacy parity). */
    private final ExptResolMstrRepository          exptResolMstrRepo;
    /** Resolves the approver chain for the picked approval line. */
    private final ApprovalLineApproverRepository   apprLineApproverRepo;
    /** Writes EXPT_RESOL_MSTR + EXPT_RESOL_APPRVR_INFM rows so the
     *  approval surfaces in {@code /payment/approval-box}. */
    private final ApprovalBoxFanoutService         approvalBoxFanoutService;

    /* ----------------------------------------------------------------
     *  List  (legacy card_0001_001_r001)
     * ---------------------------------------------------------------- */

    /**
     * Paged ledger read with aggregate totals.
     *
     * @param fromDt        required {@code yyyyMMdd}
     * @param toDt          required {@code yyyyMMdd}, &ge; fromDt
     * @param srchWd        optional substring against {@code MERCH_NM} /
     *                      {@code CARD_NM}
     * @param bankCds       optional bank-code list (any-of filter)
     * @param cardNos       optional card-no list (any-of filter)
     * @param linkYn        {@code Y} / {@code N} / blank for "all"
     * @param sortField     wire FIELD_ID; falls back to default when unknown
     * @param sortDirection {@code ASC} / {@code DESC}; falls back to ASC
     * @param page          zero-based page index
     * @param size          rows per page (1..200; clamped)
     */
    @Transactional(readOnly = true)
    public CardTransactionListResponse list(
            String fromDt, String toDt, String srchWd,
            List<String> bankCds, List<String> cardNos, String linkYn,
            String sortField, String sortDirection,
            int page, int size) {

        String bizKey = requireTenant();
        CardTransactionListFilters f = buildFilters(fromDt, toDt, srchWd, bankCds, cardNos, linkYn);
        Pageable pageable = buildPageable(page, size, sortField, sortDirection);

        // Phase B note: bank/card multi-select filters are still
        // accepted on the wire + propagated into CardTransactionListFilters
        // for API stability, but the repository does NOT yet apply them
        // (the JPQL `:list IS EMPTY` idiom doesn't compile in Hibernate 6 —
        // it's reserved for entity collection-valued paths). When the
        // frontend popovers ship, fan out to a sibling repository method
        // that declares `AND c.bankCd IN :bankCds` and branch on
        // `f.bankCds().isEmpty()` here. The accepted-but-ignored shape
        // mirrors how Internal Transfer treated its `apprType` filter
        // during Phase A.
        Page<CardTrnsHstr> entityPage = cardTrnsRepo.findLedgerPage(
                bizKey, f.fromDt(), f.toDt(),
                f.srchWdPattern(), f.linkYn(),
                pageable);

        LedgerTotalsProjection raw = cardTrnsRepo.findLedgerTotals(
                bizKey, f.fromDt(), f.toDt(),
                f.srchWdPattern(), f.linkYn());

        // Pre-resolve bank names for the rows on this page so the row
        // mapper doesn't fan out one cache hit per row inside the stream.
        Map<String, String> bankNmByCd = resolveBankNames(entityPage.getContent());

        List<CardTransactionRow> rows = entityPage.getContent().stream()
                .map(e -> toRow(e, bankNmByCd))
                .toList();

        CardTransactionTotals totals = toTotals(raw);
        log.info("feature=card-transactions list bizKey={} total={} page={} size={}",
                bizKey, entityPage.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize());
        return new CardTransactionListResponse(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                entityPage.getTotalElements(),
                totals,
                rows);
    }

    /**
     * Un-paginated ledger read consumed by the export provider. Same
     * filter + sort contract as {@link #list}; the export's row cap
     * lives in {@code ExcelExportService} and is enforced uniformly
     * across every screen.
     */
    @Transactional(readOnly = true)
    public List<CardTransactionRow> listAllRows(
            String fromDt, String toDt, String srchWd,
            List<String> bankCds, List<String> cardNos, String linkYn,
            String sortField, String sortDirection) {

        String bizKey = requireTenant();
        CardTransactionListFilters f = buildFilters(fromDt, toDt, srchWd, bankCds, cardNos, linkYn);
        // Export reuses Spring Data's paging primitive with a single
        // very large page — keeps the JPQL identical and ExcelExportService
        // applies the 50_000-row hard cap on top.
        Pageable pageable = PageRequest.of(0, MAX_PAGE_SIZE * 250 + 1,
                buildSort(sortField, sortDirection));

        Page<CardTrnsHstr> page = cardTrnsRepo.findLedgerPage(
                bizKey, f.fromDt(), f.toDt(),
                f.srchWdPattern(), f.linkYn(),
                pageable);

        Map<String, String> bankNmByCd = resolveBankNames(page.getContent());
        return page.getContent().stream()
                .map(e -> toRow(e, bankNmByCd))
                .toList();
    }

    /**
     * List every card transaction stamped with the given {@code APPR_SEQ_NO}
     * for the current tenant. Used by the shared Approval Box detail
     * popup ({@code <ApprovalDetailsDialog kind="card">}) when the user
     * opens an {@code APPR_DV='33'} row — populates the Transaction
     * Details grid with the cards that were approved together.
     *
     * <p>No paging: a single Request Approval batch caps at a handful
     * of card transactions in practice. Sorted newest-first by purchase
     * date / time for display.
     */
    @Transactional(readOnly = true)
    public List<CardTransactionRow> listByApprSeqNo(String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            return List.of();
        }
        String bizKey = requireTenant();
        List<CardTrnsHstr> entities =
                cardTrnsRepo.findByBizKeyAndApprSeqNo(bizKey, apprSeqNo.trim());
        if (entities.isEmpty()) return List.of();
        Map<String, String> bankNmByCd = resolveBankNames(entities);
        return entities.stream()
                .map(e -> toRow(e, bankNmByCd))
                .toList();
    }

    /* ----------------------------------------------------------------
     *  Cards dropdown  (legacy card_0001_001_r002)
     * ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public CardListResponse cards(String bankCd) {
        String bizKey = requireTenant();
        String narrow = blankToNull(bankCd);
        List<CardMstr> entities = cardMstrRepo.findActiveCards(bizKey, narrow);

        Map<String, String> bankNmByCd = new HashMap<>();
        List<ActiveCard> cards = entities.stream()
                .map(c -> new ActiveCard(
                        c.getBankCd(),
                        bankNmByCd.computeIfAbsent(c.getBankCd(), this::resolveBankName),
                        c.getCardNo(),
                        c.getCardNm(),
                        c.getCardNickNm(),
                        c.getCardHldrNm()))
                .toList();
        log.debug("feature=card-transactions cards bizKey={} bankCd={} count={}",
                bizKey, narrow, cards.size());
        return new CardListResponse(cards);
    }

    /* ----------------------------------------------------------------
     *  Card Management Setting modal  (legacy card_0001_003)
     * ---------------------------------------------------------------- */

    /** Cap on the modal's up-front load. The legacy modal uses
     *  infinite-scroll (PERCNT += 10 per scroll); the refactor returns
     *  the whole tenant slice in one call because typical row counts
     *  are well under this cap (DEV-wide 14 cards across all tenants). */
    private static final int MGMT_MAX_PAGE_SIZE = 100;

    /** Default ORDER BY for the Card Management modal — mirrors legacy
     *  {@code IDO.CARD_MSTR_R004}'s implicit ordering when no sort is
     *  supplied (newest modification first, then deterministic
     *  tiebreakers on the composite PK). */
    private static final Sort MGMT_DEFAULT_SORT = Sort.by(
            Sort.Order.desc("modDtm"),
            Sort.Order.asc("bankCd"),
            Sort.Order.asc("cardNo"));

    /**
     * Paged + sortable list for the Card Management Setting modal.
     * Mirrors legacy {@code card_0001_003_r001} (the modal's data feed).
     *
     * @param srchWd        optional case-insensitive substring against
     *                      {@code CARD_NM} / {@code CARD_NICK_NM} /
     *                      {@code CARD_HLDR_NM}
     * @param sortField     wire FIELD_ID from {@link CardMstrSortField}
     * @param sortDirection {@code ASC} / {@code DESC}
     * @param size          rows per page (1..100; clamped)
     */
    @Transactional(readOnly = true)
    public com.kosign.wecms.domain.payment.cardtransactions.dto.CardManagementListResponse listManagementCards(
            String srchWd, String sortField, String sortDirection, int size) {
        String bizKey = requireTenant();
        Sort   sort   = buildManagementSort(sortField, sortDirection);
        int    safe   = size <= 0 ? MGMT_MAX_PAGE_SIZE : Math.min(size, MGMT_MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(0, safe, sort);

        String pattern = buildLikePattern(srchWd);
        Page<CardMstr> page = cardMstrRepo.findManagementPage(bizKey, pattern, pageable);

        List<com.kosign.wecms.domain.payment.cardtransactions.dto.CardManagementRow> rows =
                page.getContent().stream()
                        .map(this::toManagementRow)
                        .toList();

        log.info("feature=card-transactions list-management bizKey={} total={} returned={}",
                bizKey, page.getTotalElements(), rows.size());
        return new com.kosign.wecms.domain.payment.cardtransactions.dto.CardManagementListResponse(
                page.getTotalElements(), rows);
    }

    /**
     * Toggle {@code ACTV_YN} on one card (the modal's switch). Mirrors
     * legacy {@code card_0001_003_u001}. The wire shape is always-set
     * ({@code "Y"} or {@code "N"}); validation runs at the controller
     * boundary via {@code @Pattern}.
     */
    @Transactional
    public void updateCardActive(
            String bankCd, String cardNo,
            com.kosign.wecms.domain.payment.cardtransactions.dto.UpdateCardActiveRequest req) {
        if (req == null) throw new BusinessException("WBE_VALIDATION");
        String bizKey = requireTenant();
        int n = cardMstrRepo.patchActiveYn(
                bizKey, bankCd, cardNo, req.actvYn(),
                requireUserId(), nowDtm14());
        if (n == 0) throw new BusinessException("WBE_CARD_TRANSACTIONS_NOT_FOUND");

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO_CARD_MSTR)
                .refNo1(bankCd)
                .refNo2(cardNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_PATCH_CARD_ACTIVE_KEY)
                .msgArgs(new Object[] { req.actvYn() })
                .build());

        log.info("feature=card-transactions patch-card-active bizKey={} bankCd={} cardNo={} actvYn={}",
                bizKey, bankCd, cardNo, req.actvYn());
    }

    /**
     * Inline-rename — updates {@code CARD_HLDR_NM} + {@code CARD_NICK_NM}.
     * Mirrors legacy {@code card_0001_003_u002}. Both columns ride
     * together; the frontend sends the row's current value for the
     * field the user didn't edit.
     */
    @Transactional
    public void updateCardInfo(
            String bankCd, String cardNo,
            com.kosign.wecms.domain.payment.cardtransactions.dto.UpdateCardInfoRequest req) {
        if (req == null) throw new BusinessException("WBE_VALIDATION");
        String bizKey = requireTenant();
        int n = cardMstrRepo.patchCardInfo(
                bizKey, bankCd, cardNo,
                req.cardHldrNm(), blankToNull(req.cardNickNm()),
                requireUserId(), nowDtm14());
        if (n == 0) throw new BusinessException("WBE_CARD_TRANSACTIONS_NOT_FOUND");

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO_CARD_MSTR)
                .refNo1(bankCd)
                .refNo2(cardNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_PATCH_CARD_INFO_KEY)
                .build());

        log.info("feature=card-transactions patch-card-info bizKey={} bankCd={} cardNo={}",
                bizKey, bankCd, cardNo);
    }

    /**
     * Hard-delete one card from {@code CARD_MSTR}. Mirrors legacy
     * {@code card_0001_003_d001} including the {@code WBE1153}
     * in-use guard — when the card has any rows in {@code CARD_TRNS_HSTR}
     * the delete is rejected so historical transactions never lose
     * their parent registry entry.
     */
    @Transactional
    public void deleteCard(String bankCd, String cardNo) {
        String bizKey = requireTenant();

        long usage = cardTrnsRepo.countByBizKeyAndBankCdAndCardNo(bizKey, bankCd, cardNo);
        if (usage > 0L) {
            // Legacy parity — same code the JEX side throws.
            throw new BusinessException("WBE1153");
        }

        int n = cardMstrRepo.deleteByBizKeyAndBankCdAndCardNo(bizKey, bankCd, cardNo);
        if (n == 0) throw new BusinessException("WBE_CARD_TRANSACTIONS_NOT_FOUND");

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO_CARD_MSTR)
                .refNo1(bankCd)
                .refNo2(cardNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_DELETE_CARD_KEY)
                .build());

        log.info("feature=card-transactions delete-card bizKey={} bankCd={} cardNo={}",
                bizKey, bankCd, cardNo);
    }

    /**
     * Bulk-insert scraped cards into {@code CARD_MSTR} — backs the
     * Card Management modal's Add Card flow (legacy
     * {@code comm_0218_007_c001}). The frontend posts the array
     * the user ticked in the "Confirm Cards" popup after the NXISAS
     * scrape returned them.
     *
     * <p>Per-card dedupe runs on the composite PK
     * {@code (BIZ_KEY, BANK_CD, CARD_NO)}: rows already present in the
     * tenant are silently skipped rather than throwing. Mirrors the
     * legacy behaviour where re-running the registration for an
     * already-registered card is a no-op (so the user can re-scrape a
     * bank without orphaning their previous cards).
     *
     * <p>Newly-inserted rows default to {@code ACTV_YN='Y'}. The
     * {@code BANK_CERT_SEQ} is written through when the request carried
     * one — letting the future Card Transactions ledger refresh
     * re-authenticate against the saved {@code BANK_CERT_INFO} row.
     *
     * <p>Audit: one row per call (not per inserted card — would be too
     * noisy when registering 10+ cards from one scrape). {@code refNo1}
     * carries the {@code bank_cd}; the inserted card list is captured
     * by the audit message arg as a count.
     */
    @Transactional
    public com.kosign.wecms.domain.payment.cardtransactions.dto.CreateCardsResponse createCards(
            com.kosign.wecms.domain.payment.cardtransactions.dto.CreateCardsRequest req) {
        if (req == null) throw new BusinessException("WBE_VALIDATION");
        String bizKey = requireTenant();
        String userId = requireUserId();
        String now    = nowDtm14();

        int inserted = 0;
        int skipped  = 0;

        // Auto-link fallback — when the request omits bankCertSeq
        // (current Add Card flow doesn't surface the SEQ_NO from the
        // scrape session back to the frontend), look up the most-
        // recent BANK_CERT_INFO row for (biz_key, bank_cd) and link
        // every new card to it. Without this, cards land with
        // BANK_CERT_SEQ NULL and `findActiveCardsForSync` (used by
        // the Update / scrape flow) silently excludes them — the
        // canonical "scrape returns cards_attempted=0" symptom.
        // Mirrors legacy behaviour where the popup that scraped the
        // card list and the popup that registered it both held the
        // BANK_CERT_SEQ in client memory.
        BigDecimal resolvedCertSeq = null;
        if (req.bankCertSeq() != null) {
            resolvedCertSeq = BigDecimal.valueOf(req.bankCertSeq());
        } else {
            resolvedCertSeq = bankCertInfoRepo
                    .findFirstByBizKeyAndBankCdOrderBySeqNoAsc(bizKey, req.bankCd())
                    .map(x -> x.getSeqNo())
                    .orElse(null);
            if (resolvedCertSeq != null) {
                log.info("feature=card-transactions create-cards :: auto-linked bankCertSeq={} bankCd={} (request omitted)",
                        resolvedCertSeq, req.bankCd());
            } else {
                log.warn("feature=card-transactions create-cards :: NO BANK_CERT_INFO row for bizKey={} bankCd={} — cards will be created with bank_cert_seq NULL and will NOT scrape (Update returns cards_attempted=0)",
                        bizKey, req.bankCd());
            }
        }

        for (var item : req.cards()) {
            // PK existence check — extends the registry only when new.
            var pk = new com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstrId();
            pk.setBizKey(bizKey);
            pk.setBankCd(req.bankCd());
            pk.setCardNo(item.cardNo());
            if (cardMstrRepo.existsById(pk)) {
                // If the existing row is orphaned (bank_cert_seq IS
                // NULL) and we have a resolved cert, link it now so
                // re-running Add Card after a credential exists
                // un-orphans the row. Mirrors the legacy upsert
                // behavior implicitly.
                if (resolvedCertSeq != null) {
                    CardMstr existing = cardMstrRepo.findById(pk).orElse(null);
                    if (existing != null && existing.getBankCertSeq() == null) {
                        existing.setBankCertSeq(resolvedCertSeq);
                        existing.setModId(userId);
                        existing.setModDtm(now);
                        cardMstrRepo.save(existing);
                        log.info("feature=card-transactions create-cards :: backfilled bank_cert_seq={} on existing orphan card_no={}",
                                resolvedCertSeq, item.cardNo());
                    }
                }
                skipped++;
                continue;
            }

            CardMstr c = new CardMstr();
            c.setBizKey(bizKey);
            c.setBankCd(req.bankCd());
            c.setCardNo(item.cardNo());
            c.setCardTypeCd(item.cardTypeCd());
            c.setIssuDt(blankToNull(item.issuDt()));
            c.setExpiDt(blankToNull(item.expiDt()));
            c.setCardNm(blankToNull(item.cardNm()));
            c.setCardNickNm(blankToNull(item.cardNickNm()));
            c.setCardHldrNm(item.cardHldrNm());
            // Scrape inquiry status is set by the future Card Transactions
            // refresh flow; left null on create so the registry doesn't
            // look like it's already been queried.
            c.setActvYn("Y");
            c.setRegId(userId);
            c.setRegDtm(now);
            c.setModId(userId);
            c.setModDtm(now);
            c.setNxisasAcctType(blankToNull(item.nxisasAcctType()));
            if (resolvedCertSeq != null) {
                c.setBankCertSeq(resolvedCertSeq);
            }
            c.setPymnAcctNo(blankToNull(item.pymnAcctNo()));

            cardMstrRepo.save(c);
            inserted++;
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO_CARD_MSTR)
                .refNo1(req.bankCd())
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_CREATE_CARDS_KEY)
                .msgArgs(new Object[] { inserted, skipped })
                .build());

        log.info("feature=card-transactions create-cards bizKey={} bankCd={} inserted={} skipped={}",
                bizKey, req.bankCd(), inserted, skipped);

        return new com.kosign.wecms.domain.payment.cardtransactions.dto.CreateCardsResponse(inserted, skipped);
    }

    /** Project one {@link CardMstr} entity into the modal-facing
     *  {@code CardManagementRow} DTO. {@code BANK_NM} comes from the
     *  {@link BankInfmRepository} cache (matching the existing
     *  ledger-render path); raw {@code mod_dtm} ships unchanged so the
     *  frontend can render "X ago" / locale-formatted timestamps. */
    private com.kosign.wecms.domain.payment.cardtransactions.dto.CardManagementRow toManagementRow(CardMstr c) {
        return new com.kosign.wecms.domain.payment.cardtransactions.dto.CardManagementRow(
                c.getBankCd(),
                resolveBankName(c.getBankCd()),
                c.getCardNo(),
                c.getCardNm(),
                c.getCardNickNm(),
                c.getCardHldrNm(),
                c.getActvYn(),
                c.getModId(),
                c.getModDtm());
    }

    /**
     * Request Approval for the selected card-transaction rows.
     * Mirrors legacy {@code card_0001_001.ts:661-676} which routed
     * every {@code RequestApproval}-button click through
     * {@code comm_0194_001.act} ({@code APPR_DV='33'}).
     *
     * <p>Stamps the chosen {@code APPR_LINE_NO} (rendered as a
     * 15-char numeric string) onto every selected
     * {@code CARD_TRNS_HSTR.APPR_SEQ_NO}. Rows that already carry
     * an {@code APPR_SEQ_NO} are skipped (front-end already
     * disables Request Approval in that case but we defend in
     * depth).
     *
     * <p><b>Sprint 0 scope.</b> A faithful port of legacy comm_0194_001
     * would ALSO create an approval-history row in
     * {@code EXPT_RESOL_MSTR / _DETL}. That table family isn't yet
     * migrated locally; this endpoint ships the
     * {@code CARD_TRNS_HSTR.APPR_SEQ_NO} stamp only so the front-end
     * "Approval Ref." column populates and the row visually moves
     * from APPROVAL NOT REQUESTED to APPROVAL REQUESTED. The full
     * approval-history persistence lands when EXPT_RESOL_MSTR is
     * ported.
     */
    @Transactional
    public com.kosign.wecms.domain.payment.cardtransactions.dto.RequestApprovalResponse requestApproval(
            com.kosign.wecms.domain.payment.cardtransactions.dto.RequestApprovalRequest req) {
        if (req == null) {
            throw new BusinessException("WBE_VALIDATION", "request body is required");
        }
        String bizKey = requireTenant();

        // Resolve the rows we're stamping FIRST so we can compute the
        // VND total + skip defensively before writing anything.
        // Legacy disables the Request Approval button when any selected
        // row already has an APPR_SEQ_NO (`card_0001_001.ts:650`);
        // re-assert that here for stale tabs.
        List<CardTrnsHstr> stampable = new java.util.ArrayList<>();
        int skipped = 0;
        for (String cardSeqNo : req.cardSeqNos()) {
            if (cardSeqNo == null || cardSeqNo.isBlank()) {
                skipped++;
                continue;
            }
            var ent = cardTrnsRepo.findById(
                    new com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstrId(
                            bizKey, cardSeqNo.trim()))
                    .orElse(null);
            if (ent == null) {
                skipped++;
                continue;
            }
            if (ent.getApprSeqNo() != null && !ent.getApprSeqNo().isBlank()) {
                skipped++;
                continue;
            }
            stampable.add(ent);
        }

        // Load the picked approval line's approver chain — fans out
        // into EXPT_RESOL_APPRVR_INFM so the Approval Box list query
        // surfaces the "Approver (X/Y)" chip. Card's wire DTO carries
        // only `appr_line_no` (the other 5 Payment modules ship the
        // full chain on the wire); we resolve from DB instead.
        List<ApprovalLineApprover> chainEntities = apprLineApproverRepo
                .findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(bizKey, req.apprLineNo());
        if (chainEntities.isEmpty()) {
            throw new BusinessException("WBE_VALIDATION",
                    "appr_line_no: no approvers found for the picked line");
        }
        List<ApprovalBoxFanoutService.Approver> chain = chainEntities.stream()
                .map(a -> new ApprovalBoxFanoutService.Approver(a.getApprvrId(), a.getApprvrNm()))
                .toList();

        if (stampable.isEmpty()) {
            // Nothing to stamp — short-circuit without touching
            // EXPT_RESOL_MSTR. Returns a no-op response so the
            // frontend can still surface the "skipped: N" count.
            return new com.kosign.wecms.domain.payment.cardtransactions.dto.RequestApprovalResponse(
                    0, skipped, "");
        }

        BigDecimal totalVndAmt = stampable.stream()
                .map(CardTrnsHstr::getTrscAmt)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Synthesise the APPR_SEQ_NO via per-tenant MAX+1 (Java-side,
        // no wb_get_next_val). One retry handles the rare race —
        // mirrors Internal Transfer / Expense / Vendor / Payroll.
        String apprSeqNo = allocateApprSeqNo(bizKey);
        try {
            stampApproval(stampable, apprSeqNo, totalVndAmt, chain);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            log.info("feature=card-transactions apprSeqNo race retrying once bizKey={}", bizKey);
            apprSeqNo = allocateApprSeqNo(bizKey);
            stampApproval(stampable, apprSeqNo, totalVndAmt, chain);
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(apprSeqNo)
                .refNo2(String.valueOf(req.apprLineNo()))
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_REQUEST_APPROVAL_KEY)
                .build());

        log.info(
                "feature=card-transactions request-approval bizKey={} apprLineNo={} apprSeqNo={} updated={} skipped={}",
                bizKey, req.apprLineNo(), apprSeqNo, stampable.size(), skipped);

        return new com.kosign.wecms.domain.payment.cardtransactions.dto.RequestApprovalResponse(
                stampable.size(), skipped, apprSeqNo);
    }

    /** Per-tenant MAX(APPR_SEQ_NO::bigint) + 1 from the shared
     *  EXPT_RESOL_MSTR namespace. Mirrors the allocator used by every
     *  other Payment module (Internal Transfer / Vendor / Expense /
     *  Payroll / Overseas) so the Approval Box id space is contiguous
     *  across all six modules — they read MAX from
     *  {@code SCDUL_PYMN_SUMR}, we read from {@code EXPT_RESOL_MSTR}
     *  because Card Transactions has no SCDUL parent table; both
     *  paths feed into the same {@code EXPT_RESOL_MSTR} INSERT below. */
    private String allocateApprSeqNo(String bizKey) {
        Long max = exptResolMstrRepo.findMaxApprSeqNoByBizKey(bizKey);
        long next = (max == null ? 0L : max) + 1L;
        return Long.toString(next);
    }

    /** Stamp + fan-out helper — separated from the caller so the
     *  retry path doesn't duplicate the writes. Mirrors the
     *  {@code stampApproval} private on every other Payment module. */
    private void stampApproval(
            List<CardTrnsHstr> stampable,
            String apprSeqNo,
            BigDecimal totalVndAmt,
            List<ApprovalBoxFanoutService.Approver> chain) {

        String userId = requireUserId();
        String now    = nowDtm14();

        // Fan out FIRST so the EXPT_RESOL_MSTR INSERT races (PK
        // collision) before any CARD_TRNS_HSTR rows are mutated.
        // Card-transaction-specific notes:
        //   - title = "Card Approval" (legacy DEV samples carry this
        //     literal — the wire has no per-batch title field).
        //   - docNo = apprSeqNo (no batch doc id like SCDUL_PYMN_NO).
        //   - payDate = null (card transactions are post-purchase,
        //     not scheduled).
        approvalBoxFanoutService.fanout(
                ApprDv.CORPORATE_CARD,
                apprSeqNo,
                "Card Approval",
                apprSeqNo,
                totalVndAmt,
                null,
                chain);

        // Stamp APPR_SEQ_NO on every selected card row. After this the
        // row's status flips from APPROVAL NOT REQUESTED to APPROVAL
        // REQUESTED in the summary tiles.
        for (CardTrnsHstr ent : stampable) {
            ent.setApprSeqNo(apprSeqNo);
            ent.setModId(userId);
            ent.setModDtm(now);
            cardTrnsRepo.save(ent);
        }
    }

    /** Build the {@code Sort} for the management list — primary order
     *  from the whitelist, default tiebreakers always appended. */
    private Sort buildManagementSort(String sortField, String sortDirection) {
        Optional<CardMstrSortField> primary = CardMstrSortField.parse(sortField);
        if (primary.isEmpty()) return MGMT_DEFAULT_SORT;
        SortDirection dir = SortDirection.parse(sortDirection);
        Sort.Direction springDir = dir == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(springDir, primary.get().entityProperty()).and(MGMT_DEFAULT_SORT);
    }

    /* ----------------------------------------------------------------
     *  Single-field PATCH  (legacy card_0001_001_u001)
     * ---------------------------------------------------------------- */

    /**
     * Apply the discriminated patch to one card-transaction row.
     * Routes the {@code field} discriminator to the matching narrow
     * repository writer; writes one audit row per successful change.
     *
     * @throws BusinessException {@code WBE_CARD_TRANSACTIONS_NOT_FOUND}
     *         when the PK doesn't resolve in the tenant (collapses
     *         missing-row + wrong-tenant into one envelope).
     */
    @Transactional
    public void patch(String cardSeqNo, CardTransactionPatchRequest body) {
        if (body == null) {
            throw new BusinessException("WBE_VALIDATION");
        }
        String bizKey = requireTenant();
        String modId  = requireUserId();
        String modDtm = nowDtm14();

        int rows;
        String detailKey;
        if (body instanceof PatchDescriptionRequest d) {
            rows = cardTrnsRepo.patchDescription(bizKey, cardSeqNo,
                    blankToNull(d.value()), modId, modDtm);
            detailKey = AUDIT_PATCH_DESCRIPTION_KEY;
        } else if (body instanceof PatchPurposeRequest p) {
            rows = cardTrnsRepo.patchPurpose(bizKey, cardSeqNo,
                    blankToNull(p.value()), modId, modDtm);
            detailKey = AUDIT_PATCH_PURPOSE_KEY;
        } else {
            // Unreachable — the sealed interface + JSON discriminator
            // restricts the type at deserialisation. Defensive throw
            // keeps the compiler happy and surfaces a clear envelope
            // if a future variant lands without being wired here.
            throw new BusinessException("WBE_VALIDATION");
        }

        if (rows == 0) {
            throw new BusinessException("WBE_CARD_TRANSACTIONS_NOT_FOUND");
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(cardSeqNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(detailKey)
                .build());

        log.info("feature=card-transactions patch bizKey={} cardSeqNo={} field={}",
                bizKey, cardSeqNo, body.field());
    }

    /* ----------------------------------------------------------------
     *  Update reporting account  (legacy card_0001_001_u002)
     * ---------------------------------------------------------------- */

    @Transactional
    public void updateReportingAccount(String cardSeqNo, UpdateReportingAccountRequest req) {
        if (req == null) {
            throw new BusinessException("WBE_VALIDATION");
        }
        String bizKey = requireTenant();

        // Validate the picked chart-of-accounts row exists, is active, and
        // the (seqNo, code) pair points at a single row. Same shape as
        // TransactionService.updateReportingAccount — the UK on
        // CHART_ACCT_INFM (BIZ_KEY, CHART_ACCT_CD, DEL_YN='N') makes the
        // lookup O(1).
        ChartOfAccount chart = chartAcctRepo
                .findByBizKeyAndChartAcctCdAndDelYn(bizKey, req.chartAcctCd(), "N")
                .orElseThrow(() -> new BusinessException("WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID"));
        if (!chart.getChartAcctSeqNo().equals(req.chartAcctSeqNo())) {
            throw new BusinessException("WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID");
        }
        if (!"A".equals(chart.getStts())) {
            throw new BusinessException("WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID");
        }

        int rows = cardTrnsRepo.patchReportingAccount(
                bizKey, cardSeqNo,
                req.chartAcctSeqNo(), req.chartAcctCd(),
                requireUserId(), nowDtm14());
        if (rows == 0) {
            throw new BusinessException("WBE_CARD_TRANSACTIONS_NOT_FOUND");
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(cardSeqNo)
                .refNo2(req.chartAcctCd())
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_UPDATE_REPORTING_ACCOUNT_KEY)
                .build());

        log.info("feature=card-transactions update-reporting bizKey={} cardSeqNo={} chartAcctCd={}",
                bizKey, cardSeqNo, req.chartAcctCd());
    }

    /* ----------------------------------------------------------------
     *  Helpers
     * ---------------------------------------------------------------- */

    private CardTransactionListFilters buildFilters(
            String fromDt, String toDt, String srchWd,
            List<String> bankCds, List<String> cardNos, String linkYn) {

        validateDateRange(fromDt, toDt);
        String pattern = buildLikePattern(srchWd);
        String linkYnNorm = blankToNull(linkYn);
        if (linkYnNorm != null) linkYnNorm = linkYnNorm.toUpperCase(Locale.ROOT);
        if (linkYnNorm != null && !linkYnNorm.equals("Y") && !linkYnNorm.equals("N")) {
            linkYnNorm = null;
        }
        List<String> bankCdsNorm = trimNonEmpty(bankCds);
        List<String> cardNosNorm = trimNonEmpty(cardNos);
        return new CardTransactionListFilters(
                fromDt, toDt, bankCdsNorm, cardNosNorm, pattern, linkYnNorm);
    }

    private Pageable buildPageable(int page, int size, String sortField, String sortDirection) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, buildSort(sortField, sortDirection));
    }

    /** Build the {@code Sort} from the wire sort_field / sort_direction
     *  pair — primary order from the whitelist, then the deterministic
     *  default tiebreakers. Same shape as
     *  {@code AccountService#list} default-sort composition. */
    private Sort buildSort(String sortField, String sortDirection) {
        Optional<CardTrnsHstrSortField> primary = CardTrnsHstrSortField.parse(sortField);
        if (primary.isEmpty()) return DEFAULT_SORT;
        SortDirection dir = SortDirection.parse(sortDirection);
        Sort.Direction springDir = dir == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(springDir, primary.get().entityProperty()).and(DEFAULT_SORT);
    }

    private CardTransactionRow toRow(CardTrnsHstr e, Map<String, String> bankNmByCd) {
        // CardMstr LEFT JOIN columns — Sprint 0 fetches the registry
        // names lazily per (bankCd, cardNo) on a per-page basis. With
        // typical pages of 15 rows and a low-cardinality cards table,
        // the optional per-row miss is fine. When the legacy
        // CARD_MSTR_R002 JOIN columns are needed in volume, lift the
        // resolver to a request-scoped batch read.
        ActiveCard registry = resolveCardRegistryEntry(e.getBizKey(), e.getBankCd(), e.getCardNo());
        return new CardTransactionRow(
                e.getCardSeqNo(),
                e.getBankCd(),
                bankNmByCd.getOrDefault(e.getBankCd(), ""),
                e.getCardNo(),
                registry != null ? registry.cardNm()      : e.getCardNm(),
                registry != null ? registry.cardNickNm() : null,
                registry != null ? registry.cardHldrNm() : null,
                e.getMerchNm(),
                e.getCcyCd(),
                e.getTrscAmt(),
                e.getTrscDt(),
                e.getTrscTm(),
                e.getCardTrnsUsageCode(),
                e.getChartAcctSeqNo(),
                e.getChartAcctCd(),
                e.getCont(),
                e.getApprSeqNo(),
                e.getPrchStts(),
                resolveSttsKey(e.getPrchStts()),
                List.<LinkedDocRef>of(),       // Phase B placeholder — see service Javadoc
                List.<ApproverRef>of(),        // Phase B placeholder
                e.getRegId(),
                e.getRegDtm(),
                e.getModId(),
                e.getModDtm());
    }

    private ActiveCard resolveCardRegistryEntry(String bizKey, String bankCd, String cardNo) {
        return cardMstrRepo.findById(
                new com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstrId(
                        bizKey, bankCd, cardNo))
                .map(c -> new ActiveCard(
                        c.getBankCd(), null,
                        c.getCardNo(), c.getCardNm(), c.getCardNickNm(), c.getCardHldrNm()))
                .orElse(null);
    }

    private static CardTransactionTotals toTotals(LedgerTotalsProjection raw) {
        if (raw == null) {
            return new CardTransactionTotals(0L, BigDecimal.ZERO,
                    0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO);
        }
        return new CardTransactionTotals(
                nz(raw.getTotalCount()),
                nzero(raw.getTotlAmt()),
                nz(raw.getTotlApprReqCnt()),
                nzero(raw.getTotlApprReqAmt()),
                nz(raw.getTotlApprNotReqCnt()),
                nzero(raw.getTotlApprNotReqAmt()));
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static BigDecimal nzero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Map<String, String> resolveBankNames(List<CardTrnsHstr> rows) {
        Map<String, String> by = new HashMap<>();
        for (CardTrnsHstr r : rows) {
            by.computeIfAbsent(r.getBankCd(), this::resolveBankName);
        }
        return by;
    }

    /** Single-bank-code → display-name lookup. {@code BANK_INFM} is a
     *  small registry; the JPA second-level cache + Hibernate read pool
     *  handle the per-request load fine without an explicit static
     *  cache. Returns the raw code when the registry has no entry. */
    private String resolveBankName(String bankCd) {
        if (bankCd == null) return "";
        return bankInfmRepo.findById(bankCd)
                .map(BankInfm::getRprsBankNm)
                .orElse(bankCd);
    }

    /** {@code payment.card_transactions.status.<code>} when the code is
     *  non-blank — otherwise null. Frontend renders via {@code t(key)}
     *  with fallback to the raw code per OQ-3. */
    private static String resolveSttsKey(String stts) {
        if (stts == null) return null;
        String trimmed = stts.trim();
        if (trimmed.isEmpty()) return null;
        return STTS_KEY_PREFIX + trimmed;
    }

    private static String requireUserId() {
        String userId = TenantContext.currentUserId();
        if (userId == null) {
            throw new IllegalStateException(
                    "TenantContext.userId is not populated — endpoint requires authentication");
        }
        return userId;
    }

    private static String requireTenant() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) {
            throw new IllegalStateException(
                    "TenantContext.bizKey is not populated — endpoint requires authentication");
        }
        return bizKey;
    }

    private static String nowDtm14() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM14);
    }

    private static void validateDateRange(String fromDt, String toDt) {
        // Legacy parity (card_0001_001.ts:521-523 / `card_0001_001_r001_act.jsp`):
        // only validates start <= end + the wire-shape regex. The
        // earlier 365-day cap was a refactor-only addition with no
        // legacy precedent — removed 2026-05-20 so the list endpoint
        // matches the live JEX system's filter semantics.
        if (fromDt == null || !fromDt.matches("^\\d{8}$")
                || toDt == null || !toDt.matches("^\\d{8}$")) {
            throw new BusinessException("WBE_CARD_TRANSACTIONS_PERIOD_INVALID");
        }
        if (fromDt.compareTo(toDt) > 0) {
            throw new BusinessException("WBE_CARD_TRANSACTIONS_PERIOD_INVALID");
        }
    }

    /** Build a lowercase {@code "%foo%"} LIKE pattern from a raw search
     *  keyword. Returns {@code null} when the input is blank so the JPQL
     *  {@code IS NULL} short-circuit branches cleanly. */
    private static String buildLikePattern(String raw) {
        String trimmed = blankToNull(raw);
        if (trimmed == null) return null;
        return "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> trimNonEmpty(List<String> in) {
        if (in == null || in.isEmpty()) return List.of();
        List<String> out = new java.util.ArrayList<>(in.size());
        for (String s : in) {
            String t = blankToNull(s);
            if (t != null) out.add(t);
        }
        return out;
    }
}
