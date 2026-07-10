package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tx.TransactionalRetry;
import org.springframework.dao.DataIntegrityViolationException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxActionResponse;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprDv;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalActionRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CurrencyTotal;
import com.kosign.wecms.domain.payment.internaltransfer.dto.DispatchRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferApprover;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferRow;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineDetail;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineInput;
import com.kosign.wecms.domain.payment.internaltransfer.dto.UpdateReportingAccountRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrId;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.service.ApprovalAttachmentLoader;
import com.kosign.wecms.domain.payment.internaltransfer.service.ApprovalContentLoader;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprDv;
import com.kosign.wecms.domain.payment.service.DocNoGenerator;
import com.kosign.wecms.domain.payment.internaltransfer.service.CutoffValidator;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferKeyGenerator;
import com.kosign.wecms.domain.payment.internaltransfer.service.ScdulPymnTypeCd;
import com.kosign.wecms.domain.payment.internaltransfer.service.CutoffCheck;
import com.kosign.wecms.domain.payment.internaltransfer.service.TransferNetwork;
import com.kosign.wecms.domain.payment.internaltransfer.service.TrnfYn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Payment &gt; Payroll Payment — Scheduled list service. Mirrors the
 * legacy {@code pymn_0005_001_r001} read path (which the legacy
 * {@code PymnHelper.pymn_0005_001_r001} parameterises by
 * {@code SCDUL_PYMN_TYPE_CD = '00'}). Payroll, Vendor, Expense and Internal
 * Transfer share the same physical {@code SCDUL_PYMN_SUMR} table, so this
 * service reuses {@link ScdulPymnSumrRepository} and the wire DTOs from
 * the {@code internaltransfer.dto} package — only the type discriminator
 * differs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollPaymentService {

    /** Audit-log {@code WSVC_ID} for every mutating endpoint. Payroll's
     *  refactor slug — never legacy {@code pymn_0005_001}. */
    static final String WSVC_ID = "payroll-payment";

    /** Sub-resource name written into the audit log's {@code REF_NO} column. */
    static final String REF_NO = "SCDUL_PYMN_SUMR";

    static final String AUDIT_ACTIVITY_KEY         = "audit.payroll_payment.activity";
    static final String AUDIT_UPDATE_KEY           = "audit.payroll_payment.update.detail";
    static final String AUDIT_DELETE_KEY           = "audit.payroll_payment.delete.detail";
    static final String AUDIT_DISPATCH_KEY         = "audit.payroll_payment.dispatch.detail";
    static final String AUDIT_REQUEST_APPROVAL_KEY = "audit.payroll_payment.request_approval.detail";
    static final String AUDIT_APPROVE_KEY          = "audit.payroll_payment.approve.detail";
    static final String AUDIT_REJECT_KEY           = "audit.payroll_payment.reject.detail";
    static final String AUDIT_WITHDRAW_APPROVAL_KEY =
            "audit.payroll_payment.withdraw_approval.detail";
    static final String AUDIT_CHANGE_APPROVAL_LINE_KEY =
            "audit.payroll_payment.change_approval_line.detail";
    static final String AUDIT_CREATE_KEY           = "audit.payroll_payment.create.detail";

    /** ERP Transaction History {@code TRNS_TYPE_CD} for Payroll Scheduled
     *  Payment rows. Mirrors legacy
     *  {@code wabooks.enums.ErpTypeCd.PAYROLL_SCHEDULED_PAYMENT} = "12"
     *  (NOT the same code used by Vendor/Expense/Internal). Pairs with
     *  the {@code settings.erp_transaction_history.type.12} locale
     *  entry ("Payroll Scheduled Payment"). */
    static final String ERP_TRNS_TYPE_PAYROLL_SCHEDULED_PAYMENT = "12";

    /** Vietcombank NAPAS code. Payroll VCB prohibition (G4, PO mapping
     *  2026-06-16) — mirrors legacy {@code pymn_0005_002_c001_act.jsp:134}
     *  ({@code "970436".equals(OUT_BANK_CD)}). */
    static final String VCB_BANK_CD = "970436";
    /** WeCMS&rarr;ERP ('W') audit code for the Payroll Payment RESULT download
     *  (legacy {@code ErpTypeCd.PAYROLL_PAYMENT_RESULT}). */
    static final String ERP_TRNS_TYPE_PAYROLL_PAYMENT_RESULT = "06";

    /** Approval-status codes returned by the legacy {@code wb_get_appr_stts(...)}
     *  function, used by the approve/reject "already final" guard. {@code "C"} =
     *  approved (Completed), {@code "R"} = rejected (legacy code — NOT "X"). */
    static final String APPR_STTS_APPROVED = "C";
    static final String APPR_STTS_REJECTED = "R";

    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ScdulPymnSumrRepository       sumrRepository;
    /** Emits the PROCESSING lifecycle signal on dispatch (HAENGSUNG Oracle write-back). */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final ScdulPymnSumrDetlRepository   detlRepository;
    private final com.kosign.wecms.domain.payment.common.WithdrawalAccountListService withdrawalAccountListService;
    private final CutoffValidator               cutoffValidator;
    private final PayrollPaymentExcelParser     excelParser;
    private final InternalTransferKeyGenerator  keyGenerator;
    private final TransactionalRetry            transactionalRetry;
    private final PayrollPaymentFileResolver    fileResolver;
    private final ErpTrnsHistoryWriter          erpHistoryWriter;
    private final AuditLogService               auditLogService;
    /** PR2: Payroll-only dispatch executor — file-route only (legacy
     *  pymn_0005_001_c001), never TB_PAY_TARGET. Replaces the shared
     *  TransferDispatchExecutor (which routes VND→API, wrong for Payroll).
     *  The shared executor is left untouched for Vendor/Expense. */
    private final PayrollDispatchExecutor       payrollDispatchExecutor;
    /** Approver-chain authority — see InternalTransferService for the rationale. */
    private final ApprovalBoxService            approvalBoxService;
    /** Shared projector that resolves the per-line bank name +
     *  chart-account breadcrumb the Request Approval dialog renders. */
    private final com.kosign.wecms.domain.payment.common.TransferLineDetailMapper transferLineDetailMapper;
    /** Folds the user-supplied bank code into the canonical NAPAS code
     *  before it lands on {@code SCDUL_PYMN_SUMR.OUT_BANK_CD}. Mirrors
     *  legacy {@code PymnHelper.uploadPaymentExcel:1315-1317}. */
    private final com.kosign.wecms.domain.payment.common.OutBankCdNormalizer outBankCdNormalizer;
    /** Authoritative save-path blocked-char enforcement (audit task-44508, P0). */
    private final com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard blockedCharGuard;
    /** M-2/M-3 (legacy parity): EXPT_RESOL_MSTR display flags from BUSI_ENV_SET_INFM. */
    private final com.kosign.wecms.domain.payment.approvalbox.service.ApprovalDisplayFlagsResolver displayFlagsResolver;
    /** M-1 (legacy parity): shared Request Approval attachment writer (EXPT_RESOL_FILE). */
    private final com.kosign.wecms.domain.payment.approvalbox.service.ApprovalAttachmentWriter approvalAttachmentWriter;
    /** Approval-Box fan-out — see InternalTransferService for rationale. */
    private final ApprovalBoxFanoutService      approvalBoxFanoutService;
    /** Shared request-approval bulletin / notification writer (BLTB_* tables). */
    private final com.kosign.wecms.domain.payment.notification.service.ApprovalNotificationService approvalNotificationService;
    private final Clock                         clock;
    private final ExptResolMstrRepository       exptResolMstrRepository;
    private final ExptResolApprvrInfmRepository exptResolApprvrInfmRepository;
    /** Shared avatar-URL resolver — maps approver ids → /api/v1/users/{id}/image
     *  (null when no stored image). Single home for the USER_IMG → URL logic. */
    private final com.kosign.wecms.domain.auth.service.UserImageUrlResolver userImageUrlResolver;
    /** G5: approval-request attachments ({@code EXPT_RESOL_FILE}) keyed by
     *  {@code APPR_SEQ_NO} — surfaces the Scheduled-detail {@code attachments}
     *  list (consumed by the shared Approval Details popup). Same wiring as
     *  {@code VendorPaymentService} / {@code ExpensePaymentService} /
     *  {@code InternalTransferService}. */
    private final ApprovalAttachmentLoader      approvalAttachmentLoader;
    private final ApprovalContentLoader         approvalContentLoader;
    private final DocNoGenerator                docNoGenerator;
    private final BusiInfmRepository            busiInfmRepository;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;
    /** G2 (legacy parity): resolves the typed CITAD 3-digit bank code to the
     *  6-digit internal BANK_CD on save (legacy {@code getBankCode} CITAD
     *  branch). Same repository the parser uses. */
    private final TbFirmBankCodeRepository      firmBankCodeRepo;

    @Transactional(readOnly = true)
    public ScheduledTransferListResponse list(
            String fromDate, String toDate, String srchWd, String apprType,
            String sortField, String sortDirection,
            int page, int size) {

        validateDateRange(fromDate, toDate);
        Pageable pageable = pageable(page, size, sortField, sortDirection);

        String bizKey  = requireTenant();
        String fromDtm = (fromDate + "000000").substring(0, 14);
        String toDtm   = (toDate   + "235959").substring(0, 14);

        String srchWdPattern = buildLikePattern(srchWd);
        String apprTypeArg   = (apprType == null || apprType.isBlank()) ? null : apprType.trim();

        Page<ScdulPymnSumr> rows = sumrRepository.search(
                bizKey, ScdulPymnTypeCd.PAYROLL.code(),
                fromDtm, toDtm,
                srchWdPattern,
                null,
                apprTypeArg,
                pageable);

        // One EXPT_RESOL_MSTR fetch feeds both the Approval Ref (DOC_NO)
        // and the Payroll Type (APPR_FUND_TP) columns — both join from
        // EXPT_RESOL_MSTR on APPR_SEQ_NO (legacy IDO.SCDUL_PYMN_SUMR_R002).
        Map<String, ExptResolMstr> exptBySeqNo = fetchExptResolByApprSeqNo(bizKey, rows.getContent());
        // G4: Approver column — first approver name + chain length ([N] badge) +
        // full ordered chain (popup). Mirrors VendorPaymentService /
        // ExpensePaymentService / InternalTransferService.buildFirstApproverMap.
        Map<String, ApproverSummary> approverByApprSeqNo = buildFirstApproverMap(bizKey, rows.getContent());
        // appr_stts via legacy wb_get_appr_stts(...) over EXPT_RESOL_MSTR (R002 binding).
        Map<String, String> apprSttsByApprSeqNo = legacyFunctions.apprSttsByApprSeqNos(
                bizKey, rows.getContent().stream().map(ScdulPymnSumr::getApprSeqNo).toList());
        List<ScheduledTransferRow> dtoRows = rows.getContent().stream()
                .map(r -> toListRow(r, exptBySeqNo, approverByApprSeqNo, apprSttsByApprSeqNo))
                .toList();
        // F-1: footer totals must aggregate the ENTIRE filtered result set
        // (legacy IDO.SCDUL_PYMN_SUMR_R003 — full set, no pagination), not the
        // current page slice. The aggregate query reuses search()'s exact
        // predicate (tenant / type='00' / date range / search / status filter)
        // and ignores pagination. Mirrors InternalTransferService.list.
        List<CurrencyTotal> totals = mapCurrencyTotals(
                sumrRepository.searchCurrencyTotals(
                        bizKey, ScdulPymnTypeCd.PAYROLL.code(),
                        fromDtm, toDtm,
                        srchWdPattern,
                        null,
                        apprTypeArg));

        log.info("feature=payroll-payment list bizKey={} total={} page={} size={}",
                bizKey, rows.getTotalElements(), page, pageable.getPageSize());
        return new ScheduledTransferListResponse(
                rows.getTotalElements(), page, pageable.getPageSize(),
                totals, dtoRows);
    }

    /* ----------------------------------------------------------------
     *  Detail
     * ---------------------------------------------------------------- */

    /**
     * Detail by SCDUL_PYMN_NO or DOC_NO. Mirrors legacy {@code pymn_0005_003_r001}.
     *
     * <p>Accepts either the internal {@code SCDUL_PYMN_NO} key (numeric
     * format) or the user-visible {@code DOC_NO} (VO_YYMMDD_NNN format).
     * When the primary lookup by SCDUL_PYMN_NO misses, the service resolves
     * {@code DOC_NO → EXPT_RESOL_MSTR.APPR_SEQ_NO → SCDUL_PYMN_SUMR}.
     */
    @Transactional(readOnly = true)
    public ScheduledTransferDetailResponse detail(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, id, ScdulPymnTypeCd.PAYROLL.code())
                .or(() ->
                    exptResolMstrRepository.findByBizKeyAndDocNoAndDelYn(bizKey, id, "N")
                            .filter(m -> m.getApprSeqNo() != null)
                            .flatMap(m -> sumrRepository.findByBizKeyAndApprSeqNoAndScdulPymnTypeCd(
                                    bizKey, m.getApprSeqNo(), ScdulPymnTypeCd.PAYROLL.code()))
                )
                .orElseThrow(() -> new BusinessException("WBE2060"));

        String resolvedId = master.getScdulPymnNo();
        Sort lineSort = ScdulPymnSumrDetlRepository.DETL_NO_NUMERIC_ASC;
        Map<String, com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm> chartByCode =
                transferLineDetailMapper.buildChartIndex(bizKey);
        List<TransferLineDetail> lines = detlRepository
                .findByBizKeyAndScdulPymnNo(bizKey, resolvedId, lineSort)
                .stream()
                // G1: payroll Remarks lives in WDRL_ACCT_RMRK (not TRAN_USAG),
                // so source the detail-line remarks from there.
                .map(d -> transferLineDetailMapper.toLineDetail(d, chartByCode, payrollRemarks(d)))
                .toList();

        // G2 (Scheduled detail): one EXPT_RESOL_MSTR fetch by APPR_SEQ_NO feeds
        // both the Requestor (legacy pymn_0005_003 / R012: ERM.DRAFT_USER_NM)
        // and the Approval Ref (legacy R012: ERM.DOC_NO) — same join the list
        // uses (R002), so detail and list show identical values. null/empty when
        // the batch has no approval request yet.
        String apprSeqNo = master.getApprSeqNo();
        ExptResolMstr erm = (apprSeqNo != null && !apprSeqNo.isBlank())
                ? exptResolMstrRepository
                        .findByBizKeyAndApprSeqNoIn(bizKey, java.util.List.of(apprSeqNo))
                        .stream()
                        .findFirst()
                        .orElse(null)
                : null;
        String requestor = (erm != null && erm.getDraftUserNm() != null && !erm.getDraftUserNm().isBlank())
                ? erm.getDraftUserNm()
                : null;
        // Approval Ref — legacy detail (R012) binds ERM.DOC_NO, NOT
        // SCDUL_PYMN_SUMR.ERP_FILE_NUM. Identical expression to the list
        // (toListRow): DOC_NO when present, else the legacy ERP file number —
        // guarantees detail Approval Ref == list Approval Ref for every row.
        String docNo = (erm != null && erm.getDocNo() != null)
                ? erm.getDocNo()
                : master.getErpFileNum();

        // G5 (Scheduled detail): approval attachments — UNION of EXPT_RESOL_FILE
        // + EXPT_RESOL_PAY_EVID_FILE (mirrors legacy IDO.EXPT_RESOL_FILE_R002;
        // GAP-ATT-1), keyed by APPR_SEQ_NO. Empty when the batch has no approval
        // request yet. The shared Approval Details popup consumes this list; the
        // detail page's own file card stays the uploaded-Excel file (legacy parity).
        List<ApprovalAttachmentRow> attachments =
                approvalAttachmentLoader.load(bizKey, apprSeqNo);

        // Approval Content — legacy BLTB_POST_INFM.POST_CONT (R007), NOT
        // EXPT_RESOL_MSTR.CONT (the two can diverge on the live JEX DB).
        String cont = approvalContentLoader.load(bizKey, apprSeqNo);

        return new ScheduledTransferDetailResponse(
                master.getScdulPymnNo(),
                master.getScdulPymnTypeCd(),
                master.getOutBankCd(),
                // C-2: withdrawal-bank name via legacy wb_get_bank_name_any_code
                // (legacy IDO.SCDUL_PYMN_SUMR_R002.xml:97), matching the legacy
                // OUT_BANK_NM verbatim. Per-line deposit banks keep resolveBankNm.
                legacyFunctions.resolveBankNameAnyCode(master.getOutBankCd()),
                master.getWdrlActNo(),
                master.getTitl(),
                master.getTotalTranAmt(),
                master.getTotTrnsCnt(),
                docNo,
                master.getRegId(),
                requestor,
                master.getRegDtm(),
                master.getApprSeqNo(),
                // appr_stts derived from EXPT_RESOL_MSTR (legacy JEX has no
                // SCDUL_PYMN_SUMR.APPR_STTS column).
                legacyFunctions.getApprStts(bizKey, master.getApprSeqNo()),
                null,
                master.getModDtm(),
                master.getTrnfYn(),
                master.getTrnfDtm(),
                master.getCcyCd(),
                master.getWdrlCcyCd(),
                master.getOrgFileNm(),
                master.getFileSize() == null || master.getFileSize().isBlank() ? null : new java.math.BigDecimal(master.getFileSize().trim()),
                lines,
                attachments,
                cont,  // Approval Content — BLTB_POST_INFM.POST_CONT (legacy R007)
                // Payroll Type — EXPT_RESOL_MSTR.APPR_FUND_TP joined by APPR_SEQ_NO
                // (same `erm` fetch used for Requestor / Approval Ref above). The
                // frontend resolves "0"=Salary / "1"=Bonus / "9"=Other via
                // resolvePayrollTypeLabel. Null until the batch has an approval row.
                erm != null ? erm.getApprFundTp() : null);
    }

    /* ----------------------------------------------------------------
     *  Withdrawal-account picker (legacy comm_1001_001)
     * ---------------------------------------------------------------- */

    /**
     * List firm-banking-capable withdrawal accounts the current tenant +
     * user may pick as the source of a Payroll Payment batch. Delegates
     * to the shared
     * {@link com.kosign.wecms.domain.payment.common.WithdrawalAccountListService}
     * — the lookup is module-agnostic (legacy {@code comm_1001_001} is
     * shared across every payment type) and strict (G1/G2/G3).
     */
    @Transactional(readOnly = true)
    public List<WithdrawalAccountRow> listWithdrawalAccounts(String ccyCd) {
        return withdrawalAccountListService.list(ccyCd);
    }

    /* ----------------------------------------------------------------
     *  Excel parse + save draft (legacy pymn_0005_002)
     * ---------------------------------------------------------------- */

    public ParseExcelResponse parseExcel(MultipartFile file, String bankCd) {
        requireTenant();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("WBE_VALIDATION");
        }
        String name = file.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase();
            if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
                throw new BusinessException("WBE_UNSUPPORTED_MEDIA_TYPE");
            }
        }
        if (bankCd == null || bankCd.isBlank()) {
            throw new BusinessException("WBE_VALIDATION");
        }
        return excelParser.parse(file, bankCd);
    }

    public CreateScheduledTransferResponse createDraft(
            CreateScheduledTransferRequest req,
            MultipartFile file) {
        // One-shot retry on the SCDUL_PYMN_NO MAX+1 race (wb_get_next_val has no
        // row lock). Each attempt runs in its own transaction via
        // TransactionalRetry.runOnce, so a failed attempt fully rolls back and the
        // retry re-allocates the key in a fresh Hibernate session. The master is
        // flushed before any side effect (see doCreateDraft), so a failed attempt
        // never writes a file / ERP-history / detail / audit row to duplicate.
        try {
            return transactionalRetry.runOnce(() -> doCreateDraft(req, file));
        } catch (DataIntegrityViolationException firstRace) {
            log.warn("feature=payroll-payment scdul_pymn_no insert race — retrying once");
            try {
                return transactionalRetry.runOnce(() -> doCreateDraft(req, file));
            } catch (DataIntegrityViolationException secondRace) {
                log.warn("feature=payroll-payment scdul_pymn_no insert race retry failed", secondRace);
                throw new BusinessException("WBE_SCHEDULED_PAYMENT_KEY_RACE");
            }
        }
    }

    private CreateScheduledTransferResponse doCreateDraft(
            CreateScheduledTransferRequest req,
            MultipartFile file) {

        String bizKey = requireTenant();
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        // P0 (audit task-44508): authoritative server-side blocked-char
        // enforcement BEFORE persistence (Recipient Name + Remarks).
        for (TransferLineInput line : req.lines()) {
            blockedCharGuard.assertClean(req.bankCd(),
                    com.kosign.wecms.domain.payment.validation.PymnType.PAYROLL_PAYMENT,
                    java.util.List.of(
                            com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.field(
                                    com.kosign.wecms.domain.payment.validation.FieldCd.RECIPIENT_NAME,
                                    com.kosign.wecms.domain.payment.common.LegacyTextNormalizer.normalize(line.rcptNm())),
                            com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.field(
                                    com.kosign.wecms.domain.payment.validation.FieldCd.REMARKS,
                                    com.kosign.wecms.domain.payment.common.LegacyTextNormalizer.normalize(line.tranUsag()))));
        }

        String scdulPymnNo = keyGenerator.nextScdulPymnNo(bizKey);

        ScdulPymnSumr master = new ScdulPymnSumr();
        master.setBizKey(bizKey);
        master.setScdulPymnNo(scdulPymnNo);
        master.setScdulPymnTypeCd(ScdulPymnTypeCd.PAYROLL.code());
        // Legacy parity (PymnHelper.uploadPaymentExcel:1315-1317): user
        // may type either the public NAPAS code or the internal BANK_CD;
        // persist canonical NAPAS so cut-off / dispatch resolve correctly.
        master.setOutBankCd(outBankCdNormalizer.normalize(req.bankCd()));
        master.setWdrlActNo(req.acctNo());
        master.setCcyCd(req.ccyCd());
        master.setWdrlCcyCd(req.ccyCd());
        master.setTitl(req.titl());
        master.setTrnfYn(TrnfYn.NOT_TRANSFERRED.code());
        master.setErpProcCmplYn("N");
        BigDecimal lineCount = BigDecimal.valueOf(req.lines().size());
        master.setTotTrnsCnt(lineCount);
        BigDecimal totalAmount = req.lines().stream()
                .map(TransferLineInput::tranAmt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        master.setTotalTranAmt(totalAmount);
        if (file != null && !file.isEmpty()) {
            String orgFileNm  = sanitiseOriginalFilename(file.getOriginalFilename());
            String saveFileNm = scdulPymnNo + "_" + orgFileNm;
            master.setOrgFileNm(orgFileNm);
            master.setSaveFileNm(saveFileNm);
            master.setFileSize(Long.toString(file.getSize()));
        }
        master.setRegId(userId);
        master.setRegDtm(now);
        master.setModId(userId);
        master.setModDtm(now);
        // Flush the master INSERT up front so a concurrent MAX+1 race on
        // SCDUL_PYMN_NO (wb_get_next_val has no row lock) surfaces as a
        // DataIntegrityViolationException HERE — before any file write, detail
        // INSERT, ERP-history (REQUIRES_NEW) or audit row — so the one-shot retry
        // in createDraft re-runs cleanly with no orphaned side effects.
        sumrRepository.saveAndFlush(master);

        // Disk write only AFTER the key is confirmed unique, so a failed attempt
        // never leaves an orphan file under the storage root.
        if (file != null && !file.isEmpty()) {
            persistUploadedFile(bizKey, master.getSaveFileNm(), file);
        }

        // Detail numbering via the wb_get_next_val funnel (V140 #38, per-tenant MAX+1).
        // Seed ONCE per document then increment in Java across the batch (same pattern as
        // OverseasPaymentExcelService) — no per-line DB round-trip, no per-detail flush.
        // Intra-document uniqueness is guaranteed by the increment; cross-document collision
        // is impossible because SCDUL_PYMN_NO is part of the detail PK.
        String detlSeedRaw = legacyFunctions.nextScdulPymnDetlNo(bizKey);
        if (detlSeedRaw == null || detlSeedRaw.isBlank()) {
            throw new IllegalStateException(
                    "wb_get_next_val('scdul_pymn_sumr_detl','scdul_pymn_detl_no') "
                  + "returned null/blank for bizKey=" + bizKey);
        }
        long detlNoCursor = Long.parseLong(detlSeedRaw.trim()) - 1;
        for (TransferLineInput line : req.lines()) {
            ScdulPymnSumrDetl detl = new ScdulPymnSumrDetl();
            detl.setBizKey(bizKey);
            detl.setScdulPymnNo(scdulPymnNo);
            detl.setScdulPymnDetlNo(Long.toString(++detlNoCursor));
            detl.setTranAmt(line.tranAmt());
            String lineCcy = emptyToDefault(line.trnfCcyCd(), req.trnfCcyCd());
            detl.setTrnfCcyCd(lineCcy);
            // G3 (legacy parity): payroll forces ANT_DIV_CD=CITAD on every
            // detail row — legacy pymn_0005_002_c001_act.jsp:118,125 hardcodes
            // AntDivCd.CITAD. Payroll is CITAD-only in the live system; NAPAS
            // payroll support needs a PO decision before relaxing this.
            detl.setAntDivCd("CITAD");
            // Persist normalized (CR/LF + accents stripped) value (audit task-44508, P0).
            detl.setRcptNm(emptyToNull(
                    com.kosign.wecms.domain.payment.common.LegacyTextNormalizer.normalize(line.rcptNm())));
            // G2 (legacy parity): resolve the typed CITAD code to the 6-digit
            // internal BANK_CD before persisting (legacy getBankCode CITAD
            // branch, pymn_0005_002_c001_act.jsp:126,162-185).
            String resolvedTarBankCd = resolveCitadBankCd(line.tarBankCd());
            detl.setTarBankCd(resolvedTarBankCd);
            detl.setFincMdaNo(line.fincMdaNo());
            // G4 (legacy parity, PO mapping 2026-06-16): payroll VCB
            // prohibitions enforced at upload save (legacy
            // pymn_0005_002_c001_act.jsp:128-142). VCB-as-source cannot do VND
            // payroll at all; VCB USD only to VCB (same bank). Throwing here
            // rolls the whole draft back (matches legacy WbRollbackException).
            if (VCB_BANK_CD.equals(master.getOutBankCd())) {
                if ("VND".equalsIgnoreCase(lineCcy)) {
                    throw new BusinessException("WBE_PAYROLL_VCB_VND_NOT_SUPPORTED");
                }
                if (!VCB_BANK_CD.equals(resolvedTarBankCd)) {
                    throw new BusinessException("WBE_PAYROLL_VCB_USD_OTHER_BANK");
                }
            }
            // G1 (legacy parity): payroll Remarks (Excel col 6) persists to
            // WDRL_ACCT_RMRK, NOT TRAN_USAG — legacy pymn_0005_002_r001_act.jsp
            // packs the cell into "WDRL_ACCT_RMRK" (:341,357,623) and c001
            // copyFrom(recItem) saves it there. The shared wire DTO carries the
            // value in tran_usag, so read it from there and write the legacy
            // column; leave TRAN_USAG null to match legacy payroll rows.
            detl.setWdrlActRmrk(emptyToNull(
                    com.kosign.wecms.domain.payment.common.LegacyTextNormalizer.normalize(line.tranUsag())));
            detl.setTrcoKey(emptyToNull(line.trcoKey()));
            detl.setHandTrcoKey(emptyToNull(line.handTrcoKey()));
            detl.setDocNo(emptyToNull(line.docNo()));
            detl.setNote(emptyToNull(line.note()));
            detl.setCitadBankCd(emptyToNull(line.citadBankCd()));
            detl.setTrcoCmnm(emptyToNull(line.trcoCmnm()));
            detl.setTrcoCmenm(emptyToNull(line.trcoCmenm()));
            detl.setChartAcctSeqNo(emptyToNull(line.chartAcctSeqNo()));
            detl.setChartAcctCd(emptyToNull(line.chartAcctCd()));
            detl.setDepoCcyCd(emptyToNull(line.depoCcyCd()));
            detl.setRegId(userId);
            detl.setRegDtm(now);
            detl.setModId(userId);
            detl.setModDtm(now);
            detlRepository.save(detl);
        }

        // ERP Transaction History — upload audit row. TRNS_TYPE_CD "12"
        // → Payroll Scheduled Payment (per legacy ErpTypeCd).
        if (master.getSaveFileNm() != null && !master.getSaveFileNm().isBlank()) {
            try {
                java.nio.file.Path source = fileResolver.resolve(bizKey, master.getSaveFileNm());
                erpHistoryWriter.recordUploadedFile(
                        ERP_TRNS_TYPE_PAYROLL_SCHEDULED_PAYMENT,
                        source,
                        master.getOrgFileNm());
            } catch (RuntimeException ex) {
                log.warn(
                        "feature=payroll-payment ERP history skip "
                      + "(path resolver rejected) bizKey={} scdulPymnNo={} saveFileNm={}",
                        bizKey, scdulPymnNo, master.getSaveFileNm(), ex);
            }
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(scdulPymnNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_CREATE_KEY)
                .build());

        log.info("feature=payroll-payment create bizKey={} scdulPymnNo={} lineCount={} totalAmount={}",
                bizKey, scdulPymnNo, req.lines().size(), totalAmount);
        return new CreateScheduledTransferResponse(scdulPymnNo);
    }

    /**
     * G2 (legacy parity): resolve a typed CITAD bank code (3-digit) to its
     * 6-digit internal {@code BANK_CD} via {@code TB_FIRM_BANK_CODE}. Mirrors
     * legacy {@code pymn_0005_002_c001_act.jsp getBankCode(...,"CITAD",...)}
     * (query {@code ant_div_cd='CITAD' AND citad_bank_cd=? AND
     * bank_branch_div='1'} &rarr; {@code BANK_CD}; the {@code bank_stas='1'}
     * predicate is deferred per the repository's documented caveat — same
     * lookup the parser uses).
     *
     * <p>Legacy returns {@code ""} when the code is not in the CITAD registry;
     * we keep the typed code as a fallback so a registry miss does not silently
     * blank the bank code (refactor safety — legacy's empty result is itself a
     * latent gap, not a behaviour to reproduce).
     */
    private String resolveCitadBankCd(String typed) {
        String code = emptyToNull(typed);
        if (code == null) {
            return null;
        }
        return firmBankCodeRepo
                .findFirstByAntDivCdAndCitadBankCd("CITAD", code)
                .map(TbFirmBankCode::getBankCd)
                .filter(b -> b != null && !b.isBlank())
                .orElse(code);
    }

    /**
     * G1 (legacy parity): payroll Remarks read source. Legacy keeps Remarks in
     * {@code WDRL_ACCT_RMRK}; refactor rows written before this fix may still
     * carry the value in {@code TRAN_USAG}, so coalesce (WDRL_ACCT_RMRK first).
     */
    private static String payrollRemarks(ScdulPymnSumrDetl d) {
        String rmrk = d.getWdrlActRmrk();
        if (rmrk != null && !rmrk.isBlank()) {
            return rmrk;
        }
        return d.getTranUsag();
    }

    /* ----------------------------------------------------------------
     *  File download
     * ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public DownloadResource resolveDownload(String scdulPymnNo) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, scdulPymnNo);

        String saveFileNm = master.getSaveFileNm();
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new BusinessException("WBE2069");
        }

        java.nio.file.Path path = fileResolver.resolveForRead(bizKey, saveFileNm);
        if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("WBE2069");
        }
        long fileSize = master.getFileSize() == null || master.getFileSize().isBlank()
                ? path.toFile().length()
                : Long.parseLong(master.getFileSize().trim());
        String displayName = master.getOrgFileNm() == null || master.getOrgFileNm().isBlank()
                ? saveFileNm
                : master.getOrgFileNm();
        // The /{id}/file endpoint serves the Scheduled-side original import-file
        // download. Per the ERP Transaction History mapping, Scheduled-side
        // downloads record the SCHEDULED type code (12 = Payroll Scheduled Payment),
        // UNCONDITIONALLY (both not-yet-dispatched N and dispatched Y). Non-blocking
        // (REQUIRES_NEW + swallow). See VendorPaymentService.
        erpHistoryWriter.recordExportedFile(
                ERP_TRNS_TYPE_PAYROLL_SCHEDULED_PAYMENT, path, displayName);
        return new DownloadResource(path, displayName, saveFileNm, fileSize);
    }

    public record DownloadResource(
            java.nio.file.Path path, String orgFileNm, String saveFileNm, long fileSize) { }

    /* ----------------------------------------------------------------
     *  Update reporting account (legacy pymn_0009_002_u002)
     * ---------------------------------------------------------------- */

    /**
     * Update the chart-of-accounts assignment on one Payroll Payment
     * line. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#updateReportingAccount}
     * — same wire shape, same audit pattern, scoped to the Payroll
     * {@code SCDUL_PYMN_TYPE_CD} via {@link #loadMaster}.
     */
    @Transactional
    public void updateReportingAccount(String id, String lineId,
                                       UpdateReportingAccountRequest req) {
        String bizKey = requireTenant();
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        ScdulPymnSumr master = loadMaster(bizKey, id);
        if (TrnfYn.TRANSFERRED.code().equals(master.getTrnfYn())) {
            throw new BusinessException("WBE2064");
        }

        ScdulPymnSumrDetl detl = detlRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(bizKey, id, lineId)
                .orElseThrow(() -> new BusinessException("WBE2061"));

        detl.setChartAcctSeqNo(req.chartAcctSeqNo());
        detl.setChartAcctCd(req.chartAcctCd());
        detl.setModId(userId);
        detl.setModDtm(now);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .refNo2(lineId)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_UPDATE_KEY)
                .build());

        log.info("feature=payroll-payment update-reporting bizKey={} scdulPymnNo={} lineId={} chartAcctCd={}",
                bizKey, id, lineId, req.chartAcctCd());
    }


    /* ----------------------------------------------------------------
     *  Delete draft batch (legacy pymn_0005_001_d001)
     * ---------------------------------------------------------------- */

    @Transactional
    public void delete(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        // Legacy delete-block code WBE1063 (pymn_0005_001_d001_act.jsp:91-92),
        // shared across all 5 payment modules (PO decision 2026-07-08).
        if (master.getApprSeqNo() != null && !master.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE1063");
        }

        String orgFileNm  = master.getOrgFileNm();
        String saveFileNm = master.getSaveFileNm();

        detlRepository.deleteByBizKeyAndScdulPymnNo(bizKey, id);
        sumrRepository.deleteById(new ScdulPymnSumrId(bizKey, id));

        if (saveFileNm != null && !saveFileNm.isBlank()) {
            try {
                java.nio.file.Path source = fileResolver.resolveForRead(bizKey, saveFileNm);
                erpHistoryWriter.recordDeletedFile(
                        ERP_TRNS_TYPE_PAYROLL_SCHEDULED_PAYMENT,
                        source,
                        orgFileNm);
            } catch (RuntimeException ex) {
                log.warn(
                        "feature=payroll-payment ERP history skip "
                      + "(path resolver rejected) bizKey={} scdulPymnNo={} saveFileNm={}",
                        bizKey, id, saveFileNm, ex);
            }
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_DELETE_KEY)
                .build());

        log.info("feature=payroll-payment delete bizKey={} scdulPymnNo={}", bizKey, id);
    }

    /* ----------------------------------------------------------------
     *  Request Approval
     * ---------------------------------------------------------------- */

    /**
     * Preview the next {@code VO_YYMMDD_NNN} document number for the Request
     * Approval popup (read-only). Computes via the shared {@link DocNoGenerator}
     * WITHOUT reserving or persisting anything — the authoritative DOC_NO is
     * stamped at {@link #requestApproval} submit time. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#previewNextDocNo}.
     */
    @Transactional(readOnly = true)
    public com.kosign.wecms.domain.payment.internaltransfer.dto.NextDocNoResponse previewNextDocNo() {
        return new com.kosign.wecms.domain.payment.internaltransfer.dto.NextDocNoResponse(
                docNoGenerator.nextDocNo(requireTenant()));
    }

    @Transactional
    public RequestApprovalResponse requestApproval(String id, RequestApprovalRequest req,
            java.util.List<org.springframework.web.multipart.MultipartFile> attachments) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        if (master.getApprSeqNo() != null && !master.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2062");
        }
        if (req == null) {
            throw new BusinessException("WBE_VALIDATION", "request body is required");
        }
        if (req.approvers() == null || req.approvers().isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "approvers is required");
        }

        String apprSeqNo = allocateApprSeqNo(bizKey);
        String docNo;
        try {
            docNo = stampApproval(master, apprSeqNo, req);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            log.info("feature=payroll-payment apprSeqNo race retrying once bizKey={}", bizKey);
            apprSeqNo = allocateApprSeqNo(bizKey);
            docNo = stampApproval(master, apprSeqNo, req);
        }

        // M-1 (legacy parity): persist Request Approval attachments into
        // EXPT_RESOL_FILE (legacy processFileAttachments / REC1).
        approvalAttachmentWriter.persist(bizKey, apprSeqNo, attachments);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .refNo2(apprSeqNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_REQUEST_APPROVAL_KEY)
                .build());

        log.info(
                "feature=payroll-payment request-approval bizKey={} scdulPymnNo={} apprSeqNo={} apprLineNo={} approvers={}",
                bizKey, id, apprSeqNo, req.apprLineNo(), req.approvers().size());

        // Lifecycle signal — request-approval submitted (PENDING). The HAENGSUNG
        // Oracle write-back listener maps this to TB_TRNS_MAST.LAST_STATUS='10'
        // (legacy comm_0192_001_c005 → ErpStatusUpdateHelper PENDING). No-op for
        // non-HAENGSUNG / non-ERP batches (listener guards). apprSeqNo is the
        // value resolved above (after the one-shot race retry).
        eventPublisher.publishEvent(new com.kosign.wecms.domain.payment.approvalbox.event
                .PaymentApprovalStatusChangedEvent(bizKey, apprSeqNo,
                com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus.PENDING));

        return new RequestApprovalResponse(apprSeqNo, docNo);
    }

    /** Per-tenant MAX+1 across both EXPT_RESOL_MSTR and SCDUL_PYMN_SUMR so the
     *  newly issued APPR_SEQ_NO is above any existing rows in either table. */
    private String allocateApprSeqNo(String bizKey) {
        // APPR_SEQ_NO via the single wb_get_next_val funnel (V140 #1 — the GLOBAL
        // sq_appr_seq_no sequence shared across every Payment module, so the id
        // space stays contiguous and never collides with the live JEX / WABOOKS
        // systems). Replaces the per-tenant cross-table MAX+1 ceiling.
        return legacyFunctions.nextApprSeqNo(bizKey);
    }

    private String stampApproval(ScdulPymnSumr master, String apprSeqNo,
                                RequestApprovalRequest req) {
        String bizKey  = master.getBizKey();
        String userId  = TenantContext.currentUserId();
        String userNm  = TenantContext.currentUserNm();
        String nowDtm  = ZonedDateTime.ofInstant(clock.instant(), VIETNAM_ZONE).format(DTM14);
        String draftDate = nowDtm.substring(0, 8);
        String draftTime = nowDtm.substring(8, 14);

        // 1. Stamp SCDUL_PYMN_SUMR (status is NOT stored here — derived from
        //    EXPT_RESOL_MSTR.APPR_TYPE_CD, set Pending on the master below).
        master.setApprSeqNo(apprSeqNo);
        // Do NOT overwrite SCDUL_PYMN_SUMR.TITL here. Legacy
        // comm_0192_001_c005_act.jsp:356-373 (updateScheduledPaymentSummary)
        // updates ONLY OUT_BANK_CD / WDRL_ACT_NO / APPR_SEQ_NO / WDRL_CCY_CD at
        // Request Approval — it never touches SPS.TITL. The batch title stays the
        // uploaded file/batch name; the approval-request title lives only in
        // EXPT_RESOL_MSTR.TITL (set below). Overwriting TITL here corrupted the
        // File Name column (it showed the approval title instead of the file).
        // H-1 (legacy parity, comm_0192_001_c005_act.jsp:362-371
        // updateScheduledPaymentSummary): persist the withdrawal account the
        // user selected in the Request Approval popup. OUT_BANK_CD is the
        // NAPAS-converted code — OutBankCdNormalizer reproduces the legacy
        // NapasBankInfoDAO "bank_cd = ? OR napas_bank_cd = ?" lookup.
        if (req.bankCd() != null && !req.bankCd().isBlank()) {
            master.setOutBankCd(outBankCdNormalizer.normalize(req.bankCd()));
        }
        if (req.acctNo() != null && !req.acctNo().isBlank()) {
            master.setWdrlActNo(req.acctNo().trim());
        }
        if (req.ccyCd() != null && !req.ccyCd().isBlank()) {
            master.setWdrlCcyCd(req.ccyCd().trim());
        }
        master.setModDtm(nowDtm);
        if (userId != null) master.setModId(userId);
        sumrRepository.saveAndFlush(master);

        // 2. Write EXPT_RESOL_MSTR master row
        ExptResolMstr mstr = new ExptResolMstr();
        mstr.setBizKey(bizKey);
        mstr.setApprSeqNo(apprSeqNo);
        mstr.setApprDv(ApprDv.PAYROLL.code());
        // F-4 (legacy parity): legacy comm_0192_001_c005 sets ApprSts.STS_2
        // ("2" = In Progress) at request time, not "0" (temp save).
        mstr.setApprSts("2");
        mstr.setDraftDate(draftDate);
        mstr.setDraftTime(draftTime);
        mstr.setDraftUserId(userId != null ? userId : "");
        mstr.setDraftUserNm(userNm != null ? userNm : "");
        mstr.setDraftUserSiteCd(bizKey);
        String cmpnNm = busiInfmRepository.findById(bizKey)
                .map(b -> b.getCmpnNm() != null ? b.getCmpnNm() : bizKey)
                .orElse(bizKey);
        mstr.setDraftUserSiteNm(cmpnNm);
        mstr.setHandRegYn("Y");
        // M-2 (legacy parity): display flags from BUSI_ENV_SET_INFM (00014/16/17/18),
        // not hardcoded. Mirrors comm_0192_001_c005_act.jsp:316-320.
        var flags = displayFlagsResolver.resolve(bizKey);
        mstr.setApprBoxUseYn(flags.apprBoxUseYn());
        mstr.setApprDatDispYn(flags.apprDatDispYn());
        mstr.setCmpnLogoDispYn(flags.cmpnLogoDispYn());
        mstr.setRegDispYn(flags.regDispYn());
        mstr.setTitl(req.postTitle() != null ? req.postTitle().trim() : "");
        String docNo = docNoGenerator.nextDocNo(bizKey);
        mstr.setDocNo(docNo);
        mstr.setDocNm(ApprDv.PAYROLL.docNm());   // L-1 (legacy parity): legacy DOC_NM = apprDv.getLabel()
        BigDecimal amt = master.getTotalTranAmt() != null ? master.getTotalTranAmt() : BigDecimal.ZERO;
        String ccy = master.getCcyCd() != null ? master.getCcyCd().toUpperCase() : "VND";
        boolean isUsd = "USD".equals(ccy);
        boolean isVnd = "VND".equals(ccy);
        mstr.setTotPayKhrAmt(isUsd ? BigDecimal.ZERO : amt);
        mstr.setTotPayUsdAmt(isUsd ? amt : BigDecimal.ZERO);
        mstr.setTotPayVndAmt(isVnd ? amt : BigDecimal.ZERO);
        mstr.setPayDate(req.payDate() != null && !req.payDate().isBlank() ? req.payDate().trim() : null);
        // PR7: Payroll Type — persist APPR_FUND_TP from the Request Approval
        // popup ("0"=Salary / "1"=Bonus / "9"=Other; legacy comm_0192_001_c005,
        // mandatory for APPR_DV=21). Read back by the Payroll Scheduled + Result
        // lists. Null when the FE omits it (legacy-blank). Payroll-only —
        // sibling modules never reach this code path.
        mstr.setApprFundTp(req.apprFundTp() != null && !req.apprFundTp().isBlank()
                ? req.apprFundTp().trim()
                : null);
        mstr.setDelYn("N");
        mstr.setPayEndYn("N");
        mstr.setApprTypeCd("00");
        mstr.setRegId(userId != null ? userId : "");
        mstr.setRegDtm(nowDtm);
        mstr.setModId(userId);
        mstr.setModDtm(nowDtm);
        exptResolMstrRepository.save(mstr);

        // DOC_NO is NOT back-stamped onto SCDUL_PYMN_SUMR — its canonical
        // home is EXPT_RESOL_MSTR.DOC_NO (written above). The list and detail
        // views resolve doc_no from EXPT_RESOL_MSTR by APPR_SEQ_NO.
        // SCDUL_PYMN_SUMR.ERP_FILE_NUM is legacy varchar(8) holding the
        // 8-digit ERP file sequence number — overloading it with the
        // {prefix}_{YYMMDD}_{NNN} doc_no overflowed the column (WBE_DB 500)
        // and drifted from the legacy schema. Left untouched here, matching
        // InternalTransferService#stampApproval.

        // 3. Write EXPT_RESOL_APPRVR_INFM rows — first approver "P" (active), rest "W" (waiting)
        List<RequestApprovalRequest.ApproverInput> approvers = req.approvers();
        for (int i = 0; i < approvers.size(); i++) {
            RequestApprovalRequest.ApproverInput ap = approvers.get(i);
            ExptResolApprvrInfm row = new ExptResolApprvrInfm();
            row.setBizKey(bizKey);
            row.setApprSeqNo(apprSeqNo);
            row.setApprvrId(ap.apprvrId());
            row.setApprvrStts(i == 0 ? "P" : "W");
            row.setApprvrNm(ap.apprvrNm());
            row.setApprDtm(null);
            row.setRegId(userId);
            row.setRegDtm(nowDtm);
            row.setModId(userId != null ? userId : "");
            row.setModDtm(nowDtm);
            row.setReadYn("N");
            row.setApprvrLineNo(BigDecimal.valueOf(i + 1));
            row.setApprLineNo(BigDecimal.valueOf(req.apprLineNo()));
            exptResolApprvrInfmRepository.save(row);
        }

        // F-1 (legacy parity): EXPT_RESOL_DETL row — DOC_ID = batch id,
        // AMT = batch total, CCY_CD = batch currency. Mirrors legacy
        // comm_0192_001_c005_act.jsp:340 createExptResolDetl.
        approvalBoxFanoutService.writeResolutionDetail(
                apprSeqNo, master.getScdulPymnNo(), master.getTotalTranAmt(), master.getCcyCd());
        // F-2 (legacy parity): EXPT_RESOL_APPR_LINE print-header snapshot
        // (env-gated). Mirrors comm_0192_001_c005_act.jsp:489 createApprovalLines.
        approvalBoxFanoutService.writeApprovalLineSnapshot(apprSeqNo);

        // Bulletin / notification fan-out (legacy createBulletinPost +
        // createUserNewPostNotifications + BltbHelper.createNotificationToApprvr).
        approvalNotificationService.notifyRequestApproval(
                apprSeqNo, req.postTitle(), req.postCont(), "21", "Payroll Payment");

        return docNo;
    }

    /* ----------------------------------------------------------------
     *  Approval action — Approve / Reject
     * ---------------------------------------------------------------- */

    @Transactional
    public void actOnApproval(String id, ApprovalActionRequest req) {
        if (req == null) {
            throw new BusinessException("WBE_VALIDATION", "request body is required");
        }
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        if (master.getApprSeqNo() == null || master.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2126");
        }

        String current = legacyFunctions.getApprStts(bizKey, master.getApprSeqNo());
        if (APPR_STTS_APPROVED.equals(current) || APPR_STTS_REJECTED.equals(current)) {
            throw new BusinessException("WBE2127");
        }

        // Delegate approver-chain logic — multi-approver workflows stay
        // "In Progress" until every approver has acted. The approval box owns
        // the EXPT_RESOL_MSTR.APPR_TYPE_CD transition (source of truth); no
        // write-back to SCDUL_PYMN_SUMR (no APPR_STTS column in legacy JEX).
        ApprovalBoxActionResponse result = req.isApprove()
                ? approvalBoxService.approve(master.getApprSeqNo())
                : approvalBoxService.reject(master.getApprSeqNo());

        String auditKey = req.isApprove() ? AUDIT_APPROVE_KEY : AUDIT_REJECT_KEY;
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .refNo2(master.getApprSeqNo())
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(auditKey)
                .build());

        log.info(
                "feature=payroll-payment approval-action bizKey={} scdulPymnNo={} apprSeqNo={} action={} apprStts={}",
                bizKey, id, master.getApprSeqNo(), req.action(), result.apprStts());
    }

    /**
     * Withdraw an in-flight approval request on a batch — kebab "Delete"
     * action on the Approval Details popup. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#withdrawApproval}.
     */
    @Transactional
    public void withdrawApproval(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        String apprSeqNo = master.getApprSeqNo();
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            throw new BusinessException("WBE2126");
        }

        approvalBoxFanoutService.withdrawApproval(apprSeqNo);

        master.setApprSeqNo(null);
        // ERP_FILE_NUM is no longer used to carry doc_no (see stampApproval);
        // nothing to clear here — its legacy ERP file sequence stays intact.
        master.setModDtm(OffsetDateTime.now(clock).format(DTM14));
        String userId = TenantContext.currentUserId();
        if (userId != null) master.setModId(userId);
        sumrRepository.saveAndFlush(master);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .refNo2(apprSeqNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_WITHDRAW_APPROVAL_KEY)
                .build());

        log.info("feature=payroll-payment withdraw-approval bizKey={} scdulPymnNo={} apprSeqNo={}",
                bizKey, id, apprSeqNo);
    }

    /**
     * Replace the approver chain on an in-flight approval request with
     * the approvers attached to {@code apprLineNo} — kebab "Edit" action.
     * Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#changeApprovalLine}.
     */
    @Transactional
    public void changeApprovalLine(String id, Long apprLineNo) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        String apprSeqNo = master.getApprSeqNo();
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            throw new BusinessException("WBE2126");
        }

        approvalBoxFanoutService.changeApprovalLine(apprSeqNo, apprLineNo);

        // No SCDUL_PYMN_SUMR write-back: status derives from EXPT_RESOL_MSTR
        // (reset to Pending by the fanout). Legacy JEX has no APPR_STTS column.

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .refNo2(apprSeqNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_CHANGE_APPROVAL_LINE_KEY)
                .build());

        log.info("feature=payroll-payment change-approval-line bizKey={} scdulPymnNo={} apprSeqNo={} newApprLineNo={}",
                bizKey, id, apprSeqNo, apprLineNo);
    }

    /* ----------------------------------------------------------------
     *  Dispatch / Execute Transfer
     * ---------------------------------------------------------------- */

    @Transactional
    public void dispatch(String id, DispatchRequest req) {
        String bizKey = requireTenant();
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        ScdulPymnSumr master = loadMaster(bizKey, id);

        if (TrnfYn.TRANSFERRED.code().equals(master.getTrnfYn())) {
            throw new BusinessException("WBE2064");
        }
        if (master.getApprSeqNo() == null || master.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2063");
        }

        List<ScdulPymnSumrDetl> lines = detlRepository.findByBizKeyAndScdulPymnNo(
                bizKey, id, ScdulPymnSumrDetlRepository.DETL_NO_NUMERIC_ASC);
        TransferNetwork network = lines.isEmpty()
                ? TransferNetwork.CITAD
                : TransferNetwork.fromAntDivCd(lines.get(0).getAntDivCd());
        // Payroll always dispatches via the File-SFTP route (never TB_PAY_TARGET),
        // so apiTransport=false → Shinhan CITAD uses the 15:30 File-SFTP window.
        // VND→VND still drives the Shinhan hard-block; ≥500M the VCB CITAD tier.
        boolean vndToVnd = "VND".equalsIgnoreCase(master.getCcyCd())
                && "VND".equalsIgnoreCase(master.getWdrlCcyCd());
        boolean hasLargeTxn = lines.stream().anyMatch(l -> l.getTranAmt() != null
                && l.getTranAmt().compareTo(cutoffValidator.largeTxnThreshold()) >= 0);
        cutoffValidator.validate(new CutoffCheck(
                master.getOutBankCd(), network, false, vndToVnd, hasLargeTxn));

        // PR2: resolve Payroll Type (EXPT_RESOL_MSTR.APPR_FUND_TP by APPR_SEQ_NO)
        // → TB_PAY_FILE_MST.FUND_TP, then dispatch via the Payroll-only
        // file-route executor (legacy pymn_0005_001_c001 — never TB_PAY_TARGET).
        String apprFundTp = (master.getApprSeqNo() != null && !master.getApprSeqNo().isBlank())
                ? exptResolMstrRepository
                        .findByBizKeyAndApprSeqNoIn(bizKey, java.util.List.of(master.getApprSeqNo()))
                        .stream()
                        .findFirst()
                        .map(ExptResolMstr::getApprFundTp)
                        .orElse(null)
                : null;
        payrollDispatchExecutor.execute(master, lines, req.trnfCcyCd(), apprFundTp);

        master.setTrnfYn(TrnfYn.TRANSFERRED.code());
        master.setTrnfDtm(now);
        master.setModId(userId);
        master.setModDtm(now);
        sumrRepository.saveAndFlush(master);

        // Lifecycle signal — dispatched / processing (HAENGSUNG Oracle write-back).
        eventPublisher.publishEvent(new com.kosign.wecms.domain.payment.approvalbox.event
                .PaymentApprovalStatusChangedEvent(bizKey, master.getApprSeqNo(),
                com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus.PROCESSING));

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_DISPATCH_KEY)
                .msgArgs(new Object[]{ req.trnfCcyCd() })
                .build());

        log.info("feature=payroll-payment dispatch bizKey={} scdulPymnNo={} trnfCcyCd={} network={} lines={}",
                bizKey, id, req.trnfCcyCd(), network, lines.size());
    }

    /* ----------------------------------------------------------------
     *  Helpers
     * ---------------------------------------------------------------- */

    private ScdulPymnSumr loadMaster(String bizKey, String scdulPymnNo) {
        return sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, scdulPymnNo, ScdulPymnTypeCd.PAYROLL.code())
                .orElseThrow(() -> new BusinessException("WBE2060"));
    }

    /**
     * Fetch the {@code EXPT_RESOL_MSTR} rows backing the current page's
     * batches, keyed by {@code APPR_SEQ_NO}. One query; callers derive
     * both {@code DOC_NO} (Approval Ref) and {@code APPR_FUND_TP}
     * (Payroll Type) from the same rows — mirrors the legacy
     * {@code IDO.SCDUL_PYMN_SUMR_R002} LEFT JOIN.
     */
    private Map<String, ExptResolMstr> fetchExptResolByApprSeqNo(String bizKey, List<ScdulPymnSumr> rows) {
        List<String> apprSeqNos = rows.stream()
                .map(ScdulPymnSumr::getApprSeqNo)
                .filter(seq -> seq != null && !seq.isBlank())
                .distinct()
                .toList();
        if (apprSeqNos.isEmpty()) return Map.of();
        return exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(bizKey, apprSeqNos)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ExptResolMstr::getApprSeqNo,
                        m -> m,
                        (a, b) -> a));
    }

    private static ScheduledTransferRow toListRow(ScdulPymnSumr s,
                                                   Map<String, ExptResolMstr> exptBySeqNo,
                                                   Map<String, ApproverSummary> approverByApprSeqNo,
                                                   Map<String, String> apprSttsByApprSeqNo) {
        ExptResolMstr erm = s.getApprSeqNo() != null ? exptBySeqNo.get(s.getApprSeqNo()) : null;
        String docNo = (erm != null && erm.getDocNo() != null)
                ? erm.getDocNo()
                : s.getErpFileNum();
        // Payroll Type — joined from EXPT_RESOL_MSTR.APPR_FUND_TP
        // ("0"=Salary, "1"=Bonus, else blank; resolved to a label
        // on the frontend). Null until the batch has an approval row.
        String apprFundTp = erm != null ? erm.getApprFundTp() : null;
        // PR6: Schedule Date — legacy "지급예정일" comes from
        // EXPT_RESOL_MSTR.PAY_DATE (written at Request Approval, legacy
        // IDO.SCDUL_PYMN_SUMR_R002 `, ERM.PAY_DATE`), NOT
        // SCDUL_PYMN_SUMR.PAY_TRAN_DT (which is dispatch-time and stays null
        // for pending/approved batches). Null until the batch has an approval
        // row with a scheduled date — legacy-correct blank.
        String payDate = erm != null ? erm.getPayDate() : null;
        // G4: Approver column — null pre-approval (no APPR_SEQ_NO / no chain),
        // so the shared shell leaves the cell blank exactly like legacy.
        ApproverSummary appr = s.getApprSeqNo() != null
                ? approverByApprSeqNo.get(s.getApprSeqNo())
                : null;
        String  approver      = appr != null ? appr.firstName() : null;
        Integer approverCount = appr != null ? appr.count()     : null;
        List<ScheduledTransferApprover> approvers = appr != null ? appr.approvers() : null;
        // Requestor — legacy IDO.SCDUL_PYMN_SUMR_R002 binds ERM.DRAFT_USER_NM
        // (발의자명) for the list "Requestor" column, from the same
        // EXPT_RESOL_MSTR join already fetched above. null for drafts (no
        // approval request) — the column stays blank, matching legacy.
        String draftUserNm = (erm != null && erm.getDraftUserNm() != null && !erm.getDraftUserNm().isBlank())
                ? erm.getDraftUserNm()
                : null;
        return new ScheduledTransferRow(
                s.getScdulPymnNo(),
                s.getScdulPymnTypeCd(),
                s.getCcyCd(),
                // Approval Title — legacy IDO.SCDUL_PYMN_SUMR_R002:58 binds the
                // list "Title" column to ERM.TITL (EXPT_RESOL_MSTR.TITL), NOT
                // SCDUL_PYMN_SUMR.TITL. Null until the batch has an approval row.
                erm != null ? erm.getTitl() : null,
                s.getApprSeqNo(),
                s.getTotalTranAmt(),
                s.getTotTrnsCnt(),
                s.getRegDtm(),
                s.getRegId(),
                // appr_stts via legacy wb_get_appr_stts(...) over EXPT_RESOL_MSTR
                // (R002 binding); SCDUL_PYMN_SUMR has no APPR_STTS column in
                // legacy JEX. null for drafts (no approval master).
                s.getApprSeqNo() != null && !s.getApprSeqNo().isBlank()
                        ? apprSttsByApprSeqNo.get(s.getApprSeqNo())
                        : null,
                null,
                null,
                s.getTrnfYn(),
                s.getOutBankCd(),
                s.getWdrlActNo(),
                payDate,
                // File Name = ORG_FILE_NM (the original uploaded Excel name).
                // Legacy IDO R002:57 aliases `SPS.TITL AS FILE_NM`, but SPS.TITL is
                // the upload batch title which the approval flow / Approval Box edit
                // can repurpose — ORG_FILE_NM is the stable uploaded-file name and is
                // what the Payroll Result list (TransferResultRow.fileNm) and
                // Vendor/Expense already display. Approval Title comes from
                // EXPT_RESOL_MSTR.TITL separately (the `titl` arg above).
                s.getOrgFileNm(),
                docNo,
                s.getWdrlCcyCd(),
                apprFundTp,
                approver,
                approverCount,
                approvers,
                draftUserNm);
    }

    /** First approver name + total chain length + the full ordered chain
     *  (name/status/datetime) for one approval request — feeds the Scheduled
     *  list "Approver" cell (name + "[N]" + click popup). Byte-identical to
     *  {@code VendorPaymentService.ApproverSummary}. */
    private record ApproverSummary(
            String firstName,
            int count,
            List<ScheduledTransferApprover> approvers) {}

    /**
     * Map {@code APPR_SEQ_NO → {first approver name, chain length, chain}} from
     * the {@code EXPT_RESOL_APPRVR_INFM} chain (ordered by {@code APPRVR_LINE_NO}).
     * Drives the Scheduled Payment list's "Approver" cell — primary approver
     * name + the legacy "{@code [N]}" count badge (= chain length) + the click
     * popup. One {@code findChain} per distinct approval request on the page
     * (≤ page size); empty when no row has been submitted for approval.
     * Byte-identical to {@code VendorPaymentService.buildFirstApproverMap}.
     */
    private Map<String, ApproverSummary> buildFirstApproverMap(String bizKey, List<ScdulPymnSumr> rows) {
        List<String> apprSeqNos = rows.stream()
                .map(ScdulPymnSumr::getApprSeqNo)
                .filter(seq -> seq != null && !seq.isBlank())
                .distinct()
                .toList();
        if (apprSeqNos.isEmpty()) return Map.of();
        Map<String, ApproverSummary> out = new java.util.HashMap<>(apprSeqNos.size());
        for (String seq : apprSeqNos) {
            var chain = exptResolApprvrInfmRepository.findChain(bizKey, seq);
            if (!chain.isEmpty()) {
                String nm = chain.get(0).getApprvrNm();
                String firstName = (nm != null && !nm.isBlank()) ? nm.trim() : null;
                Map<String, String> imgUrlByApprvrId = userImageUrlResolver.resolveImageUrls(
                        bizKey, chain.stream().map(a -> a.getApprvrId()).toList());
                List<ScheduledTransferApprover> list = chain.stream()
                        .map(a -> new ScheduledTransferApprover(
                                a.getApprvrNm(), a.getApprvrStts(), a.getApprDtm(),
                                imgUrlByApprvrId.get(a.getApprvrId())))
                        .toList();
                out.put(seq, new ApproverSummary(firstName, chain.size(), list));
            }
        }
        return out;
    }

    /**
     * Map the {@code [ccyCd, sumAmount, docCount, sumTransactionCount]} rows
     * from {@link ScdulPymnSumrRepository#searchCurrencyTotals} into the
     * {@link CurrencyTotal} wire records. The DB aggregate has already summed
     * across the full filtered set and grouped by currency (F-1, legacy
     * IDO.SCDUL_PYMN_SUMR_R003); this method only does the {@code COUNT}-Long →
     * {@code BigDecimal} projection and null-safety. Mirrors
     * {@code InternalTransferService#mapCurrencyTotals}.
     */
    private static List<CurrencyTotal> mapCurrencyTotals(List<Object[]> aggregated) {
        return aggregated.stream()
                .map(r -> new CurrencyTotal(
                        r[1] == null ? BigDecimal.ZERO : (BigDecimal) r[1],
                        r[2] == null ? BigDecimal.ZERO : BigDecimal.valueOf(((Number) r[2]).longValue()),
                        r[0] == null ? "" : (String) r[0],
                        r[3] == null ? BigDecimal.ZERO : (BigDecimal) r[3]))
                .toList();
    }

    private static String requireTenant() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) {
            throw new IllegalStateException("tenant context not populated");
        }
        return bizKey;
    }

    private static void validateDateRange(String fromDate, String toDate) {
        if (fromDate == null || !fromDate.matches("^[0-9]{8}$")
                || toDate == null || !toDate.matches("^[0-9]{8}$")) {
            throw new BusinessException("WBE_VALIDATION");
        }
        if (fromDate.compareTo(toDate) > 0) {
            throw new BusinessException("WBE_VALIDATION");
        }
    }

    private static Pageable pageable(int page, int size,
                                     String sortField, String sortDirection) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 10 : Math.min(size, 200);
        Sort sort = PayrollPaymentSortField.parse(sortField)
                .map(sf -> {
                    SortDirection dir = SortDirection.parse(sortDirection);
                    Sort.Direction d = dir == SortDirection.DESC
                            ? Sort.Direction.DESC : Sort.Direction.ASC;
                    return Sort.by(d, sf.entityProperty())
                            .and(Sort.by(Sort.Order.desc("regDtm")));
                })
                .orElseGet(() -> Sort.by(Sort.Order.desc("regDtm"),
                        Sort.Order.desc("scdulPymnNo")));
        return PageRequest.of(safePage, safeSize, sort);
    }

    private static String buildLikePattern(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        return "%" + trimmed.toLowerCase() + "%";
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String emptyToDefault(String s, String def) {
        String v = emptyToNull(s);
        return v == null ? def : v;
    }

    private static String sanitiseOriginalFilename(String original) {
        if (original == null || original.isBlank()) return "upload.xlsx";
        String leaf = original;
        int sep = Math.max(leaf.lastIndexOf('/'), leaf.lastIndexOf('\\'));
        if (sep >= 0) leaf = leaf.substring(sep + 1);
        leaf = leaf.replaceAll("[^A-Za-z0-9._\\- (),]", "_");
        return leaf.isBlank() ? "upload.xlsx" : leaf;
    }

    private void persistUploadedFile(String bizKey, String saveFileNm, MultipartFile file) {
        Path target = fileResolver.resolve(bizKey, saveFileNm);
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("feature=payroll-payment file-write bizKey={} saveFileNm={} bytes={}",
                    bizKey, saveFileNm, file.getSize());
        } catch (IOException e) {
            log.error("feature=payroll-payment file-write-failed bizKey={} saveFileNm={}",
                    bizKey, saveFileNm, e);
            throw new BusinessException("WBE2070");
        }
    }
}
