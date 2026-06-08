package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
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
import com.kosign.wecms.domain.notification.service.NotificationService;
import com.kosign.wecms.domain.payment.dispatch.service.TransferDispatchExecutor;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
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
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolFileRepository;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprDv;
import com.kosign.wecms.domain.payment.service.DocNoGenerator;
import com.kosign.wecms.domain.payment.internaltransfer.service.CutoffValidator;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferFileResolver;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferKeyGenerator;
import com.kosign.wecms.domain.payment.internaltransfer.service.ScdulPymnTypeCd;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Payment &gt; Expense Payment — Scheduled list service.
 *
 * <p>Mirrors the legacy {@code pymn_0003_001_r001} read path (which the
 * legacy {@code PymnHelper.pymn_0003_001_r001} parameterises by
 * {@code SCDUL_PYMN_TYPE_CD = '02'}). Expense, Vendor and Internal Transfer share
 * the same physical {@code SCDUL_PYMN_SUMR} table, so this service reuses
 * {@link ScdulPymnSumrRepository} and the wire DTOs from the
 * {@code internaltransfer.dto} package — only the type discriminator
 * differs.
 *
 * <p><b>Phase A scope</b>: read-only list. Detail, create / draft upload,
 * dispatch, request-approval, and the Result list/detail land in
 * subsequent MRs. See workspace memory
 * {@code project_payment_module_reference.md} for the locked baseline.
 *
 * <p>Shared DTOs note: {@link ScheduledTransferRow},
 * {@link ScheduledTransferListResponse}, and {@link CurrencyTotal} live
 * under {@code internaltransfer.dto} for historical reasons (they were
 * the first consumer). The wire contract is module-agnostic — every
 * Payment-module Scheduled list emits the same field set — so Expense
 * imports them rather than duplicating. If a future Payment module
 * genuinely needs a different shape, lift the shared records to
 * {@code domain/payment/common/dto/} first.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpensePaymentService {

    /** Audit-log {@code WSVC_ID} for every mutating endpoint. Expense's
     *  refactor slug — never legacy {@code pymn_0001_001}. */
    static final String WSVC_ID = "expense-payment";

    /** Sub-resource name written into the audit log's {@code REF_NO} column. */
    static final String REF_NO = "SCDUL_PYMN_SUMR";

    static final String AUDIT_ACTIVITY_KEY         = "audit.expense_payment.activity";
    static final String AUDIT_UPDATE_KEY           = "audit.expense_payment.update.detail";
    static final String AUDIT_DELETE_KEY           = "audit.expense_payment.delete.detail";
    static final String AUDIT_DISPATCH_KEY         = "audit.expense_payment.dispatch.detail";
    static final String AUDIT_REQUEST_APPROVAL_KEY = "audit.expense_payment.request_approval.detail";
    static final String AUDIT_APPROVE_KEY          = "audit.expense_payment.approve.detail";
    static final String AUDIT_REJECT_KEY           = "audit.expense_payment.reject.detail";
    static final String AUDIT_WITHDRAW_APPROVAL_KEY =
            "audit.expense_payment.withdraw_approval.detail";
    static final String AUDIT_CHANGE_APPROVAL_LINE_KEY =
            "audit.expense_payment.change_approval_line.detail";
    static final String AUDIT_CREATE_KEY           = "audit.expense_payment.create.detail";
    /** Audit detail key for per-line Re-run action (legacy
     *  `pymn_0004_002.ts` ETC-column Re-run button). */
    static final String AUDIT_RERUN_KEY = "audit.expense_payment.rerun.detail";

    /** Transient bank-side response codes that the legacy live system
     *  considers retryable. Same set as Internal Transfer. */
    static final java.util.Set<String> RERUN_VALID_RS_CDS =
            java.util.Set.of("0001", "0002", "0003", "0004", "0005");

    /** ERP Transaction History {@code TRNS_TYPE_CD} for Expense Scheduled
     *  Payment rows. Pairs with the
     *  {@code settings.erp_transaction_history.type.02} locale entry
     *  ("Expense Scheduled Payment") — legacy parity with
     *  {@code wabooks.enums.ErpTypeCd.VENDOR_SCHEDULED_PAYMENT}. */
    static final String ERP_TRNS_TYPE_EXPENSE_SCHEDULED_PAYMENT = "03";

    /** APPR_STTS code stamped on Request Approval — initial Pending state. */
    static final String APPR_STTS_PENDING  = "P";

    /** Legacy {@code APPRVR_STTS} code stamped on the master when an
     *  approver presses Approve. */
    static final String APPR_STTS_APPROVED = "C";

    /** APPR_STTS code stamped while approval is mid-chain. */
    static final String APPR_STTS_IN_PROGRESS = "I";

    /** Legacy {@code APPRVR_STTS} code stamped on the master when an
     *  approver presses Reject. */
    static final String APPR_STTS_REJECTED = "X";

    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ScdulPymnSumrRepository       sumrRepository;
    private final ScdulPymnSumrDetlRepository   detlRepository;
    private final AcctMstrRepository            acctMstrRepository;
    private final BankInfmRepository            bankInfmRepository;
    private final CutoffValidator               cutoffValidator;
    private final ExpensePaymentExcelParser      excelParser;
    private final InternalTransferKeyGenerator  keyGenerator;
    private final InternalTransferFileResolver  fileResolver;
    private final ErpTrnsHistoryWriter          erpHistoryWriter;
    private final AuditLogService               auditLogService;
    private final TransferDispatchExecutor      dispatchExecutor;
    /** Approver-chain authority — see InternalTransferService for the rationale. */
    private final ApprovalBoxService            approvalBoxService;
    /** Shared projector that resolves the per-line bank name +
     *  chart-account breadcrumb the Request Approval dialog renders. */
    private final com.kosign.wecms.domain.payment.common.TransferLineDetailMapper transferLineDetailMapper;
    /** Folds the user-supplied bank code into the canonical NAPAS code
     *  before it lands on {@code SCDUL_PYMN_SUMR.OUT_BANK_CD}. Mirrors
     *  legacy {@code PymnHelper.uploadPaymentExcel:1315-1317}. */
    private final com.kosign.wecms.domain.payment.common.OutBankCdNormalizer outBankCdNormalizer;
    /** Dispatch-table writes for the per-line Re-run action. */
    private final com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository tbPayFileDataRepository;
    /** Per-line dispatch-result lookup (shared resolver). */
    private final com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver dispatchResultResolver;
    /** Approval-Box fan-out — see InternalTransferService for rationale. */
    private final ApprovalBoxFanoutService      approvalBoxFanoutService;
    private final Clock                         clock;
    private final ExptResolMstrRepository       exptResolMstrRepository;
    private final ExptResolApprvrInfmRepository exptResolApprvrInfmRepository;
    /** Approval-request attachments ({@code EXPT_RESOL_FILE}) keyed by
     *  {@code APPR_SEQ_NO} — surfaces the Scheduled-detail {@code attachments}
     *  list (consumed by the shared Approval Details popup). Shared
     *  repository; same wiring as {@code InternalTransferService}. */
    private final ExptResolFileRepository       exptResolFileRepository;
    private final NotificationService           notificationService;
    private final DocNoGenerator                docNoGenerator;
    private final BusiInfmRepository            busiInfmRepository;

    /**
     * List Expense Payment batches for the current tenant in the given
     * {@code REG_DTM} window. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#list}
     * but stamps {@code SCDUL_PYMN_TYPE_CD = '02'} so only Expense batches
     * are returned.
     */
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

        // Pre-built LIKE pattern — same `bytea`-avoidance pattern used by
        // InternalTransferService.
        String srchWdPattern = buildLikePattern(srchWd);
        String apprTypeArg   = (apprType == null || apprType.isBlank()) ? null : apprType.trim();

        Page<ScdulPymnSumr> rows = sumrRepository.search(
                bizKey, ScdulPymnTypeCd.EXPENSE.code(),
                fromDtm, toDtm,
                srchWdPattern,
                null,
                apprTypeArg,
                pageable);

        // One EXPT_RESOL_MSTR fetch feeds the Approval Ref (DOC_NO), the
        // Schedule Date (PAY_DATE) and the Requestor (DRAFT_USER_NM) columns —
        // all three join from EXPT_RESOL_MSTR on APPR_SEQ_NO (legacy
        // IDO.SCDUL_PYMN_SUMR_R002). DOC_NO is the authoritative Approval Ref
        // source — ERP_FILE_NUM write-back may be absent on legacy / seed
        // records. Mirrors PayrollPaymentService.
        Map<String, ExptResolMstr> exptBySeqNo = fetchExptResolByApprSeqNo(bizKey, rows.getContent());
        // G4: Approver column — first approver name + chain length ([N] badge) +
        // full ordered chain (popup). Mirrors InternalTransferService.buildFirstApproverMap.
        Map<String, ApproverSummary> approverByApprSeqNo = buildFirstApproverMap(bizKey, rows.getContent());

        List<ScheduledTransferRow> dtoRows = rows.getContent().stream()
                .map(s -> toListRow(s, exptBySeqNo, approverByApprSeqNo))
                .toList();
        List<CurrencyTotal> totals = computeCurrencyTotals(rows.getContent());

        log.info("feature=expense-payment list bizKey={} total={} page={} size={}",
                bizKey, rows.getTotalElements(), page, pageable.getPageSize());
        return new ScheduledTransferListResponse(
                rows.getTotalElements(), page, pageable.getPageSize(),
                totals, dtoRows);
    }

    /* ----------------------------------------------------------------
     *  Detail
     * ---------------------------------------------------------------- */

    /**
     * Detail by SCDUL_PYMN_NO or DOC_NO. Returns master fields + per-line list.
     * Mirrors legacy {@code pymn_0003_002_r001} (Expense Payment detail).
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
                        bizKey, id, ScdulPymnTypeCd.EXPENSE.code())
                .or(() ->
                    exptResolMstrRepository.findByBizKeyAndDocNoAndDelYn(bizKey, id, "N")
                            .filter(m -> m.getApprSeqNo() != null)
                            .flatMap(m -> sumrRepository.findByBizKeyAndApprSeqNoAndScdulPymnTypeCd(
                                    bizKey, m.getApprSeqNo(), ScdulPymnTypeCd.EXPENSE.code()))
                )
                .orElseThrow(() -> new BusinessException("WBE2060"));

        String resolvedId = master.getScdulPymnNo();
        Sort lineSort = Sort.by(Sort.Order.asc("scdulPymnDetlNo"));
        java.util.Map<String, com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount> chartByCode =
                transferLineDetailMapper.buildChartIndex(bizKey);
        List<TransferLineDetail> lines = detlRepository
                .findByBizKeyAndScdulPymnNo(bizKey, resolvedId, lineSort)
                .stream()
                .map(d -> transferLineDetailMapper.toLineDetail(d, chartByCode))
                .toList();

        // G2 (Scheduled detail): requester display name (legacy #requestor_name cell =
        // EXPT_RESOL_MSTR.DRAFT_USER_NM), looked up by APPR_SEQ_NO. null when the batch has
        // no approval request yet — the frontend falls back to regId. Same wiring as the
        // Result-detail fix + InternalTransferService.
        String apprSeqNo = master.getApprSeqNo();
        // Load the approval master once and reuse it for both the requester
        // display name and the scheduled/pay date (EXPT_RESOL_MSTR.PAY_DATE) —
        // one round-trip, mirroring InternalTransferService.detail().
        java.util.Optional<ExptResolMstr> apprMstr = (apprSeqNo != null && !apprSeqNo.isBlank())
                ? exptResolMstrRepository
                        .findByBizKeyAndApprSeqNoIn(bizKey, java.util.List.of(apprSeqNo))
                        .stream()
                        .findFirst()
                : java.util.Optional.empty();
        String requestor = apprMstr
                .map(ExptResolMstr::getDraftUserNm)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        // Scheduled / pay date — EXPT_RESOL_MSTR.PAY_DATE (yyyyMMdd), the same
        // source the Approval Box entry surfaces; lets the Expense detail popup
        // show Scheduled Date consistently. Null → frontend hides the row.
        String payDate = apprMstr
                .map(ExptResolMstr::getPayDate)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        // Payroll Type — EXPT_RESOL_MSTR.APPR_FUND_TP (0/1/9), null for
        // non-payroll; the frontend resolves the label. Lets the Approval
        // Details popup show Payroll Type consistently across entry points.
        String apprFundTp = apprMstr
                .map(ExptResolMstr::getApprFundTp)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);

        // G5 (Scheduled detail): approval-request attachments from EXPT_RESOL_FILE,
        // keyed by APPR_SEQ_NO (ordered by FILE_ID). Empty when the batch has no
        // approval request yet. Byte-identical to InternalTransferService.detail().
        // The shared Approval Details popup consumes this list; the detail page's
        // own file card stays the uploaded-Excel file (legacy parity).
        List<ApprovalAttachmentRow> attachments =
                (apprSeqNo == null || apprSeqNo.isBlank())
                        ? List.of()
                        : exptResolFileRepository
                                .findByBizKeyAndApprSeqNoOrderByFileIdAsc(bizKey, apprSeqNo)
                                .stream()
                                .map(f -> new ApprovalAttachmentRow(
                                        f.getFileId(),
                                        f.getOrgFileNm(),
                                        f.getFileSize(),
                                        f.getRegNm(),
                                        f.getRegDtm()))
                                .toList();

        return new ScheduledTransferDetailResponse(
                master.getScdulPymnNo(),
                master.getScdulPymnTypeCd(),
                master.getOutBankCd(),
                transferLineDetailMapper.resolveBankNm(master.getOutBankCd()),
                master.getWdrlActNo(),
                master.getTitl(),
                master.getTotalTranAmt(),
                master.getTotTrnsCnt(),
                master.getErpFileNum(),
                master.getRegId(),
                requestor,
                master.getRegDtm(),
                master.getApprSeqNo(),
                master.getApprStts(),
                null,
                master.getModDtm(),
                master.getTrnfYn(),
                master.getTrnfDtm(),
                master.getCcyCd(),
                master.getWdrlCcyCd(),
                master.getOrgFileNm(),
                master.getFileSize(),
                lines,
                attachments,
                payDate,
                apprFundTp);
    }

    /* ----------------------------------------------------------------
     *  Withdrawal-account picker (legacy comm_1001_001)
     * ---------------------------------------------------------------- */

    /**
     * List firm-banking-capable withdrawal accounts the current tenant
     * may pick as the source of a Expense Payment batch. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#listWithdrawalAccounts}
     * — the lookup is module-agnostic (legacy {@code comm_1001_001} is
     * shared across every payment type).
     */
    @Transactional(readOnly = true)
    public List<WithdrawalAccountRow> listWithdrawalAccounts(String ccyCd) {
        String bizKey = requireTenant();
        String filter = (ccyCd == null || ccyCd.isBlank())
                ? null : ccyCd.trim().toUpperCase();
        return acctMstrRepository.findWithdrawalAccounts(bizKey, filter).stream()
                .map(this::toWithdrawalAccountRow)
                .toList();
    }

    private WithdrawalAccountRow toWithdrawalAccountRow(AcctMstr a) {
        String bankNm = bankInfmRepository.findById(a.getBankCd())
                .map(BankInfm::getRprsBankNm)
                .orElse(a.getBankCd());
        return new WithdrawalAccountRow(
                a.getBankCd(),
                bankNm,
                a.getAcctNo(),
                a.getAcctTypeCd(),
                a.getCcyCd(),
                a.getAcctNickNm());
    }

    /* ----------------------------------------------------------------
     *  Excel parse + save draft (legacy pymn_0003_002_r001 / _c001)
     * ---------------------------------------------------------------- */

    /**
     * Parse + per-line validate an uploaded Excel template. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#parseExcel}
     * — Expense uses the same template format as Internal Transfer, so
     * the parser is reused verbatim. File-shape envelope (tenant
     * context, non-empty, .xls/.xlsx extension, withdrawal bank code
     * present) is validated before handing off to the workbook walk.
     */
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

    /**
     * Save a draft Expense Payment batch. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#createDraft}
     * but stamps {@code SCDUL_PYMN_TYPE_CD = '02'} so the batch is
     * scoped to the Expense module. The {@code SCDUL_PYMN_NO} key
     * namespace is shared with Internal Transfer (the physical table
     * is the same), so the {@link InternalTransferKeyGenerator} is
     * reused verbatim.
     *
     * <p>The original Excel file is persisted to
     * {@code <storage-root>/<biz_key>/internal-transfer/<save_file_nm>}
     * via the same {@link InternalTransferFileResolver} — keeping the
     * single storage tree per tenant keeps the download path module-
     * agnostic.
     */
    @Transactional
    public CreateScheduledTransferResponse createDraft(
            CreateScheduledTransferRequest req,
            MultipartFile file) {

        String bizKey = requireTenant();
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        String scdulPymnNo = keyGenerator.nextScdulPymnNo(bizKey);

        ScdulPymnSumr master = new ScdulPymnSumr();
        master.setBizKey(bizKey);
        master.setScdulPymnNo(scdulPymnNo);
        master.setScdulPymnTypeCd(ScdulPymnTypeCd.EXPENSE.code());
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
            persistUploadedFile(bizKey, saveFileNm, file);
            master.setOrgFileNm(orgFileNm);
            master.setSaveFileNm(saveFileNm);
            master.setFileSize(BigDecimal.valueOf(file.getSize()));
        }
        master.setRegId(userId);
        master.setRegDtm(now);
        master.setModId(userId);
        master.setModDtm(now);
        sumrRepository.save(master);

        int seq = 1;
        for (TransferLineInput line : req.lines()) {
            ScdulPymnSumrDetl detl = new ScdulPymnSumrDetl();
            detl.setBizKey(bizKey);
            detl.setScdulPymnNo(scdulPymnNo);
            detl.setScdulPymnDetlNo(String.format("%06d", seq++));
            detl.setTranAmt(line.tranAmt());
            detl.setTrnfCcyCd(emptyToDefault(line.trnfCcyCd(), req.trnfCcyCd()));
            detl.setAntDivCd(emptyToNull(line.antDivCd()));
            detl.setRcptNm(emptyToNull(line.rcptNm()));
            detl.setTarBankCd(emptyToNull(line.tarBankCd()));
            detl.setFincMdaNo(line.fincMdaNo());
            detl.setTranUsag(emptyToNull(line.tranUsag()));
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

        // Preserve the uploaded Excel under ERP Transaction History so
        // the user can later download it from Settings > ERP Transaction
        // History (legacy parity with `PymnHelper.uploadPaymentExcel` +
        // `ErpTrnsHisHelper.erpTrnsHisUploader`). TRNS_TYPE_CD = "03"
        // (Expense Scheduled Payment) lands the row in the "ERP" tab.
        // The writer is REQUIRES_NEW + swallows its own errors, so an
        // ERP audit-write failure cannot roll back the batch create.
        if (master.getSaveFileNm() != null && !master.getSaveFileNm().isBlank()) {
            try {
                java.nio.file.Path source = fileResolver.resolve(bizKey, master.getSaveFileNm());
                erpHistoryWriter.recordUploadedFile(
                        ERP_TRNS_TYPE_EXPENSE_SCHEDULED_PAYMENT,
                        source,
                        master.getOrgFileNm());
            } catch (RuntimeException ex) {
                log.warn(
                        "feature=expense-payment ERP history skip "
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

        log.info("feature=expense-payment create bizKey={} scdulPymnNo={} lineCount={} totalAmount={}",
                bizKey, scdulPymnNo, req.lines().size(), totalAmount);
        return new CreateScheduledTransferResponse(scdulPymnNo);
    }

    /* ----------------------------------------------------------------
     *  File download (legacy pymn_0003_002 file-row glyph)
     * ---------------------------------------------------------------- */

    /**
     * Resolve the physical file behind a Expense batch's original Excel.
     * Mirrors {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#resolveDownload}
     * — the storage tree is shared (the create path persists every
     * payment-module upload under
     * {@code <storage-root>/<biz_key>/internal-transfer/<save_file_nm>}),
     * so the same {@link InternalTransferFileResolver} resolves Expense
     * files too.
     *
     * <ul>
     *   <li>Row not found / wrong tenant → {@code WBE2060}.</li>
     *   <li>Row exists but no file (legacy import or pre-storage row)
     *       or file missing on disk → {@code WBE2069}.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public DownloadResource resolveDownload(String scdulPymnNo) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, scdulPymnNo);

        String saveFileNm = master.getSaveFileNm();
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new BusinessException("WBE2069");
        }

        java.nio.file.Path path = fileResolver.resolve(bizKey, saveFileNm);
        if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("WBE2069");
        }
        long fileSize = master.getFileSize() == null
                ? path.toFile().length()
                : master.getFileSize().longValueExact();
        String displayName = master.getOrgFileNm() == null || master.getOrgFileNm().isBlank()
                ? saveFileNm
                : master.getOrgFileNm();
        return new DownloadResource(path, displayName, saveFileNm, fileSize);
    }

    /** Controller carrier for the resolved file — same shape as
     *  Internal Transfer's {@code DownloadResource}. */
    public record DownloadResource(
            java.nio.file.Path path, String orgFileNm, String saveFileNm, long fileSize) { }

    /* ----------------------------------------------------------------
     *  Delete draft batch (legacy pymn_0003_001_d001)
     * ---------------------------------------------------------------- */

    /**
     * Update the chart-of-accounts assignment on one Expense Payment
     * line. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#updateReportingAccount}
     * — same wire shape, same audit pattern, scoped to the Expense
     * {@code SCDUL_PYMN_TYPE_CD} via {@link #loadMaster}. Used by the
     * Scheduled detail page's inline picker.
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

        log.info("feature=expense-payment update-reporting bizKey={} scdulPymnNo={} lineId={} chartAcctCd={}",
                bizKey, id, lineId, req.chartAcctCd());
    }

    /**
     * Re-run one detail line — legacy parity with
     * `pymn_0004_002.ts:441-472`. Same shape as
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#rerun}.
     */
    @Transactional
    public void rerun(String scdulPymnNo, String scdulPymnDetlNo) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, scdulPymnNo);
        ScdulPymnSumrDetl detl = detlRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(bizKey, scdulPymnNo, scdulPymnDetlNo)
                .orElseThrow(() -> new BusinessException("WBE2061"));

        String tranDt    = detl.getPayTranDt();
        String tranSeq   = detl.getPayTranSeq();
        String fileSeqNo = master.getPayFileSeqNo();
        if (tranDt == null || tranDt.isBlank()
                || tranSeq == null || tranSeq.isBlank()
                || fileSeqNo == null || fileSeqNo.isBlank()) {
            throw new BusinessException("WBE2122");
        }

        java.util.Map<String,
                com.kosign.wecms.domain.payment.dispatch.service.DispatchResult> results =
                dispatchResultResolver.resolve(master, java.util.List.of(detl));
        com.kosign.wecms.domain.payment.dispatch.service.DispatchResult result =
                results.get(scdulPymnDetlNo);
        if (result == null || result.rsCd() == null
                || !RERUN_VALID_RS_CDS.contains(result.rsCd())) {
            throw new BusinessException("WBE2123");
        }
        String procDttm = result.procDttm();
        if (procDttm == null || procDttm.length() < 8) {
            throw new BusinessException("WBE2124");
        }
        String todayYmd = java.time.OffsetDateTime.now(clock)
                .withOffsetSameInstant(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!todayYmd.equals(procDttm.substring(0, 8))) {
            throw new BusinessException("WBE2124");
        }

        java.math.BigDecimal seq;
        try {
            seq = new java.math.BigDecimal(tranSeq);
        } catch (NumberFormatException ex) {
            throw new BusinessException("WBE2122");
        }
        com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileDataId dataId =
                new com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileDataId(
                        tranDt, bizKey, fileSeqNo, seq);
        tbPayFileDataRepository.findById(dataId).ifPresent(data -> {
            data.setRsCd(null);
            tbPayFileDataRepository.save(data);
        });

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(scdulPymnNo)
                .refNo2(scdulPymnDetlNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_RERUN_KEY)
                .build());

        log.info("feature=expense-payment rerun bizKey={} scdulPymnNo={} lineId={} tranDt={} seq={}",
                bizKey, scdulPymnNo, scdulPymnDetlNo, tranDt, seq);
    }

    /**
     * Delete a draft Expense Payment batch. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#delete}
     * but stamps the Expense type discriminator and Expense audit keys.
     *
     * <p>Rejects when {@code APPR_SEQ_NO} is already set
     * ({@code WBE2062}) and deletes the master + line rows. Before the
     * delete, snapshots the file metadata so the ERP-history writer
     * can preserve the original Excel — the user can still download
     * the file from Settings &gt; ERP Transaction History (the row
     * lands in the "Deleted" tab via {@code ERP_TYPE='D'} +
     * {@code TRNS_TYPE_CD='02'}).
     */
    @Transactional
    public void delete(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        if (master.getApprSeqNo() != null && !master.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2062");
        }

        // Snapshot the file metadata BEFORE the row goes away so the
        // ERP-history writer can copy the Excel and reference it. The
        // writer is REQUIRES_NEW + swallows its own errors, so an ERP
        // audit-write failure cannot roll back the delete itself.
        String orgFileNm  = master.getOrgFileNm();
        String saveFileNm = master.getSaveFileNm();

        detlRepository.deleteByBizKeyAndScdulPymnNo(bizKey, id);
        sumrRepository.deleteById(new ScdulPymnSumrId(bizKey, id));

        // Preserve the uploaded Excel in ERP Transaction History so the
        // user can still download it after the source batch is gone.
        // TRNS_TYPE_CD = "03" → Expense Scheduled Payment (per the
        // `settings.erp_transaction_history.type.02` locale entry);
        // writer stamps ERP_TYPE='D' so the row lands in the "Deleted"
        // tab. No-op when the batch has no uploaded file.
        if (saveFileNm != null && !saveFileNm.isBlank()) {
            try {
                java.nio.file.Path source = fileResolver.resolve(bizKey, saveFileNm);
                erpHistoryWriter.recordDeletedFile(
                        ERP_TRNS_TYPE_EXPENSE_SCHEDULED_PAYMENT,
                        source,
                        orgFileNm);
            } catch (RuntimeException ex) {
                log.warn(
                        "feature=expense-payment ERP history skip "
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

        log.info("feature=expense-payment delete bizKey={} scdulPymnNo={}", bizKey, id);
    }

    /* ----------------------------------------------------------------
     *  Request Approval (legacy comm_0194_001 → comm_0192_001_c005)
     * ---------------------------------------------------------------- */

    /**
     * Submit one batch for approval. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#requestApproval}.
     * Stamps a synthesised {@code APPR_SEQ_NO} via per-tenant MAX+1 and
     * writes an audit row. The full per-leg approval-line fan-out lands
     * with the EXPT_RESOL_* port.
     */
    @Transactional
    public RequestApprovalResponse requestApproval(String id, RequestApprovalRequest req) {
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
            log.info("feature=expense-payment apprSeqNo race retrying once bizKey={}", bizKey);
            apprSeqNo = allocateApprSeqNo(bizKey);
            docNo = stampApproval(master, apprSeqNo, req);
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .refNo2(apprSeqNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_REQUEST_APPROVAL_KEY)
                .build());

        log.info(
                "feature=expense-payment request-approval bizKey={} scdulPymnNo={} apprSeqNo={} apprLineNo={} approvers={}",
                bizKey, id, apprSeqNo, req.apprLineNo(), req.approvers().size());

        return new RequestApprovalResponse(apprSeqNo, docNo);
    }

    /** Per-tenant MAX+1 across both EXPT_RESOL_MSTR and SCDUL_PYMN_SUMR so the
     *  newly issued APPR_SEQ_NO is above any existing rows in either table. */
    private String allocateApprSeqNo(String bizKey) {
        Long fromMstr = exptResolMstrRepository.findMaxApprSeqNoByBizKey(bizKey);
        Long fromSumr = sumrRepository.findMaxApprSeqNoByBizKey(bizKey);
        long max = Math.max(
                fromMstr == null ? 0L : fromMstr,
                fromSumr == null ? 0L : fromSumr);
        return Long.toString(max + 1L);
    }

    private String stampApproval(ScdulPymnSumr master, String apprSeqNo,
                                RequestApprovalRequest req) {
        String bizKey  = master.getBizKey();
        String userId  = TenantContext.currentUserId();
        String userNm  = TenantContext.currentUserNm();
        String nowDtm  = ZonedDateTime.ofInstant(clock.instant(), VIETNAM_ZONE).format(DTM14);
        String draftDate = nowDtm.substring(0, 8);
        String draftTime = nowDtm.substring(8, 14);

        // 1. Stamp SCDUL_PYMN_SUMR
        master.setApprSeqNo(apprSeqNo);
        master.setApprStts("P");
        if (req.postTitle() != null && !req.postTitle().isBlank()) {
            master.setTitl(req.postTitle().trim());
        }
        master.setModDtm(nowDtm);
        if (userId != null) master.setModId(userId);
        sumrRepository.saveAndFlush(master);

        // 2. Write EXPT_RESOL_MSTR master row
        ExptResolMstr mstr = new ExptResolMstr();
        mstr.setBizKey(bizKey);
        mstr.setApprSeqNo(apprSeqNo);
        mstr.setApprDv(ApprDv.EXPENSE.code());
        mstr.setApprSts("0");
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
        mstr.setApprBoxUseYn("Y");
        mstr.setApprDatDispYn("N");
        mstr.setCmpnLogoDispYn("Y");
        mstr.setRegDispYn("Y");
        mstr.setTitl(req.postTitle() != null ? req.postTitle().trim() : "");
        String docNo = docNoGenerator.nextDocNo(bizKey);
        mstr.setDocNo(docNo);
        mstr.setDocNm(master.getTitl() != null ? master.getTitl() : master.getScdulPymnNo());
        BigDecimal amt = master.getTotalTranAmt() != null ? master.getTotalTranAmt() : BigDecimal.ZERO;
        String ccy = master.getCcyCd() != null ? master.getCcyCd().toUpperCase() : "VND";
        boolean isUsd = "USD".equals(ccy);
        boolean isVnd = "VND".equals(ccy);
        mstr.setTotPayKhrAmt(isUsd ? BigDecimal.ZERO : amt);
        mstr.setTotPayUsdAmt(isUsd ? amt : BigDecimal.ZERO);
        mstr.setTotPayVndAmt(isVnd ? amt : BigDecimal.ZERO);
        mstr.setPayDate(req.payDate() != null && !req.payDate().isBlank() ? req.payDate().trim() : null);
        mstr.setDelYn("N");
        mstr.setPayEndYn("N");
        mstr.setApprTypeCd("00");
        mstr.setRegId(userId != null ? userId : "");
        mstr.setRegDtm(nowDtm);
        mstr.setModId(userId);
        mstr.setModDtm(nowDtm);
        exptResolMstrRepository.save(mstr);

        // Write doc_no back to SCDUL_PYMN_SUMR so the list query can surface it
        master.setErpFileNum(docNo);
        sumrRepository.save(master);

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

        // Phase-1 notification producer: fan an approval-request notification
        // out to the approver chain. Best-effort + isolated (REQUIRES_NEW) so a
        // notification failure never rolls back this approval submission.
        notificationService.notifyApprovalRequested(
                ApprDv.EXPENSE.slug(), apprSeqNo, docNo, req.postTitle(),
                approvers.stream()
                        .map(a -> new NotificationService.Recipient(a.apprvrId(), a.apprvrNm()))
                        .toList());
        return docNo;
    }

    /* ----------------------------------------------------------------
     *  Approval action — Approve / Reject (legacy comm_0195_001
     *  footer buttons → bltb_0001_003_u001)
     * ---------------------------------------------------------------- */

    /**
     * Approve or reject the active approval request on one batch.
     * Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService#actOnApproval}.
     */
    @Transactional
    public void actOnApproval(String id, ApprovalActionRequest req) {
        if (req == null) {
            throw new BusinessException("WBE_VALIDATION", "request body is required");
        }
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        if (master.getApprSeqNo() == null || master.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2094");
        }

        String current = master.getApprStts();
        if (APPR_STTS_APPROVED.equals(current) || APPR_STTS_REJECTED.equals(current)) {
            throw new BusinessException("WBE2095");
        }

        // Delegate approver-chain logic — multi-approver workflows stay
        // "In Progress" until every approver has acted.
        ApprovalBoxActionResponse result = req.isApprove()
                ? approvalBoxService.approve(master.getApprSeqNo())
                : approvalBoxService.reject(master.getApprSeqNo());

        String next = mapApprStts(result.apprStts());
        master.setApprStts(next);
        master.setModDtm(OffsetDateTime.now(clock).format(DTM14));
        String userId = TenantContext.currentUserId();
        if (userId != null) master.setModId(userId);
        sumrRepository.saveAndFlush(master);

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
                "feature=expense-payment approval-action bizKey={} scdulPymnNo={} apprSeqNo={} action={} apprStts={}",
                bizKey, id, master.getApprSeqNo(), req.action(), next);
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
            throw new BusinessException("WBE2094");
        }

        approvalBoxFanoutService.withdrawApproval(apprSeqNo);

        master.setApprSeqNo(null);
        master.setApprStts(null);
        master.setErpFileNum(null);
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

        log.info("feature=expense-payment withdraw-approval bizKey={} scdulPymnNo={} apprSeqNo={}",
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
            throw new BusinessException("WBE2094");
        }

        approvalBoxFanoutService.changeApprovalLine(apprSeqNo, apprLineNo);

        master.setApprStts(APPR_STTS_PENDING);
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
                .detailKey(AUDIT_CHANGE_APPROVAL_LINE_KEY)
                .build());

        log.info("feature=expense-payment change-approval-line bizKey={} scdulPymnNo={} apprSeqNo={} newApprLineNo={}",
                bizKey, id, apprSeqNo, apprLineNo);
    }

    /** Map approval-box "P/I/C/R" → SCDUL_PYMN_SUMR "P/I/C/X". */
    private static String mapApprStts(String raw) {
        if (raw == null) return APPR_STTS_PENDING;
        return switch (raw) {
            case "C" -> APPR_STTS_APPROVED;
            case "I" -> APPR_STTS_IN_PROGRESS;
            case "R" -> APPR_STTS_REJECTED;
            default  -> APPR_STTS_PENDING;
        };
    }

    /* ----------------------------------------------------------------
     *  Dispatch / Execute Transfer (legacy pymn_0003_001_c001)
     * ---------------------------------------------------------------- */

    /**
     * Execute the dispatch flow. Mirrors
     * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDispatchService#dispatch}
     * but stamps the Expense type discriminator and Expense audit keys.
     * Cut-off-time gate, {@code TRNF_YN='Y'} stamp on the master,
     * and {@code TB_PAY_TARGET} / {@code TB_PAY_FILE_MST} +
     * {@code TB_PAY_FILE_DATA} writes via the shared
     * {@code TransferDispatchExecutor}.
     */
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
                bizKey, id, Sort.by(Sort.Order.asc("scdulPymnDetlNo")));
        TransferNetwork network = lines.isEmpty()
                ? TransferNetwork.CITAD
                : TransferNetwork.fromAntDivCd(lines.get(0).getAntDivCd());
        cutoffValidator.validate(master.getOutBankCd(), network);

        dispatchExecutor.execute(master, lines, req.trnfCcyCd());

        master.setTrnfYn(TrnfYn.TRANSFERRED.code());
        master.setTrnfDtm(now);
        master.setModId(userId);
        master.setModDtm(now);
        sumrRepository.saveAndFlush(master);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_DISPATCH_KEY)
                .msgArgs(new Object[]{ req.trnfCcyCd() })
                .build());

        log.info("feature=expense-payment dispatch bizKey={} scdulPymnNo={} trnfCcyCd={} network={} lines={}",
                bizKey, id, req.trnfCcyCd(), network, lines.size());
    }

    /* ----------------------------------------------------------------
     *  Helpers
     * ---------------------------------------------------------------- */

    private ScdulPymnSumr loadMaster(String bizKey, String scdulPymnNo) {
        return sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, scdulPymnNo, ScdulPymnTypeCd.EXPENSE.code())
                .orElseThrow(() -> new BusinessException("WBE2060"));
    }

    /**
     * Fetch the {@code EXPT_RESOL_MSTR} rows backing the current page's
     * batches, keyed by {@code APPR_SEQ_NO}. One query; callers derive
     * the Approval Ref ({@code DOC_NO}), Schedule Date ({@code PAY_DATE})
     * and Requestor ({@code DRAFT_USER_NM}) columns from the same rows —
     * mirrors the legacy {@code IDO.SCDUL_PYMN_SUMR_R002} LEFT JOIN and
     * {@code PayrollPaymentService.fetchExptResolByApprSeqNo}.
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
            Map<String, ApproverSummary> approverByApprSeqNo) {
        ExptResolMstr erm = s.getApprSeqNo() != null ? exptBySeqNo.get(s.getApprSeqNo()) : null;
        String docNo = (erm != null && erm.getDocNo() != null)
                ? erm.getDocNo()
                : s.getErpFileNum();
        // Schedule Date = EXPT_RESOL_MSTR.PAY_DATE (the requested payment date
        // set at Request Approval), NOT SCDUL_PYMN_SUMR.PAY_TRAN_DT (the
        // dispatch date, which stays null until the batch is transferred to the
        // bank). Mirrors legacy pymn_0003_001 list PAY_DATE column
        // (IDO.SCDUL_PYMN_SUMR_R002 `, ERM.PAY_DATE`). Null until the batch has
        // an approval row — legacy-correct blank.
        String payDate = erm != null ? erm.getPayDate() : null;
        // Requestor = EXPT_RESOL_MSTR.DRAFT_USER_NM (발의자명, the requesting
        // user's display NAME) — NOT SCDUL_PYMN_SUMR.REG_ID. Null until the
        // batch has an approval row; the frontend falls back to reg_id.
        String draftUserNm = erm != null ? erm.getDraftUserNm() : null;
        // G4: Approver column — null pre-approval (no APPR_SEQ_NO / no chain),
        // so the shared shell leaves the cell blank exactly like legacy.
        ApproverSummary appr = s.getApprSeqNo() != null
                ? approverByApprSeqNo.get(s.getApprSeqNo())
                : null;
        String  approver      = appr != null ? appr.firstName() : null;
        Integer approverCount = appr != null ? appr.count()     : null;
        List<ScheduledTransferApprover> approvers = appr != null ? appr.approvers() : null;
        return new ScheduledTransferRow(
                s.getScdulPymnNo(),
                s.getScdulPymnTypeCd(),
                s.getCcyCd(),
                s.getTitl(),
                s.getApprSeqNo(),
                s.getTotalTranAmt(),
                s.getTotTrnsCnt(),
                s.getRegDtm(),
                s.getRegId(),
                s.getApprStts(),
                null,                       // appr_stts_key — Phase B
                null,                       // trnf_stts     — Phase B
                s.getTrnfYn(),
                s.getOutBankCd(),
                s.getWdrlActNo(),
                payDate,
                s.getOrgFileNm(),
                docNo,
                s.getWdrlCcyCd(),
                null,                       // appr_fund_tp — Payroll-only column
                approver,
                approverCount,
                approvers,
                draftUserNm);
    }

    /** First approver name + total chain length + the full ordered chain
     *  (name/status/datetime) for one approval request — feeds the Scheduled
     *  list "Approver" cell (name + "[N]" + click popup). Mirrors
     *  {@code InternalTransferService.ApproverSummary}. */
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
     * Byte-identical to {@code InternalTransferService.buildFirstApproverMap}.
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
                List<ScheduledTransferApprover> list = chain.stream()
                        .map(a -> new ScheduledTransferApprover(
                                a.getApprvrNm(), a.getApprvrStts(), a.getApprDtm()))
                        .toList();
                out.put(seq, new ApproverSummary(firstName, chain.size(), list));
            }
        }
        return out;
    }

    private static List<CurrencyTotal> computeCurrencyTotals(List<ScdulPymnSumr> rows) {
        Map<String, BigDecimal> amountByCcy = new TreeMap<>();
        Map<String, BigDecimal> countByCcy  = new TreeMap<>();
        Map<String, BigDecimal> trnsByCcy   = new TreeMap<>();
        for (ScdulPymnSumr r : rows) {
            String ccy = Objects.requireNonNullElse(r.getCcyCd(), "");
            amountByCcy.merge(ccy, Objects.requireNonNullElse(r.getTotalTranAmt(), BigDecimal.ZERO), BigDecimal::add);
            countByCcy.merge(ccy, BigDecimal.ONE, BigDecimal::add);
            trnsByCcy.merge(ccy, Objects.requireNonNullElse(r.getTotTrnsCnt(), BigDecimal.ZERO), BigDecimal::add);
        }
        return amountByCcy.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> new CurrencyTotal(
                        e.getValue(),
                        countByCcy.get(e.getKey()),
                        e.getKey(),
                        trnsByCcy.get(e.getKey())))
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
        Sort sort = ExpensePaymentSortField.parse(sortField)
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

    /** Path-traversal-safe filename leaf — mirrors Internal Transfer's
     *  {@code sanitiseOriginalFilename}. */
    private static String sanitiseOriginalFilename(String original) {
        if (original == null || original.isBlank()) return "upload.xlsx";
        String leaf = original;
        int sep = Math.max(leaf.lastIndexOf('/'), leaf.lastIndexOf('\\'));
        if (sep >= 0) leaf = leaf.substring(sep + 1);
        leaf = leaf.replaceAll("[^A-Za-z0-9._\\- (),]", "_");
        return leaf.isBlank() ? "upload.xlsx" : leaf;
    }

    /** Stream the uploaded multipart bytes through the shared
     *  {@link InternalTransferFileResolver}. The storage root is keyed
     *  by tenant + the resolver's fixed sub-directory; sharing it across
     *  payment modules keeps the download path module-agnostic. */
    private void persistUploadedFile(String bizKey, String saveFileNm, MultipartFile file) {
        Path target = fileResolver.resolve(bizKey, saveFileNm);
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("feature=expense-payment file-write bizKey={} saveFileNm={} bytes={}",
                    bizKey, saveFileNm, file.getSize());
        } catch (IOException e) {
            log.error("feature=expense-payment file-write-failed bizKey={} saveFileNm={}",
                    bizKey, saveFileNm, e);
            throw new BusinessException("WBE2070");
        }
    }
}
