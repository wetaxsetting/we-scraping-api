package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.domain.payment.cardtransactions.entity.BusaDocRcvPayHis;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsRcvPayInfm;
import com.kosign.wecms.domain.payment.cardtransactions.entity.MemoInfm;
import com.kosign.wecms.domain.payment.cardtransactions.repository.BusaDocRcvPayHisRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsRcvPayInfmRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.MemoInfmRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.UsagInfmRepository;
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
import com.kosign.wecms.domain.setting.approvalline.entity.ApprLineInfmDetl;
import com.kosign.wecms.domain.setting.approvalline.repository.ApprovalLineApproverRepository;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;
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
    static final String AUDIT_WITHDRAW_APPROVAL_KEY = "audit.card_transactions.withdraw_approval.detail";
    static final String AUDIT_CHANGE_APPROVAL_LINE_KEY =
            "audit.card_transactions.change_approval_line.detail";

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
    /** EXPT_RESOL_MSTR access (Approval Box master). APPR_SEQ_NO is
     *  allocated through the wb_get_next_val funnel (see
     *  {@link #allocateApprSeqNo}), NOT via a Java MAX+1 on this repo. */
    private final ExptResolMstrRepository          exptResolMstrRepo;
    /** Approver-chain repo — used by the change-approval-line and
     *  withdraw-approval paths to replace / delete the per-approver
     *  rows alongside the master row. */
    private final com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository
                                                   exptResolApprvrRepo;
    /** Resolves the approver chain for the picked approval line. */
    private final ApprovalLineApproverRepository   apprLineApproverRepo;
    /** Writes EXPT_RESOL_MSTR + EXPT_RESOL_APPRVR_INFM rows so the
     *  approval surfaces in {@code /payment/approval-box}. */
    private final ApprovalBoxFanoutService         approvalBoxFanoutService;
    /** M-1 (legacy parity): shared Request Approval attachment writer (EXPT_RESOL_FILE). */
    private final com.kosign.wecms.domain.payment.approvalbox.service.ApprovalAttachmentWriter approvalAttachmentWriter;
    /** Shared request-approval bulletin / notification writer (BLTB_* tables). */
    private final com.kosign.wecms.domain.payment.notification.service.ApprovalNotificationService approvalNotificationService;
    /** Reads e-Invoice rows for the Link Card Payments popup's
     *  Expenses tab (legacy {@code card_0001_002_r001}). */
    private final BusaDocMstrRepository            busaDocMstrRepo;
    /** Card-link master writes (legacy {@code card_0001_002_c001}). */
    private final CardTrnsRcvPayInfmRepository     cardTrnsRcvPayRepo;
    /** Per-doc card-link writes (legacy {@code card_0001_002_c001}). */
    private final BusaDocRcvPayHisRepository       busaDocRcvPayHisRepo;
    /** Purpose dictionary (legacy {@code prch_0011_001} USAG combo). */
    private final UsagInfmRepository               usagInfmRepo;
    /** Polymorphic memo storage (legacy
     *  {@code wabooks.dao.MemoInfmDAO} / `MEMO_INFM_C001 / R001 / D001`). */
    private final MemoInfmRepository               memoInfmRepo;
    /** Resolves {@code USER_INFM.USER_NM} for memo author display
     *  names. JpaRepository keyed by {@code USER_ID}. */
    private final com.kosign.wecms.domain.auth.repository.UserInfmRepository userInfmRepo;

    /** Single funnel for legacy wb_get_next_val key allocation (V140):
     *  BUSA_DOC_RCV_PAY_HIS.rcv_pay_his_seq_no (#29) + the shared
     *  EXPT_RESOL_MSTR.appr_seq_no global sequence (#1). */
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    /** Legacy {@code WEB_SVC_ID} value the side panel scopes memos by
     *  on the Card Transactions page. Slug-based per workspace
     *  CLAUDE.md "Refactor identifier naming" (the legacy used
     *  {@code "card_0001_001"}; the refactor swaps to the route slug). */
    private static final String MEMO_WEB_SVC_ID_CARD = "card-transactions";

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

        // Bank/card multi-select filters (audit D2): applied to BOTH the
        // page and the totals query so the grid and the dashboard tiles
        // stay consistent. Boolean flag + non-empty sentinel keeps the
        // bound IN list valid SQL when the filter is absent.
        boolean filterBank = hasFilter(f.bankCds());
        boolean filterCard = hasFilter(f.cardNos());
        Page<CardTrnsHstr> entityPage = cardTrnsRepo.findLedgerPage(
                bizKey, f.fromDt(), f.toDt(),
                filterBank, inParam(f.bankCds()),
                filterCard, inParam(f.cardNos()),
                f.srchWdPattern(), f.linkYn(),
                pageable);

        LedgerTotalsProjection raw = cardTrnsRepo.findLedgerTotals(
                bizKey, f.fromDt(), f.toDt(),
                filterBank, inParam(f.bankCds()),
                filterCard, inParam(f.cardNos()),
                f.srchWdPattern(), f.linkYn());

        // Pre-resolve bank names for the rows on this page so the row
        // mapper doesn't fan out one cache hit per row inside the stream.
        Map<String, String> bankNmByCd = resolveBankNames(entityPage.getContent());
        Map<String, String> docNoByApprSeqNo = resolveDocNos(entityPage.getContent());
        Map<String, List<LinkedDocRef>> linkedDocsByCardSeqNo = resolveLinkedDocs(entityPage.getContent());

        List<CardTransactionRow> rows = entityPage.getContent().stream()
                .map(e -> toRow(e, bankNmByCd, docNoByApprSeqNo, linkedDocsByCardSeqNo))
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
                hasFilter(f.bankCds()), inParam(f.bankCds()),
                hasFilter(f.cardNos()), inParam(f.cardNos()),
                f.srchWdPattern(), f.linkYn(),
                pageable);

        Map<String, String> bankNmByCd = resolveBankNames(page.getContent());
        Map<String, String> docNoByApprSeqNo = resolveDocNos(page.getContent());
        Map<String, List<LinkedDocRef>> linkedDocsByCardSeqNo = resolveLinkedDocs(page.getContent());
        return page.getContent().stream()
                .map(e -> toRow(e, bankNmByCd, docNoByApprSeqNo, linkedDocsByCardSeqNo))
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
        Map<String, String> docNoByApprSeqNo = resolveDocNos(entities);
        Map<String, List<LinkedDocRef>> linkedDocsByCardSeqNo = resolveLinkedDocs(entities);
        return entities.stream()
                .map(e -> toRow(e, bankNmByCd, docNoByApprSeqNo, linkedDocsByCardSeqNo))
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
                        c.getCardHldrNm(),
                        c.getTrnLstSucDtm()))
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
            com.kosign.wecms.domain.payment.cardtransactions.dto.RequestApprovalRequest req,
            java.util.List<org.springframework.web.multipart.MultipartFile> attachments) {
        if (req == null) {
            throw new BusinessException("WBE_VALIDATION", "request body is required");
        }
        String bizKey = requireTenant();

        // Resolve the rows we're stamping FIRST so we can compute the
        // VND total + skip defensively before writing anything.
        // Legacy disables the Request Approval button when any selected
        // row already has an APPR_SEQ_NO (`card_0001_001.ts:650`);
        // re-assert that here for stale tabs.
        List<StampTarget> stampable = new java.util.ArrayList<>();
        int skipped = 0;
        for (com.kosign.wecms.domain.payment.cardtransactions.dto.RequestApprovalRequest.CardApprovalItem item
                : req.cards()) {
            if (item == null || item.cardSeqNo() == null || item.cardSeqNo().isBlank()) {
                skipped++;
                continue;
            }
            var ent = cardTrnsRepo.findById(
                    new com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstrId(
                            bizKey, item.cardSeqNo().trim()))
                    .orElse(null);
            if (ent == null) {
                skipped++;
                continue;
            }
            if (ent.getApprSeqNo() != null && !ent.getApprSeqNo().isBlank()) {
                skipped++;
                continue;
            }
            stampable.add(new StampTarget(ent, item));
        }

        // Load the picked approval line's approver chain — fans out
        // into EXPT_RESOL_APPRVR_INFM so the Approval Box list query
        // surfaces the "Approver (X/Y)" chip. Card's wire DTO carries
        // only `appr_line_no` (the other 5 Payment modules ship the
        // full chain on the wire); we resolve from DB instead.
        List<ApprLineInfmDetl> chainEntities = apprLineApproverRepo
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
                .map(t -> t.ent().getTrscAmt())
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Synthesise the APPR_SEQ_NO via the wb_get_next_val funnel.
        // One retry handles the rare race — mirrors Internal Transfer /
        // Expense / Vendor / Payroll.
        String apprSeqNo = allocateApprSeqNo(bizKey);
        try {
            stampApproval(stampable, apprSeqNo, totalVndAmt, req.postTitle(), req.postCont(), req.payDate(), chain, req.apprLineNo());
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            log.info("feature=card-transactions apprSeqNo race retrying once bizKey={}", bizKey);
            apprSeqNo = allocateApprSeqNo(bizKey);
            stampApproval(stampable, apprSeqNo, totalVndAmt, req.postTitle(), req.postCont(), req.payDate(), chain, req.apprLineNo());
        }

        // M-1 (legacy parity): persist Request Approval attachments into
        // EXPT_RESOL_FILE (legacy card_0001_005_c001 processFileAttachments / REC1).
        approvalAttachmentWriter.persist(bizKey, apprSeqNo, attachments);

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

    /**
     * Withdraw a pending Card Approval — deletes the EXPT_RESOL_MSTR +
     * EXPT_RESOL_APPRVR_INFM rows and clears {@code APPR_SEQ_NO} on
     * every {@code CARD_TRNS_HSTR} row stamped with it. Mirrors legacy
     * {@code comm_0195_001.ts:1347} `deleteApproval()` → `comm_0192_004_d001`
     * scoped to a card-approval batch.
     *
     * <p>Refuses the operation when the master row is past Pending /
     * In Progress (i.e. {@code APPR_TYPE_CD ∈ {'01','02'}} — Approved
     * or Rejected) — closed approvals are immutable.
     */
    @Transactional
    public void withdrawApproval(String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "appr_seq_no: required");
        }
        String bizKey = requireTenant();
        String userId = requireUserId();
        String now    = nowDtm14();

        var mstrId = new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(
                bizKey, apprSeqNo.trim());
        var mstr = exptResolMstrRepo.findById(mstrId)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "Approval reference not found"));

        // Guard: only Pending ("00") + In Progress ("03") are
        // withdrawable. Closed approvals stay immutable.
        String typeCd = mstr.getApprTypeCd();
        if (!"00".equals(typeCd) && !"03".equals(typeCd)) {
            throw new BusinessException("WBE_VALIDATION",
                    "Only Pending or In Progress approvals can be withdrawn");
        }

        // Clear the stamp on every CARD_TRNS_HSTR row pointing at this
        // approval before nuking the master — the row returns to
        // "APPROVAL NOT REQUESTED" in the summary tiles.
        var stamped = cardTrnsRepo.findByBizKeyAndApprSeqNo(bizKey, apprSeqNo.trim());
        for (var ent : stamped) {
            ent.setApprSeqNo(null);
            ent.setModId(userId);
            ent.setModDtm(now);
        }
        cardTrnsRepo.saveAll(stamped);

        // Bulletin / attachment cleanup (legacy comm_0192_004_d001) — before
        // the master delete (EXPT_RESOL_FILE FK).
        approvalNotificationService.cleanupApprovalArtifacts(apprSeqNo.trim());

        // Delete approver rows + master.
        exptResolApprvrRepo.deleteByBizKeyAndApprSeqNo(bizKey, apprSeqNo.trim());
        exptResolMstrRepo.delete(mstr);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(apprSeqNo.trim())
                .refNo2(String.valueOf(stamped.size()))
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_WITHDRAW_APPROVAL_KEY)
                .build());

        log.info("feature=card-transactions withdraw-approval bizKey={} apprSeqNo={} cleared={}",
                bizKey, apprSeqNo, stamped.size());
    }

    /**
     * Change the approval line on a pending Card Approval — replaces
     * every {@code EXPT_RESOL_APPRVR_INFM} row with the new line's
     * approvers. {@code CARD_TRNS_HSTR.APPR_SEQ_NO} stays unchanged
     * (legacy parity — only the approver chain rotates). Mirrors
     * legacy {@code comm_0195_001.ts:278} `editApproval()` path.
     *
     * <p>Same Pending / In Progress guard as
     * {@link #withdrawApproval(String)}.
     */
    @Transactional
    public void changeApprovalLine(String apprSeqNo, Long apprLineNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "appr_seq_no: required");
        }
        if (apprLineNo == null || apprLineNo <= 0) {
            throw new BusinessException("WBE_VALIDATION", "appr_line_no: required");
        }
        String bizKey = requireTenant();
        String userId = requireUserId();
        String now    = nowDtm14();

        var mstrId = new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(
                bizKey, apprSeqNo.trim());
        var mstr = exptResolMstrRepo.findById(mstrId)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "Approval reference not found"));

        String typeCd = mstr.getApprTypeCd();
        if (!"00".equals(typeCd) && !"03".equals(typeCd)) {
            throw new BusinessException("WBE_VALIDATION",
                    "Only Pending or In Progress approvals can be edited");
        }

        // Resolve the new approver chain.
        var chainEntities = apprLineApproverRepo
                .findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(bizKey, apprLineNo);
        if (chainEntities.isEmpty()) {
            throw new BusinessException("WBE_VALIDATION",
                    "appr_line_no: no approvers found for the picked line");
        }

        // Wipe the existing approver rows and rebuild from the new
        // line. Reset master to Pending so the new first approver
        // becomes the active one.
        exptResolApprvrRepo.deleteByBizKeyAndApprSeqNo(bizKey, apprSeqNo.trim());
        exptResolApprvrRepo.flush();

        int line = 1;
        for (var a : chainEntities) {
            var row = new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm();
            row.setBizKey(bizKey);
            row.setApprSeqNo(apprSeqNo.trim());
            row.setApprvrId(a.getApprvrId());
            row.setApprvrNm(a.getApprvrNm() != null ? a.getApprvrNm() : a.getApprvrId());
            row.setApprvrStts(line == 1 ? "P" : "W");
            row.setApprvrLineNo(BigDecimal.valueOf(line));
            row.setReadYn("N");
            row.setRegId(userId);
            row.setRegDtm(now);
            row.setModId(userId);
            row.setModDtm(now);
            exptResolApprvrRepo.save(row);
            line++;
        }

        mstr.setApprTypeCd("00");  // back to Pending
        mstr.setModId(userId);
        mstr.setModDtm(now);
        exptResolMstrRepo.save(mstr);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(apprSeqNo.trim())
                .refNo2(String.valueOf(apprLineNo))
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_CHANGE_APPROVAL_LINE_KEY)
                .build());

        log.info("feature=card-transactions change-approval-line bizKey={} apprSeqNo={} newApprLineNo={} approvers={}",
                bizKey, apprSeqNo, apprLineNo, chainEntities.size());
    }

    /**
     * Preview the next {@code VO_YYMMDD_NNN} doc number for the
     * current tenant. Used by the Card Request Approval popup which
     * has no parent batch row to display while the user is filling
     * the form — every other Payment module passes the parent's
     * {@code SCDUL_PYMN_NO}.
     *
     * <p>Read-only / non-reserving: a second caller (or this caller's
     * own later submit) may end up with a higher number if other
     * Request Approvals land in between. The authoritative value is
     * assigned by {@code ApprovalBoxFanoutService.fanout(...)} at
     * submit time.
     */
    public com.kosign.wecms.domain.payment.cardtransactions.dto.NextDocNoResponse previewNextDocNo() {
        String bizKey = TenantContext.currentBizKey();
        String docNo = approvalBoxFanoutService.nextDocNo(bizKey, OffsetDateTime.now());
        return new com.kosign.wecms.domain.payment.cardtransactions.dto.NextDocNoResponse(docNo);
    }

    /**
     * List e-Invoice rows eligible to be linked to a card transaction.
     * Drives the Expenses e-Invoice tab on the
     * "Link Card Payments with the Expenses" popup
     * (legacy {@code card_0001_002_r001_act.jsp}).
     *
     * <p>Filter chain — every condition is legacy parity:
     * <ul>
     *   <li>Tenant — {@link TenantContext#currentBizKey()}</li>
     *   <li>{@code DOC_TYPE='B'} + {@code HAND_REG_YN='N'} — GDT-synced
     *       Bills only (manual receipts belong on the {@code r002} tab)</li>
     *   <li>{@code PROV_STTS NOT IN ('EE','VD')} — hides Rejected /
     *       Voided</li>
     *   <li>{@code ISSU_DT BETWEEN fromDt AND toDt}</li>
     *   <li>NOT already linked — defensive {@code NOT EXISTS} on
     *       {@code BUSA_DOC_RCV_PAY_HIS}</li>
     *   <li>Optional case-insensitive partial match on
     *       {@code TRCO_CMNM} / {@code TRCO_CMENM} / {@code DISP_DOC_ID}</li>
     * </ul>
     *
     * @param fromDt  inclusive {@code yyyyMMdd}, required
     * @param toDt    inclusive {@code yyyyMMdd}, required
     * @param search  optional keyword; null / blank skips the filter
     * @param page    0-based page index
     * @param size    page size (frontend ships 50 by default)
     */
    public com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableEInvoiceListResponse
            listLinkableEInvoices(String fromDt, String toDt, String search, int page, int size) {

        String bizKey = TenantContext.currentBizKey();
        String keywordPattern = (search == null || search.isBlank())
                ? null
                : "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";

        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 200));

        Page<BusaDocMstr> result = busaDocMstrRepo.findLinkableEInvoices(
                bizKey, fromDt, toDt, keywordPattern, pageable);

        List<com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableEInvoiceRow> rows = result
                .getContent()
                .stream()
                .map(this::toLinkableEInvoiceRow)
                .toList();

        return new com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableEInvoiceListResponse(
                result.getTotalElements(), rows);
    }

    /** Map a {@link BusaDocMstr} entity onto the wire DTO. Picks the
     *  Vietnamese merchant name first ({@code TRCO_CMNM}), falls back
     *  to the English form ({@code TRCO_CMENM}). */
    private com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableEInvoiceRow
            toLinkableEInvoiceRow(BusaDocMstr m) {
        String merchNm = (m.getTrcoCmnm() != null && !m.getTrcoCmnm().isBlank())
                ? m.getTrcoCmnm()
                : m.getTrcoCmenm();
        return new com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableEInvoiceRow(
                m.getDocId(),
                m.getIssuDt(),
                m.getDispDocId(),
                m.getEinvInvoicestatus(),
                merchNm,
                m.getTrcoTaxId(),
                m.getTotlAmt(),
                m.getTrcoTrscCcyCd());
    }

    /** Escape user-supplied {@code search} for LIKE matching — keeps
     *  {@code %} / {@code _} / {@code !} literal so the wildcard chars
     *  on either side stay the only wildcards. {@code !} is the ESCAPE
     *  char declared on the LIKE clause. */
    private static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /**
     * List manually-registered receipts eligible to be linked to a card
     * transaction. Powers the <i>Receipt</i> tab on the
     * "Link Card Payments with the Expenses" popup (legacy
     * {@code card_0001_002_r002}).
     *
     * <p>Same filter shape as {@link #listLinkableEInvoices} but
     * {@code DOC_TYPE='E'} + {@code HAND_REG_YN='Y'} and dates filter on
     * {@code USE_DT} (legacy r002 stance).
     */
    public com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableReceiptListResponse
            listLinkableReceipts(String fromDt, String toDt, String search, int page, int size) {

        String bizKey = TenantContext.currentBizKey();
        String keywordPattern = (search == null || search.isBlank())
                ? null
                : "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";

        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 200));

        Page<BusaDocMstr> result = busaDocMstrRepo.findLinkableReceipts(
                bizKey, fromDt, toDt, keywordPattern, pageable);

        List<com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableReceiptRow> rows = result
                .getContent()
                .stream()
                .map(this::toLinkableReceiptRow)
                .toList();

        return new com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableReceiptListResponse(
                result.getTotalElements(), rows);
    }

    private com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableReceiptRow
            toLinkableReceiptRow(BusaDocMstr m) {
        String merchNm = (m.getTrcoCmnm() != null && !m.getTrcoCmnm().isBlank())
                ? m.getTrcoCmnm()
                : m.getTrcoCmenm();
        // Receipts file by USE_DT; fall back to ISSU_DT when the legacy
        // import path left USE_DT null (DEV has both populated).
        String dt = (m.getUseDt() != null && !m.getUseDt().isBlank())
                ? m.getUseDt().substring(0, Math.min(8, m.getUseDt().length()))
                : m.getIssuDt();
        return new com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableReceiptRow(
                m.getDocId(),
                dt,
                merchNm,
                m.getTotlAmt(),
                m.getTrcoTrscCcyCd(),
                m.getBusaUsagCd(),
                m.getCont());
    }

    /**
     * Link one or more expenses / receipts to a card transaction. Mirrors
     * legacy {@code card_0001_002_c001_act.jsp}:
     *
     * <ol>
     *   <li>Resolve the card row from {@code CARD_TRNS_HSTR}.</li>
     *   <li>Validate {@code SUM(item.dedu_amt) == TRSC_AMT} exactly
     *       (legacy {@code WBE0001}, refactor
     *       {@code WBE_LINK_AMOUNT_MISMATCH}).</li>
     *   <li>Reject if the card row already has a master link
     *       (legacy {@code WSE0409}, refactor
     *       {@code WBE_LINK_ALREADY_LINKED}).</li>
     *   <li>Resolve every {@code doc_id} via
     *       {@link BusaDocMstrRepository#findLinkableByDocIds} — drops
     *       ids that are missing / wrong-tenant / already-linked /
     *       rejected / voided; the resolved subset must equal the
     *       request size or the call fails with
     *       {@code WBE_LINK_DOCS_UNAVAILABLE}.</li>
     *   <li>Insert one {@code CARD_TRNS_RCV_PAY_INFM} row and N
     *       {@code BUSA_DOC_RCV_PAY_HIS} rows, all in one transaction.</li>
     * </ol>
     *
     * <p>The legacy "update {@code BUSA_DOC_MSTR.PROV_STTS}" step
     * (legacy {@code updateProvStts}) is deferred — the link state is
     * authoritative on {@code BUSA_DOC_RCV_PAY_HIS} (NOT EXISTS keeps
     * linked docs out of the {@code r001/r002} pickers), so the parent
     * row's provision status isn't on the read path yet.
     */
    @Transactional
    public com.kosign.wecms.domain.payment.cardtransactions.dto.LinkExpensesResponse
            linkExpenses(com.kosign.wecms.domain.payment.cardtransactions.dto.LinkExpensesRequest req) {

        String bizKey = TenantContext.currentBizKey();
        String userId = requireUserId();
        String now    = nowDtm14();

        // 1) Resolve the card row.
        CardTrnsHstr card = cardTrnsRepo
                .findByBizKeyAndCardSeqNo(bizKey, req.cardSeqNo())
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "card_seq_no: " + req.cardSeqNo()));

        // 2) Sum / match check — the legacy WBE0001 guard.
        BigDecimal totalDedu = req.items().stream()
                .map(com.kosign.wecms.domain.payment.cardtransactions.dto.LinkExpensesRequest.Item::deduAmt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal trscAmt = card.getTrscAmt() != null
                ? card.getTrscAmt()
                : BigDecimal.ZERO;
        if (trscAmt.compareTo(totalDedu) != 0) {
            throw new BusinessException("WBE_LINK_AMOUNT_MISMATCH");
        }

        // 3) "Already linked?" guard — one master row per card.
        if (cardTrnsRcvPayRepo.existsByBizKeyAndCardSeqNo(bizKey, req.cardSeqNo())) {
            throw new BusinessException("WBE_LINK_ALREADY_LINKED");
        }

        // 4) Resolve every doc — drops missing / wrong-tenant / linked /
        //    rejected ids. Resolved subset must equal request size.
        List<String> reqDocIds = req.items().stream()
                .map(com.kosign.wecms.domain.payment.cardtransactions.dto.LinkExpensesRequest.Item::docId)
                .toList();
        List<BusaDocMstr> docs = busaDocMstrRepo.findLinkableByDocIds(bizKey, reqDocIds);
        if (docs.size() != reqDocIds.size()) {
            throw new BusinessException("WBE_LINK_DOCS_UNAVAILABLE");
        }
        Map<String, com.kosign.wecms.domain.payment.cardtransactions.dto.LinkExpensesRequest.Item> itemByDoc = new HashMap<>();
        for (var item : req.items()) {
            itemByDoc.put(item.docId(), item);
        }

        // 5) Insert master link row.
        CardTrnsRcvPayInfm master = new CardTrnsRcvPayInfm();
        master.setBizKey(bizKey);
        master.setBankCd(safe(card.getBankCd()));
        master.setCardNo(safe(card.getCardNo()));
        master.setTrscDt(safe(card.getTrscDt()));
        master.setCardSeqNo(card.getCardSeqNo());
        master.setRcvPaySeqNo(BigDecimal.ONE);
        master.setRcvPayAmt(trscAmt);
        master.setRegId(userId);
        master.setRegDtm(now);
        master.setModId(userId);
        master.setModDtm(now);
        cardTrnsRcvPayRepo.save(master);

        // 6) Insert per-doc child rows. RCV_PAY_HIS_SEQ_NO is the JEX
        //    natural key, allocated per-doc via MAX+1 — first link row
        //    for a given doc gets 1.
        // Each doc is distinct by doc_id (findLinkableByDocIds returns one row
        // per doc_id and the size guard above rejects duplicate request ids), so
        // the per-doc MAX+1 allocations are independent — collect the rows and
        // saveAll without needing a flush between iterations. Per-doc key
        // generation is unchanged.
        java.util.List<BusaDocRcvPayHis> hisRows = new java.util.ArrayList<>(docs.size());
        for (BusaDocMstr doc : docs) {
            var item = itemByDoc.get(doc.getDocId());
            // RCV_PAY_HIS_SEQ_NO via the single wb_get_next_val funnel
            // (V140 #29 — per (biz_key, doc_id) MAX+1).
            BigDecimal nextSeq = new BigDecimal(
                    legacyFunctions.nextBusaDocRcvPayHisSeqNo(bizKey, doc.getDocId()));

            BusaDocRcvPayHis row = new BusaDocRcvPayHis();
            row.setBizKey(bizKey);
            row.setDocId(doc.getDocId());
            row.setUserId(userId);
            row.setRcvPayHisSeqNo(nextSeq);
            row.setRcvPayWayCd("R"); // RcvPayWayCd.CARD ('C' is CASH) — legacy card_0001_002_c001_act.jsp:174
            row.setBankCd(safe(card.getBankCd()));
            row.setCardNo(safe(card.getCardNo()));
            row.setTrscDt(safe(card.getTrscDt()));
            row.setRefNo(card.getCardSeqNo());
            row.setDeduAmt(item.deduAmt());
            row.setRegId(userId);
            row.setRegDtm(now);
            hisRows.add(row);
        }
        busaDocRcvPayHisRepo.saveAll(hisRows);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(card.getCardSeqNo())
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey("audit.card_transactions.link_expenses.detail")
                .build());

        log.info("feature=card-transactions link-expenses bizKey={} cardSeqNo={} docs={}",
                bizKey, card.getCardSeqNo(), docs.size());

        return new com.kosign.wecms.domain.payment.cardtransactions.dto.LinkExpensesResponse(docs.size());
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    /**
     * List Purpose options for the New Receipt popup's dropdown.
     * Mirrors legacy {@code prch_0011_001.ts:91} ({@code comboDataSearch
     * 'USAG'} call with {@code USAG_GRP_CD='6'} +
     * {@code ADV_PAYM_DEBIT='N'}).
     *
     * <p>Returns {@code USE_USAG_CD} + the localized name resolved from
     * {@code USAG_INFM_LANG_DETL} per the caller's {@code Accept-Language}
     * (falls back to EN). Static / tenant-agnostic dictionary.
     */
    public List<com.kosign.wecms.domain.payment.cardtransactions.dto.PurposeOption>
            listPurposeOptions(String grpCd) {
        String resolvedGrp = (grpCd == null || grpCd.isBlank()) ? "6" : grpCd.trim();
        LangCd langCd = TenantContext.currentLangCd();
        String lang = (langCd != null ? langCd : LangCd.EN).name();
        return usagInfmRepo.findActiveByGrpCd(resolvedGrp, lang).stream()
                .map(p -> new com.kosign.wecms.domain.payment.cardtransactions.dto.PurposeOption(
                        p.getUseUsagCd(), p.getUsagNm()))
                .toList();
    }

    /**
     * Read-only detail of one receipt / bill. Backs the View Detail
     * action on the Card Transactions list's linked-doc cell
     * (mirrors legacy {@code card_0001_001.ts:337-350}
     * `.detail-receipt` / `.detail-e-invoice`).
     *
     * <p>Tenant-scoped via {@link TenantContext#currentBizKey()};
     * missing / wrong-tenant docs surface as {@code WBE_NOT_FOUND}.
     * Purpose name is resolved via {@code USAG_INFM_LANG_DETL} per
     * the caller's {@code Accept-Language} (falls back to EN, then
     * to the raw code).
     */
    /**
     * List every memo attached to one card transaction. Mirrors
     * legacy {@code MEMO_INFM_R001} called from
     * {@code card_0001_001.ts:1262-1276} (`getMemoPopup2`).
     *
     * <p>Scope: tenant + {@code WEB_SVC_ID="card-transactions"} +
     * {@code REF_NO=cardSeqNo}. Newest-first ordering so the side
     * panel can render top-down without a client sort.
     *
     * <p>Author names resolved in one batch JPA fetch against
     * {@code USER_INFM} keyed by {@code REG_ID}.
     */
    public com.kosign.wecms.domain.payment.cardtransactions.dto.MemoListResponse
            listMemos(String cardSeqNo) {

        String bizKey = TenantContext.currentBizKey();
        String userId = requireUserId();

        List<MemoInfm> rows = memoInfmRepo
                .findByBizKeyAndWebSvcIdAndRefNoOrderByRegDtmDescSeqNoDesc(
                        bizKey, MEMO_WEB_SVC_ID_CARD, cardSeqNo.trim());

        // Batch resolve author display names — one query for the page.
        java.util.Set<String> userIds = rows.stream()
                .map(MemoInfm::getRegId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, String> userNmById = new HashMap<>();
        if (!userIds.isEmpty()) {
            userInfmRepo.findAllById(userIds).forEach(u -> {
                if (u.getUserNm() != null) userNmById.put(u.getUserId(), u.getUserNm());
            });
        }

        List<com.kosign.wecms.domain.payment.cardtransactions.dto.MemoRow> list = rows.stream()
                .map(m -> new com.kosign.wecms.domain.payment.cardtransactions.dto.MemoRow(
                        m.getSeqNo(),
                        m.getMemo(),
                        m.getColor() != null ? m.getColor() : "white",
                        m.getRegId(),
                        userNmById.getOrDefault(m.getRegId(), m.getRegId()),
                        m.getRegDtm(),
                        userId.equals(m.getRegId())))
                .toList();
        return new com.kosign.wecms.domain.payment.cardtransactions.dto.MemoListResponse(
                list.size(), list);
    }

    /**
     * Add a memo to one card transaction. Mirrors legacy
     * {@code MEMO_INFM_C001} via
     * {@code wabooks.dao.MemoInfmDAO.create()}. Allocates
     * {@code SEQ_NO} via per-(tenant, page) MAX+1 with a one-shot
     * retry on PK collision (same pattern Card-link / Approval Box
     * use, no new {@code wb_get_next_val} branch).
     */
    @Transactional
    public com.kosign.wecms.domain.payment.cardtransactions.dto.MemoRow createMemo(
            com.kosign.wecms.domain.payment.cardtransactions.dto.CreateMemoRequest req) {

        String bizKey = TenantContext.currentBizKey();
        String userId = requireUserId();
        String now    = nowDtm14();
        String color  = (req.color() == null || req.color().isBlank()) ? "white" : req.color().trim();

        MemoInfm m;
        try {
            m = insertMemoOnce(bizKey, req, userId, now, color);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            log.info("feature=card-transactions memo seq race retrying once bizKey={}", bizKey);
            m = insertMemoOnce(bizKey, req, userId, now, color);
        }

        String userNm = TenantContext.currentUserNm();
        return new com.kosign.wecms.domain.payment.cardtransactions.dto.MemoRow(
                m.getSeqNo(),
                m.getMemo(),
                m.getColor(),
                m.getRegId(),
                userNm != null ? userNm : userId,
                m.getRegDtm(),
                true);
    }

    private MemoInfm insertMemoOnce(
            String bizKey,
            com.kosign.wecms.domain.payment.cardtransactions.dto.CreateMemoRequest req,
            String userId, String now, String color) {

        java.math.BigDecimal maxSeq = memoInfmRepo.findMaxSeqNoByBizKeyAndWebSvcId(
                bizKey, MEMO_WEB_SVC_ID_CARD);
        java.math.BigDecimal nextSeq = (maxSeq == null ? java.math.BigDecimal.ZERO : maxSeq)
                .add(java.math.BigDecimal.ONE);

        MemoInfm m = new MemoInfm();
        m.setBizKey(bizKey);
        m.setWebSvcId(MEMO_WEB_SVC_ID_CARD);
        m.setSeqNo(nextSeq);
        m.setMemo(req.memo().trim());
        m.setColor(color);
        m.setRefNo(req.refNo().trim());
        m.setRegId(userId);
        m.setRegDtm(now);
        m.setModId(userId);
        m.setModDtm(now);
        return memoInfmRepo.save(m);
    }

    /**
     * Delete one memo. Hard delete (legacy {@code MEMO_INFM_D001}
     * parity). Only the memo's author can delete it — refactor
     * stance, slightly tighter than legacy which lets any tenant
     * member delete. Returns silently when the memo doesn't exist
     * (idempotent).
     */
    @Transactional
    public void deleteMemo(java.math.BigDecimal seqNo) {
        String bizKey = TenantContext.currentBizKey();
        String userId = requireUserId();
        var id = new com.kosign.wecms.domain.payment.cardtransactions.entity.MemoInfmId(
                bizKey, MEMO_WEB_SVC_ID_CARD, seqNo);
        memoInfmRepo.findById(id).ifPresent(m -> {
            if (!userId.equals(m.getRegId())) {
                throw new BusinessException("WBE_MEMO_NOT_AUTHOR");
            }
            memoInfmRepo.delete(m);
        });
    }

    public com.kosign.wecms.domain.payment.cardtransactions.dto.ReceiptDetailResponse
            getReceiptDetail(String docId) {

        String bizKey = TenantContext.currentBizKey();
        var key = new com.kosign.wecms.domain.einvoice.entity.BusaDocMstrId(bizKey, docId);
        BusaDocMstr m = busaDocMstrRepo.findById(key)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "doc_id: " + docId));

        // Resolve purpose name when the doc carries a BUSA_USAG_CD —
        // reuses the same lang-aware lookup the picker uses.
        String purposeNm = null;
        if (m.getBusaUsagCd() != null && !m.getBusaUsagCd().isBlank()) {
            LangCd langCd = TenantContext.currentLangCd();
            String lang = (langCd != null ? langCd : LangCd.EN).name();
            purposeNm = usagInfmRepo
                    .findActiveByGrpCd("6", lang).stream()
                    .filter(p -> m.getBusaUsagCd().equals(p.getUseUsagCd()))
                    .map(UsagInfmRepository.PurposeProjection::getUsagNm)
                    .findFirst()
                    .orElse(null);
        }

        // For receipts USE_DT is the user-picked date; for bills ISSU_DT
        // is the GDT-supplied issue date.
        String date = "E".equals(m.getDocType())
                ? (m.getUseDt() != null && m.getUseDt().length() >= 8
                        ? m.getUseDt().substring(0, 8)
                        : m.getIssuDt())
                : m.getIssuDt();

        String contactName = (m.getTrcoCmnm() != null && !m.getTrcoCmnm().isBlank())
                ? m.getTrcoCmnm()
                : m.getTrcoCmenm();

        return new com.kosign.wecms.domain.payment.cardtransactions.dto.ReceiptDetailResponse(
                m.getDocId(),
                m.getDispDocId() != null && !m.getDispDocId().isBlank()
                        ? m.getDispDocId()
                        : m.getDocId(),
                m.getDocType(),
                date,
                contactName,
                m.getTrcoTaxId(),
                m.getTotlAmt(),
                m.getTrcoTrscCcyCd(),
                m.getBusaUsagCd(),
                purposeNm,
                m.getCont());
    }

    /**
     * Create a manually-entered receipt — legacy {@code prch_0011_001}.
     * Writes one {@code BUSA_DOC_MSTR} row with {@code DOC_TYPE='E'} +
     * {@code HAND_REG_YN='Y'}, returning the newly-allocated
     * {@code DOC_ID} so the caller can immediately select it on the
     * Receipt tab.
     *
     * <p>DOC_ID allocation: timestamp-derived ({@code "R" + yyyyMMddHHmmssSSS
     * + random suffix}) instead of {@code wb_get_next_val} — manual
     * receipts have no required legacy format, and this keeps the link
     * write off the legacy generator. Future MR can swap for a per-tenant
     * counter if the volume ever justifies one.
     *
     * <p>Phase D scope this MR: free-text contact + purpose. Phase B
     * picker dialogs against {@code TRCO_INFM} / {@code USAG_INFM} land
     * in a follow-up.
     */
    @Transactional
    public com.kosign.wecms.domain.payment.cardtransactions.dto.CreateReceiptResponse
            createReceipt(com.kosign.wecms.domain.payment.cardtransactions.dto.CreateReceiptRequest req) {

        String bizKey = TenantContext.currentBizKey();
        String userId = requireUserId();
        String now    = nowDtm14();
        String ccyCd  = (req.ccyCd() == null || req.ccyCd().isBlank()) ? "VND" : req.ccyCd().trim();

        String docId = "R" + now + String.format("%03d",
                java.util.concurrent.ThreadLocalRandom.current().nextInt(1000));

        // TRCO_KEY: real id from the Contacts picker when in Contacts
        // mode; legacy "manual mode" sentinel when free-typed. Mirrors
        // prch_0011_001.ts:478 (Manual mode hardcodes the all-9 key).
        String trcoKey = (req.trcoKey() != null && !req.trcoKey().isBlank())
                ? req.trcoKey().trim()
                : "99999999999999999999";

        BusaDocMstr m = new BusaDocMstr();
        m.setBizKey(bizKey);
        m.setDocId(docId);
        m.setDocType("E");
        m.setIssuDt(req.useDt());
        m.setUseDt(req.useDt());
        m.setIssuTaxId(req.taxId() != null ? req.taxId().trim() : "");
        m.setTrcoKey(trcoKey);
        m.setTrcoTaxId(req.taxId());
        m.setTrcoCmnm(req.contactName().trim());
        m.setTrcoTrscCcyCd(ccyCd);
        m.setTotlAmt(req.amount());
        m.setBusaUsagCd(req.purposeCd());
        m.setCont(req.description());
        m.setHandRegYn("Y");
        m.setProvStts("OP");
        m.setRegId(userId);
        m.setRegDtm(now);
        m.setModId(userId);
        m.setModDtm(now);
        busaDocMstrRepo.save(m);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo("BUSA_DOC_MSTR")
                .refNo1(docId)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey("audit.card_transactions.create_receipt.detail")
                .build());

        log.info("feature=card-transactions create-receipt bizKey={} docId={}", bizKey, docId);
        return new com.kosign.wecms.domain.payment.cardtransactions.dto.CreateReceiptResponse(docId);
    }

    /**
     * Unlink every expense / receipt previously linked to a card
     * transaction. Mirrors legacy {@code card_0001_002_u001_act.jsp}:
     * drops every {@code BUSA_DOC_RCV_PAY_HIS} row tied to the card,
     * then drops the master {@code CARD_TRNS_RCV_PAY_INFM} rows.
     *
     * <p>Idempotent on a card with no link — silently returns 0 (legacy
     * raises an error here but the refactor stance is "unlinking
     * something already unlinked is a no-op").
     *
     * <p>The legacy {@code updateProvStts} reset on {@code BUSA_DOC_MSTR}
     * is deferred — link state is authoritative on
     * {@code BUSA_DOC_RCV_PAY_HIS} (NOT EXISTS on the picker queries),
     * so PROV_STTS doesn't gate the read paths today.
     */
    @Transactional
    public int unlinkExpenses(String cardSeqNo) {
        if (cardSeqNo == null || cardSeqNo.isBlank()) {
            return 0;
        }
        String bizKey = TenantContext.currentBizKey();
        String trimmed = cardSeqNo.trim();

        int deletedChildren = busaDocRcvPayHisRepo.deleteByBizKeyAndRefNo(bizKey, trimmed);
        List<CardTrnsRcvPayInfm> masters =
                cardTrnsRcvPayRepo.findByBizKeyAndCardSeqNo(bizKey, trimmed);
        if (!masters.isEmpty()) {
            cardTrnsRcvPayRepo.deleteAll(masters);
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(trimmed)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey("audit.card_transactions.unlink_expenses.detail")
                .build());

        log.info("feature=card-transactions unlink-expenses bizKey={} cardSeqNo={} childrenDropped={}",
                bizKey, trimmed, deletedChildren);
        return deletedChildren;
    }

    /** Allocate the shared {@code EXPT_RESOL_MSTR.APPR_SEQ_NO} via the single
     *  {@code wb_get_next_val} funnel (V140 #1 — the GLOBAL {@code sq_appr_seq_no}
     *  sequence shared across every Payment module), so the Approval Box id space
     *  stays contiguous and never collides with the live JEX / WABOOKS systems.
     *  Mirrors {@code LegacyFunctionRepository.nextApprSeqNo} used by the other
     *  payment modules. */
    private String allocateApprSeqNo(String bizKey) {
        // APPR_SEQ_NO via the single wb_get_next_val funnel (V140 #1). JEX backs
        // this with the GLOBAL sq_appr_seq_no sequence shared across every Payment
        // module — so the id space stays contiguous and never collides with the
        // live JEX / WABOOKS systems. (Replaces the per-tenant cross-table MAX+1.)
        return legacyFunctions.nextApprSeqNo(bizKey);
    }

    /** Stamp + fan-out helper — separated from the caller so the
     *  retry path doesn't duplicate the writes. Mirrors the
     *  {@code stampApproval} private on every other Payment module. */
    private void stampApproval(
            List<StampTarget> stampable,
            String apprSeqNo,
            BigDecimal totalVndAmt,
            String postTitle,
            String postCont,
            String payDate,
            List<ApprovalBoxFanoutService.Approver> chain,
            Long apprLineNo) {

        String userId = requireUserId();
        String now    = nowDtm14();

        // 1. Fan out FIRST so the EXPT_RESOL_MSTR INSERT races (PK
        // collision) before any CARD_TRNS_HSTR rows are mutated.
        // Card-transaction-specific notes:
        //   - title = user-entered post_title from the Request Approval
        //     popup; falls back to "Card Approval" only when the wire
        //     field is blank (defensive — frontend already requires a
        //     non-empty title before enabling Submit).
        //   - docNo argument is ignored by fanout (overridden with
        //     ApprovalBoxFanoutService.nextDocNo → VO_YYMMDD_NNN).
        //   - payDate = user-picked Scheduled Date from the Request
        //     Approval popup ({@code yyyyMMdd}). Optional; fanout skips
        //     the column when blank.
        //   - apprLineNo is now passed through so each
        //     EXPT_RESOL_APPRVR_INFM row carries APPR_LINE_NO (legacy
        //     parity C2 — Card previously left it null).
        String titl = postTitle != null && !postTitle.isBlank()
                ? postTitle.trim()
                : "Card Approval";
        approvalBoxFanoutService.fanout(
                ApprDv.CORPORATE_CARD,
                apprSeqNo,
                titl,
                apprSeqNo,
                totalVndAmt,
                "VND",
                payDate,
                postCont,
                chain,
                apprLineNo);

        // 2. EXPT_RESOL_APPR_LINE print-header snapshot (env-gated).
        // Legacy card_0001_005_c001_act.jsp:523 createApprovalLines (H3).
        approvalBoxFanoutService.writeApprovalLineSnapshot(apprSeqNo);

        for (StampTarget t : stampable) {
            CardTrnsHstr ent = t.ent();
            com.kosign.wecms.domain.payment.cardtransactions.dto.RequestApprovalRequest.CardApprovalItem item = t.item();

            // 3. EXPT_RESOL_DETL — one row per card transaction (legacy
            // card_0001_005_c001_act.jsp:280 createExptResolDetl, loops
            // REC3). CCY_CD='VND' + DOC_TYPE='B' are legacy REC3 constants
            // (comm_0194_001.ts:1868/1881); AMT comes from the card row;
            // BUSA_USAG_CD / MEMO from the per-card wire item; legacy REC3
            // carries no DOC_ID and sends TRSC_DT="" (C1).
            approvalBoxFanoutService.writeResolutionDetail(
                    apprSeqNo,
                    null,
                    ent.getTrscAmt(),
                    "VND",
                    item.busaUsagCd(),
                    item.memo(),
                    "B",
                    null);

            // 4. Patch CARD_TRNS_HSTR — APPR_SEQ_NO + per-card chart-acct /
            // usage / memo (legacy card_0001_005_c001_act.jsp:295
            // updateCardTrnsHisData, H1). Reporting account guarded by
            // non-blank (legacy guards CHART_ACCT_CD with isNotBlank);
            // usage / memo set when the wire provides them (null = key
            // absent → leave existing value).
            //   NOTE: legacy has a bug at line 308-310 that overwrites
            //   CHART_ACCT_CD with CHART_ACCT_SEQ_NO; the refactor writes
            //   each value to its own column (deliberate correction, not
            //   a parity divergence — copying the bug would corrupt the
            //   reporting-account code).
            ent.setApprSeqNo(apprSeqNo);
            if (item.chartAcctCd() != null && !item.chartAcctCd().isBlank()) {
                ent.setChartAcctCd(item.chartAcctCd().trim());
            }
            if (item.chartAcctSeqNo() != null && !item.chartAcctSeqNo().isBlank()) {
                ent.setChartAcctSeqNo(item.chartAcctSeqNo().trim());
            }
            if (item.busaUsagCd() != null) {
                ent.setCardTrnsUsageCode(item.busaUsagCd());
            }
            if (item.memo() != null) {
                ent.setCont(item.memo());
            }
            ent.setModId(userId);
            ent.setModDtm(now);
            cardTrnsRepo.save(ent);
        }

        // 5. Bulletin / notification fan-out (legacy createBulletinPost +
        // createUserNewPostNotifications + BltbHelper.createNotificationToApprvr).
        // POST_APPR_DV='10' for card (legacy comm_0194_001.ts:1827).
        approvalNotificationService.notifyRequestApproval(
                apprSeqNo, titl, postCont, "10", "Corporate Card Payment");
    }

    /** Pairs a resolved {@link CardTrnsHstr} row with its per-card
     *  approval fields (legacy REC3 equivalent) for the stamp loop. */
    private record StampTarget(
            CardTrnsHstr ent,
            com.kosign.wecms.domain.payment.cardtransactions.dto.RequestApprovalRequest.CardApprovalItem item) {}

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
        ChartAcctInfm chart = chartAcctRepo
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

    /**
     * Clear (null out) {@code CHART_ACCT_SEQ_NO} + {@code CHART_ACCT_CD} on
     * one row. Backs the clear-first × button on the inline Reporting
     * Account cell — there is no chart-of-accounts validation because the
     * target is null. Reuses the same `patchReportingAccount` modifying
     * query with null binds, and the same audit activity / detail key as
     * the paired update (clearing is a reporting-account change).
     */
    @Transactional
    public void clearReportingAccount(String cardSeqNo) {
        String bizKey = requireTenant();

        int rows = cardTrnsRepo.patchReportingAccount(
                bizKey, cardSeqNo,
                null, null,
                requireUserId(), nowDtm14());
        if (rows == 0) {
            throw new BusinessException("WBE_CARD_TRANSACTIONS_NOT_FOUND");
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(cardSeqNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_UPDATE_REPORTING_ACCOUNT_KEY)
                .build());

        log.info("feature=card-transactions clear-reporting bizKey={} cardSeqNo={}",
                bizKey, cardSeqNo);
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

    /** True when a multi-select filter list carries at least one value. */
    private static boolean hasFilter(List<String> v) {
        return v != null && !v.isEmpty();
    }

    /** Sentinel-safe IN parameter: the repository's {@code IN :list}
     *  clause is bound even when its boolean guard is {@code false}, so
     *  the list must never be empty (an empty {@code IN ()} is invalid
     *  SQL). Returns the values when present, else a one-element list
     *  that the {@code :filterXxx = FALSE} guard never lets match. */
    private static List<String> inParam(List<String> v) {
        return hasFilter(v) ? v : List.of("");
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

    private CardTransactionRow toRow(
            CardTrnsHstr e,
            Map<String, String> bankNmByCd,
            Map<String, String> docNoByApprSeqNo,
            Map<String, List<LinkedDocRef>> linkedDocsByCardSeqNo) {
        // CardMstr LEFT JOIN columns — Sprint 0 fetches the registry
        // names lazily per (bankCd, cardNo) on a per-page basis. With
        // typical pages of 15 rows and a low-cardinality cards table,
        // the optional per-row miss is fine. When the legacy
        // CARD_MSTR_R002 JOIN columns are needed in volume, lift the
        // resolver to a request-scoped batch read.
        ActiveCard registry = resolveCardRegistryEntry(e.getBizKey(), e.getBankCd(), e.getCardNo());
        // EXPT_RESOL_MSTR.DOC_NO ("VO_YYMMDD_NNN") for the row's
        // approval reference. Null when the row hasn't been submitted
        // for approval yet, or when the matching master row was deleted.
        String apprDocNo = e.getApprSeqNo() != null && !e.getApprSeqNo().isBlank()
                ? docNoByApprSeqNo.get(e.getApprSeqNo())
                : null;
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
                apprDocNo,
                e.getPrchStts(),
                resolveSttsKey(e.getPrchStts()),
                linkedDocsByCardSeqNo.getOrDefault(e.getCardSeqNo(), List.of()),
                List.<ApproverRef>of(),        // Phase B placeholder (approvers — separate from linked docs)
                e.getRegId(),
                e.getRegDtm(),
                e.getModId(),
                e.getModDtm(),
                // GAP-CARD-FIELDS: legacy CARD_TRNS_HSTR_R002 detail fields.
                e.getUsingType(),
                e.getAuthCode(),
                e.getCity(),
                e.getNote());
    }

    /** Batch (APPR_SEQ_NO → DOC_NO) lookup against EXPT_RESOL_MSTR for
     *  the page's set of approval references. One IN-query per ledger
     *  load — keeps the per-row mapper a constant-time dictionary hit. */
    private Map<String, String> resolveDocNos(List<CardTrnsHstr> rows) {
        if (rows.isEmpty()) return Map.of();
        String bizKey = rows.get(0).getBizKey();
        java.util.Set<String> apprSeqNos = rows.stream()
                .map(CardTrnsHstr::getApprSeqNo)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (apprSeqNos.isEmpty()) return Map.of();
        Map<String, String> docByApprSeqNo = new HashMap<>();
        exptResolMstrRepo.findDocNoByApprSeqNos(bizKey, apprSeqNos)
                .forEach(arr -> {
                    String apprSeqNo = (String) arr[0];
                    String docNo     = (String) arr[1];
                    if (apprSeqNo != null) docByApprSeqNo.put(apprSeqNo, docNo);
                });
        return docByApprSeqNo;
    }

    /**
     * Batch-resolve the linked-doc list for a page of card transactions.
     * Mirrors the {@link #resolveDocNos} pattern — one query per page,
     * groups by {@code cardSeqNo}.
     *
     * <p>Each card may have 0 or more linked docs (one row per
     * {@code BUSA_DOC_RCV_PAY_HIS} entry where
     * {@code RCV_PAY_WAY_CD='R'} (CARD) and {@code REF_NO=cardSeqNo}). The
     * frontend renders the first doc id on the Link Reference cell + a
     * dropdown listing the rest.
     */
    private Map<String, List<LinkedDocRef>> resolveLinkedDocs(List<CardTrnsHstr> rows) {
        if (rows.isEmpty()) return Map.of();
        String bizKey = rows.get(0).getBizKey();
        List<String> cardSeqNos = rows.stream()
                .map(CardTrnsHstr::getCardSeqNo)
                .filter(s -> s != null && !s.isBlank())
                .toList();
        if (cardSeqNos.isEmpty()) return Map.of();

        Map<String, List<LinkedDocRef>> byCard = new HashMap<>();
        busaDocRcvPayHisRepo
                .findLinkedDocsByCardSeqNos(bizKey, cardSeqNos)
                .forEach(p -> byCard
                        .computeIfAbsent(p.getCardSeqNo(), k -> new java.util.ArrayList<>())
                        .add(new LinkedDocRef(
                                p.getDocId(),
                                p.getDispDocId(),
                                null,
                                p.getDocType(),
                                null,
                                p.getDeduAmt())));
        return byCard;
    }

    private ActiveCard resolveCardRegistryEntry(String bizKey, String bankCd, String cardNo) {
        return cardMstrRepo.findById(
                new com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstrId(
                        bizKey, bankCd, cardNo))
                .map(c -> new ActiveCard(
                        c.getBankCd(), null,
                        c.getCardNo(), c.getCardNm(), c.getCardNickNm(), c.getCardHldrNm(),
                        c.getTrnLstSucDtm()))
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
