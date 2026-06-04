package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxActionResponse;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprDv;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxService;
import com.kosign.wecms.domain.notification.service.NotificationService;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CreateScheduledTransferResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CurrencyTotal;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferRow;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineDetail;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineInput;
import com.kosign.wecms.domain.payment.internaltransfer.dto.UpdateReportingAccountRequest;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrId;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprDv;
import com.kosign.wecms.domain.payment.service.DocNoGenerator;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Payment &gt; Internal Transfer — core service. Mirrors the legacy
 * read paths of:
 *
 * <ul>
 *   <li>{@code pymn_0009_001_r001} — list (legacy delegates to
 *       {@code PymnHelper.pymn_0001_001_r001(scdulPymnTypeCd="03")}).</li>
 *   <li>{@code pymn_0009_003_r001} — detail (legacy IDOs
 *       {@code SCDUL_PYMN_SUMR_R012} + {@code _R013}).</li>
 *   <li>{@code pymn_0009_002_c001} — save draft.</li>
 *   <li>{@code pymn_0009_002_u002} — update reporting account.</li>
 *   <li>{@code pymn_0009_001_d001} — delete draft batch (rejected when
 *       {@code APPR_SEQ_NO} is set).</li>
 *   <li>{@code pymn_0009_002_r001} — parse Excel (Phase A stub).</li>
 * </ul>
 *
 * <h2>Identity rules</h2>
 *
 * <ul>
 *   <li>Audit {@code WSVC_ID} = {@code "internal-transfer"} (slug, not
 *       {@code pymn_0009_001}) per workspace
 *       {@code CLAUDE.md} §"Refactor identifier naming".</li>
 *   <li>{@code SCDUL_PYMN_TYPE_CD} is constant
 *       {@link ScdulPymnTypeCd#INTERNAL} ({@code "03"}) — Internal Transfer
 *       endpoints never read or write batches of other Payment-module
 *       types.</li>
 *   <li>{@code SCDUL_PYMN_NO} generation goes through
 *       {@link InternalTransferKeyGenerator} — never the legacy
 *       {@code wb_get_next_val} branch.</li>
 * </ul>
 *
 * <h2>Tenant scope</h2>
 *
 * <p>Every method reads {@link TenantContext#currentBizKey()} and
 * applies it as a predicate of every query / write — no method on this
 * service accepts a wire-supplied {@code bizKey}.
 *
 * <h2>Phase A scope</h2>
 *
 * <p>{@link #parseExcel(MultipartFile, String)} ships as a local stub
 * — it performs file-shape validation (non-empty, extension allow-list)
 * but does not parse cell contents. {@link #createDraft} accepts a
 * pre-validated row set from the frontend ({@link TransferLineInput})
 * and persists it. Real Apache POI workbook parsing lands in a
 * follow-up MR per backend doc §13.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferService {

    /** Audit-log {@code WSVC_ID} for every mutating endpoint. */
    static final String WSVC_ID = "internal-transfer";

    /** Sub-resource name written into the audit log's {@code REF_NO} column. */
    static final String REF_NO = "SCDUL_PYMN_SUMR";

    static final String AUDIT_ACTIVITY_KEY = "audit.internal_transfer.activity";
    static final String AUDIT_CREATE_KEY   = "audit.internal_transfer.create.detail";
    static final String AUDIT_UPDATE_KEY   = "audit.internal_transfer.update.detail";
    static final String AUDIT_DELETE_KEY   = "audit.internal_transfer.delete.detail";
    static final String AUDIT_REQUEST_APPROVAL_KEY =
            "audit.internal_transfer.request_approval.detail";
    static final String AUDIT_APPROVE_KEY =
            "audit.internal_transfer.approve.detail";
    static final String AUDIT_REJECT_KEY  =
            "audit.internal_transfer.reject.detail";
    static final String AUDIT_WITHDRAW_APPROVAL_KEY =
            "audit.internal_transfer.withdraw_approval.detail";
    /** Audit detail key for per-line Re-run action — mirrors legacy
     *  {@code pymn_0010_002.ts:441-472} (ETC column Re-run button that
     *  re-fires the dispatch on a transient-failure row). */
    static final String AUDIT_RERUN_KEY = "audit.internal_transfer.rerun.detail";

    /** Transient bank-side response codes that the legacy live system
     *  considers retryable. Mirror of
     *  {@code pymn_0010_002.ts:441} {@code validResponseCode}. */
    static final java.util.Set<String> RERUN_VALID_RS_CDS =
            java.util.Set.of("0001", "0002", "0003", "0004", "0005");

    static final String AUDIT_CHANGE_APPROVAL_LINE_KEY =
            "audit.internal_transfer.change_approval_line.detail";

    /** Legacy {@code APPRVR_STTS} code stamped on the master when an
     *  approver presses Approve — final outcome, batch is approved. */
    /** APPR_STTS code stamped on Request Approval — "Pending", the initial
     *  state before any approver has acted. Frontend renders this as the
     *  yellow "Pending" badge per the legacy three-state UI rule (pending
     *  → in_progress when the first approver acts → approved/rejected). */
    static final String APPR_STTS_PENDING  = "P";
    static final String APPR_STTS_APPROVED = "C";

    /** APPR_STTS code stamped on the master while approval is mid-chain —
     *  ≥1 approver acted but more remain. Drives the orange "In Progress"
     *  badge on the list. */
    static final String APPR_STTS_IN_PROGRESS = "I";

    /** Legacy {@code APPRVR_STTS} code stamped on the master when an
     *  approver presses Reject — final outcome, batch is rejected. */
    static final String APPR_STTS_REJECTED = "X";

    /** ERP Transaction History {@code TRNS_TYPE_CD} for Internal
     *  Scheduled Transfer rows. Pairs with the
     *  {@code settings.erp_transaction_history.type.16} locale entry
     *  ("Internal Scheduled Transfer"). */
    static final String ERP_TRNS_TYPE_INTERNAL_SCHEDULED_TRANSFER = "16";

    /** {@code yyyyMMddHHmmss} formatter shared with the audit writer. */
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ScdulPymnSumrRepository      sumrRepository;
    private final ScdulPymnSumrDetlRepository  detlRepository;
    private final com.kosign.wecms.domain.banking.repository.AcctMstrRepository acctMstrRepository;
    private final com.kosign.wecms.domain.banking.repository.BankInfmRepository bankInfmRepository;
    private final InternalTransferKeyGenerator keyGenerator;
    private final InternalTransferExcelParser  excelParser;
    /** Approver-chain authority — delegate approve/reject here so the
     *  approver chain advances correctly (multi-approver workflows stay
     *  "In Progress" until every approver has acted). The Internal
     *  Transfer master row then mirrors the resulting status. */
    private final ApprovalBoxService           approvalBoxService;
    /** Approval-Box fan-out — Request Approval writes the matching
     *  rows into {@code EXPT_RESOL_MSTR} + {@code EXPT_RESOL_APPRVR_INFM}
     *  so the legacy Approval Box list query sees the request. */
    private final ApprovalBoxFanoutService     approvalBoxFanoutService;
    private final InternalTransferFileResolver fileResolver;
    private final AuditLogService              auditLogService;
    private final com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter erpHistoryWriter;
    private final Clock                        clock;
    private final ExptResolMstrRepository       exptResolMstrRepository;
    private final ExptResolApprvrInfmRepository exptResolApprvrInfmRepository;
    private final NotificationService           notificationService;
    /** Approval-request attachments — written on submit, read on
     *  detail, streamed on download. See V208. */
    private final com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolFileRepository exptResolFileRepository;
    private final DocNoGenerator                docNoGenerator;
    private final BusiInfmRepository            busiInfmRepository;
    /** Shared projector that resolves the per-line bank name +
     *  chart-account breadcrumb the Request Approval dialog renders.
     *  See {@code domain/payment/common/TransferLineDetailMapper}. */
    private final com.kosign.wecms.domain.payment.common.TransferLineDetailMapper transferLineDetailMapper;
    /** Folds a user-supplied bank code (NAPAS or internal {@code BANK_CD})
     *  into the canonical NAPAS code before it lands on
     *  {@code SCDUL_PYMN_SUMR.OUT_BANK_CD}. Mirrors legacy
     *  {@code PymnHelper.uploadPaymentExcel:1315-1317}. */
    private final com.kosign.wecms.domain.payment.common.OutBankCdNormalizer outBankCdNormalizer;
    /** Dispatch-table writes for the per-line Re-run action — clears
     *  {@code RS_CD} so the bank-side daemon re-picks up the row.
     *  Mirrors the legacy {@code pymn_0010_002.ts:441-472} Re-run popup. */
    private final com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository tbPayFileDataRepository;
    /** Per-line dispatch-result lookup — shared resolver that hides the
     *  API vs File path branching. Used here to verify Re-run
     *  eligibility ({@code RS_CD} ∈ transient set + {@code PROC_DTTM} today). */
    private final com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver dispatchResultResolver;

    /* ----------------------------------------------------------------
     *  List
     * ---------------------------------------------------------------- */

    /**
     * List Scheduled Transfer batches for the current tenant in
     * the given REG_DTM window.
     *
     * @param fromDate       inclusive, {@code yyyyMMdd}
     * @param toDate         inclusive, {@code yyyyMMdd}
     * @param srchWd         optional title / approval-ref LIKE
     * @param sortField      optional whitelisted FIELD_ID
     * @param sortDirection  optional ASC / DESC
     * @param page           zero-based page index (≥ 0)
     * @param size           rows per page (1..200; clamped if outside)
     */
    @Transactional(readOnly = true)
    public ScheduledTransferListResponse list(
            String fromDate, String toDate, String srchWd, String apprType,
            String sortField, String sortDirection,
            int page, int size) {

        validateDateRange(fromDate, toDate);
        Pageable pageable = pageable(page, size, sortField, sortDirection,
                InternalTransferSortField::parse);

        String bizKey  = requireTenant();
        String fromDtm = (fromDate + "000000").substring(0, 14);
        String toDtm   = (toDate   + "235959").substring(0, 14);

        // Pre-build the lowercase LIKE pattern so JPQL never has to
        // CONCAT a possibly-null parameter. PostgreSQL otherwise
        // infers `bytea` for the unknown-typed param and fails with
        // `function lower(bytea) does not exist`. Mirrors the
        // `AcctMstrRepository#findFiltered` precedent.
        String srchWdPattern = buildLikePattern(srchWd);
        String apprTypeArg   = (apprType == null || apprType.isBlank()) ? null : apprType.trim();

        Page<ScdulPymnSumr> rows = sumrRepository.search(
                bizKey, ScdulPymnTypeCd.INTERNAL.code(),
                fromDtm, toDtm,
                srchWdPattern,
                null,
                apprTypeArg,
                pageable);

        Map<String, String> docNoByApprSeqNo = buildDocNoMap(bizKey, rows.getContent());
        List<ScheduledTransferRow> dtoRows = rows.getContent().stream()
                .map(s -> toListRow(s, docNoByApprSeqNo))
                .toList();
        List<CurrencyTotal> totals = computeCurrencyTotals(rows.getContent());

        log.info("feature=internal-transfer list bizKey={} total={} page={} size={}",
                bizKey, rows.getTotalElements(), page, pageable.getPageSize());
        return new ScheduledTransferListResponse(
                rows.getTotalElements(), page, pageable.getPageSize(),
                totals, dtoRows);
    }

    /* ----------------------------------------------------------------
     *  Detail
     * ---------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public ScheduledTransferDetailResponse detail(String id) {
        String bizKey = requireTenant();
        // Primary lookup: treat id as SCDUL_PYMN_NO (internal key).
        // Fallback: treat id as DOC_NO (VO_YYMMDD_NNN user-visible key).
        ScdulPymnSumr master = sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, id, ScdulPymnTypeCd.INTERNAL.code())
                .or(() ->
                    exptResolMstrRepository.findByBizKeyAndDocNoAndDelYn(bizKey, id, "N")
                            .filter(m -> m.getApprSeqNo() != null)
                            .flatMap(m -> sumrRepository.findByBizKeyAndApprSeqNoAndScdulPymnTypeCd(
                                    bizKey, m.getApprSeqNo(), ScdulPymnTypeCd.INTERNAL.code()))
                )
                .orElseThrow(() -> new BusinessException("WBE2060"));

        String resolvedId = master.getScdulPymnNo();
        Sort lineSort = Sort.by(Sort.Order.asc("scdulPymnDetlNo"));
        // Build the per-tenant chart-account index ONCE so each line's
        // breadcrumb walks the parent chain in-memory (no N+1 lookups).
        Map<String, com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount> chartByCode =
                transferLineDetailMapper.buildChartIndex(bizKey);
        List<TransferLineDetail> lines = detlRepository
                .findByBizKeyAndScdulPymnNo(bizKey, resolvedId, lineSort)
                .stream()
                .map(d -> transferLineDetailMapper.toLineDetail(d, chartByCode))
                .toList();

        // Resolve the withdrawal bank's human name via the same two-step
        // lookup the per-line "Deposit Bank" cell uses (BANK_INFM →
        // V86 NAPAS / CITAD fallback). Without this the Approval Details
        // popup's "Withdrawal Bank" row would render the raw code (e.g.
        // "11704020" — Woori) instead of "Woori VN", mismatching the
        // legacy live system.
        String outBankNm = transferLineDetailMapper.resolveBankNm(master.getOutBankCd());

        // Approval-request attachments registered against this batch's
        // APPR_SEQ_NO. Empty when the batch has not been submitted for
        // approval yet, or when the user submitted with no files. The
        // ApprovalDetailsDialog popup renders one chip per row +
        // streams the binary back via the download endpoint.
        java.util.List<com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow> attachments =
                master.getApprSeqNo() == null || master.getApprSeqNo().isBlank()
                        ? java.util.List.of()
                        : exptResolFileRepository
                                .findByBizKeyAndApprSeqNoOrderByFileIdAsc(bizKey, master.getApprSeqNo())
                                .stream()
                                .map(f -> new com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow(
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
                outBankNm,
                master.getWdrlActNo(),
                master.getTitl(),
                master.getTotalTranAmt(),
                master.getTotTrnsCnt(),
                master.getErpFileNum(),
                master.getRegId(),
                master.getRegDtm(),
                master.getApprSeqNo(),
                // appr_stts — sourced from SCDUL_PYMN_SUMR.APPR_STTS (V120).
                // The approval-action endpoint stamps "C"/"X" here; the
                // dialog reads this to flip the approver chip + status
                // badge to the post-action state on re-open.
                master.getApprStts(),
                null,  // appr_stts_key — Phase B (server-resolved i18n key)
                // mod_dtm — yyyyMMddHHmmss; after approve/reject the
                // dialog formats this as the approver chip's "has
                // approved on …" / "has rejected on …" date.
                master.getModDtm(),
                master.getTrnfYn(),
                master.getTrnfDtm(),
                master.getCcyCd(),
                master.getWdrlCcyCd(),
                master.getOrgFileNm(),
                master.getFileSize(),
                lines,
                attachments);
    }

    /* ----------------------------------------------------------------
     *  Create (save draft)
     * ---------------------------------------------------------------- */

    /**
     * Save a draft batch. The original Excel file is persisted to the
     * configured storage root under
     * {@code <storage-root>/<biz_key>/internal-transfer/<save_file_nm>}
     * — mirrors the ERP Transaction History storage convention so the
     * download endpoint can re-stream the original bytes.
     *
     * <p>{@code save_file_nm} is composed as
     * {@code <SCDUL_PYMN_NO>_<sanitised-original-name>} so collisions
     * across tenants / days are impossible and the file's owner row
     * is recoverable from the filename alone.
     *
     * <p>Validation chain:
     * <ol>
     *   <li>Bean validation on {@link CreateScheduledTransferRequest}
     *       (controller layer).</li>
     *   <li>{@link #requireTenant()} — guarantees a real authenticated
     *       caller.</li>
     *   <li>Per-line {@link TransferLineInput#fincMdaNo()} present
     *       (Bean validation).</li>
     * </ol>
     *
     * <p>File-write failures surface as {@code WBE2016} so the caller
     * can distinguish a storage-layer problem from a row-validation
     * error.
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
        master.setScdulPymnTypeCd(ScdulPymnTypeCd.INTERNAL.code());
        // Legacy parity (PymnHelper.uploadPaymentExcel:1315-1317): the user
        // may type either the public NAPAS code (970xxx) or the internal
        // BANK_CD (1170xxxx). Either way, OUT_BANK_CD must be persisted as
        // the canonical NAPAS code so downstream cut-off / dispatch keys
        // resolve correctly.
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
        // `ErpTrnsHisHelper.erpTrnsHisUploader`). TRNS_TYPE_CD = "16"
        // (Internal Scheduled Transfer) lands the row in the "ERP" tab.
        // The writer is REQUIRES_NEW + swallows its own errors, so an
        // ERP audit-write failure cannot roll back the batch create.
        if (master.getSaveFileNm() != null && !master.getSaveFileNm().isBlank()) {
            try {
                java.nio.file.Path source = fileResolver.resolve(bizKey, master.getSaveFileNm());
                erpHistoryWriter.recordUploadedFile(
                        ERP_TRNS_TYPE_INTERNAL_SCHEDULED_TRANSFER,
                        source,
                        master.getOrgFileNm());
            } catch (RuntimeException ex) {
                log.warn(
                        "feature=internal-transfer ERP history skip "
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

        log.info("feature=internal-transfer create bizKey={} scdulPymnNo={} lineCount={} totalAmount={}",
                bizKey, scdulPymnNo, req.lines().size(), totalAmount);
        return new CreateScheduledTransferResponse(scdulPymnNo);
    }

    /* ----------------------------------------------------------------
     *  Update reporting account (legacy pymn_0009_002_u002)
     * ---------------------------------------------------------------- */

    @Transactional
    public void updateReportingAccount(String id, String lineId,
                                        UpdateReportingAccountRequest req) {
        String bizKey = requireTenant();
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        // Master existence + tenant scope.
        ScdulPymnSumr master = loadMaster(bizKey, id);
        // Reject when the batch is already locked by approval (mirrors
        // intent of pymn_0009_001_d001 — though for line-edit JEX does
        // not explicitly forbid it; we mirror conservatively).
        if (Boolean.TRUE.equals(TrnfYn.TRANSFERRED.code().equals(master.getTrnfYn()))) {
            throw new BusinessException("WBE2064");
        }

        ScdulPymnSumrDetl detl = detlRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(bizKey, id, lineId)
                .orElseThrow(() -> new BusinessException("WBE2061"));

        // NOTE: CHART_ACCT_INFM existence check is deferred — that
        // entity is not yet migrated (see database/internal-transfer.md
        // §"Read-only — already migrated"). Bean validation enforces
        // shape; the missing-row guard lands when the entity lands.
        detl.setChartAcctSeqNo(req.chartAcctSeqNo());
        detl.setChartAcctCd(req.chartAcctCd());
        detl.setModId(userId);
        detl.setModDtm(now);
        // JPA dirty checking + write-behind handles the UPDATE.

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .refNo2(lineId)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_UPDATE_KEY)
                .build());

        log.info("feature=internal-transfer update-reporting bizKey={} scdulPymnNo={} lineId={} chartAcctCd={}",
                bizKey, id, lineId, req.chartAcctCd());
    }

    /* ----------------------------------------------------------------
     *  Re-run one detail line (legacy pymn_0010_002.ts:441-472)
     *
     *  Fires when the user clicks the ETC-column "Re-run" button on a
     *  dispatched line whose bank-side response is a transient failure
     *  (RS_CD ∈ {"0001"…"0005"}) AND whose PROC_DTTM is today. Resets
     *  PROC_STAT='9' + clears the result fields on the matching
     *  TB_PAY_FILE_DATA row so the bank-side daemon re-picks it up
     *  on its next sweep.
     *
     *  Refactor R&D scope: the actual bank daemon doesn't run locally,
     *  so a successful Re-run leaves the dispatch row in "pending"
     *  state until manual intervention. Production parity is in the
     *  endpoint behaviour, not in the downstream effect.
     * ---------------------------------------------------------------- */

    @Transactional
    public void rerun(String scdulPymnNo, String scdulPymnDetlNo) {
        String bizKey = requireTenant();

        // 1. Master + detail existence + tenant scope.
        ScdulPymnSumr master = loadMaster(bizKey, scdulPymnNo);
        ScdulPymnSumrDetl detl = detlRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(bizKey, scdulPymnNo, scdulPymnDetlNo)
                .orElseThrow(() -> new BusinessException("WBE2061"));

        // 2. Both shadow keys + master PAY_FILE_SEQ_NO must be stamped
        //    — i.e. the line has been through dispatch.
        String tranDt    = detl.getPayTranDt();
        String tranSeq   = detl.getPayTranSeq();
        String fileSeqNo = master.getPayFileSeqNo();
        if (tranDt == null || tranDt.isBlank()
                || tranSeq == null || tranSeq.isBlank()
                || fileSeqNo == null || fileSeqNo.isBlank()) {
            // WBE2080 — line has no dispatch row yet.
            throw new BusinessException("WBE2122");
        }

        // 3. Eligibility via the shared resolver — surfaces the same
        //    (PROC_STAT / RS_CD / PROC_DTTM / RSLT_DTTM) tuple the
        //    Result-detail read endpoint serves. Avoids duplicating the
        //    API/File-path branching here. Mirrors legacy
        //    pymn_0010_002.ts:441-449 (validResponseCode + executedToday).
        java.util.Map<String,
                com.kosign.wecms.domain.payment.dispatch.service.DispatchResult> results =
                dispatchResultResolver.resolve(master, java.util.List.of(detl));
        com.kosign.wecms.domain.payment.dispatch.service.DispatchResult result =
                results.get(scdulPymnDetlNo);
        if (result == null || result.rsCd() == null
                || !RERUN_VALID_RS_CDS.contains(result.rsCd())) {
            // WBE2123 — RS_CD not in transient set (or no result yet).
            throw new BusinessException("WBE2123");
        }
        String procDttm = result.procDttm();
        if (procDttm == null || procDttm.length() < 8) {
            throw new BusinessException("WBE2124");
        }
        String todayYmd = OffsetDateTime.now(clock)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!todayYmd.equals(procDttm.substring(0, 8))) {
            // WBE2124 — line was not executed today; can't be re-run.
            throw new BusinessException("WBE2124");
        }

        // 4. Clear RS_CD on TB_PAY_FILE_DATA so the bank-side daemon
        //    re-picks up the row on its next sweep. Other result fields
        //    (PROC_STAT / PROC_DTTM / RSLT_DTTM / RS_MSG) live on
        //    TB_PAY_FILE_MST or aren't mapped on the per-line entity —
        //    R&D scope limits the reset to the per-line RS_CD column
        //    that the entity exposes.
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

        // 5. Audit log.
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(scdulPymnNo)
                .refNo2(scdulPymnDetlNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_RERUN_KEY)
                .build());

        log.info("feature=internal-transfer rerun bizKey={} scdulPymnNo={} lineId={} tranDt={} seq={}",
                bizKey, scdulPymnNo, scdulPymnDetlNo, tranDt, seq);
    }

    /* ----------------------------------------------------------------
     *  Delete draft batch (legacy pymn_0009_001_d001)
     * ---------------------------------------------------------------- */

    @Transactional
    public void delete(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        // Reject when the batch is past the draft state.
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
        // TRNS_TYPE_CD = "16" → Internal Scheduled Transfer (per the
        // `settings.erp_transaction_history.type.16` locale entry);
        // writer stamps ERP_TYPE='D' so the row lands in the "Deleted"
        // tab. No-op when the batch has no uploaded file.
        if (saveFileNm != null && !saveFileNm.isBlank()) {
            try {
                java.nio.file.Path source = fileResolver.resolve(bizKey, saveFileNm);
                erpHistoryWriter.recordDeletedFile(
                        ERP_TRNS_TYPE_INTERNAL_SCHEDULED_TRANSFER,
                        source,
                        orgFileNm);
            } catch (RuntimeException ex) {
                // Defence in depth — fileResolver throws on a
                // bizKey/saveFileNm mismatch (path traversal guard).
                // The writer's own try/catch covers everything past
                // that, so the only thing reaching here is a malformed
                // saveFileNm that fails the path-composition step.
                log.warn(
                        "feature=internal-transfer ERP history skip "
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

        log.info("feature=internal-transfer delete bizKey={} scdulPymnNo={}", bizKey, id);
    }

    /* ----------------------------------------------------------------
     *  Request Approval (legacy comm_0194_001 → comm_0192_001_c005)
     * ----------------------------------------------------------------
     *
     * <p>Phase A scope: stamps a synthesised {@code APPR_SEQ_NO} on the
     * batch master and writes an audit row. The full per-leg approval
     * snapshot (legacy {@code BLTB_POST_INFM} / {@code EXPT_RESOL_MSTR}
     * / {@code EXPT_RESOL_APPRVR_INFM} fan-out) lands when those
     * tables are ported in a follow-up. The stamped {@code APPR_SEQ_NO}
     * is sufficient to:
     *   <ul>
     *     <li>flip the row out of the "draftable" state (so the Delete
     *         button hides and a second Request Approval click returns
     *         {@code WBE2062});</li>
     *     <li>echo back to the frontend so the row can show the
     *         "in_progress" / "approved" status badge once the live
     *         approval-flow tables fan in.</li>
     *   </ul>
     *
     * <p>Concurrency: the MAX+1 generator is race-prone (same precedent
     * as Approval Line / Chart of Accounts). One retry on
     * {@code DataIntegrityViolationException} catches the rare
     * collision; a third writer in the same instant gets a 500.
     */
    /**
     * Preview the next {@code VO_YYMMDD_NNN} document number for the
     * current tenant. Used by the Request Approval popup to populate
     * the read-only "Document No." field before submit so the dialog
     * shows the same friendly id format as Card Transactions — the
     * authoritative value is stamped server-side by
     * {@link #requestApproval} at submit time.
     *
     * <p>Read-only / non-reserving: a second caller (or this caller's
     * own later submit) may receive a higher number if other Request
     * Approvals land in between. Frontend treats the preview as
     * informational and renders whatever value the server returns.
     */
    @Transactional(readOnly = true)
    public com.kosign.wecms.domain.payment.internaltransfer.dto.NextDocNoResponse previewNextDocNo() {
        String bizKey = requireTenant();
        String docNo = docNoGenerator.nextDocNo(bizKey);
        return new com.kosign.wecms.domain.payment.internaltransfer.dto.NextDocNoResponse(docNo);
    }

    @Transactional
    public com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalResponse requestApproval(
            String id,
            com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest req,
            java.util.List<org.springframework.web.multipart.MultipartFile> attachments) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        // Idempotency guard — re-using the same code as Delete because
        // the legacy contract is the same: an APPR_SEQ_NO row is not
        // re-submittable. The frontend disables the Request Approval
        // button after the first successful submit; this guard catches
        // the stale-tab case.
        if (master.getApprSeqNo() != null && !master.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2062");
        }

        // Defence in depth — bean-validation on the record covers the
        // shape; this re-asserts the wire fields we actually use.
        if (req == null) {
            throw new BusinessException("WBE_VALIDATION", "request body is required");
        }
        if (req.approvers() == null || req.approvers().isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "approvers is required");
        }

        // Synthesise the APPR_SEQ_NO via per-tenant MAX+1 (Java-side,
        // no wb_get_next_val). The wire field is a VARCHAR so we stamp
        // the decimal string. One retry handles the rare race.
        String apprSeqNo = allocateApprSeqNo(bizKey);
        String docNo;
        try {
            docNo = stampApproval(master, apprSeqNo, req);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            log.info("feature=internal-transfer apprSeqNo race retrying once bizKey={}", bizKey);
            apprSeqNo = allocateApprSeqNo(bizKey);
            docNo = stampApproval(master, apprSeqNo, req);
        }

        // Approval-request attachments — stream each file to disk under
        // `<storage-root>/<biz_key>/approval-attachments/<appr_seq_no>/`
        // and insert one EXPT_RESOL_FILE row per file. Runs INSIDE the
        // same @Transactional so a partial failure (disk write OK + DB
        // insert fail, or vice versa) rolls back the metadata; the disk
        // copy of any partially-written files survives but is harmless
        // because the matching DB row never committed (the user can
        // re-submit). Empty / null list is the no-attachment path —
        // legacy parity with the JSON-only contract.
        persistApprovalAttachments(bizKey, apprSeqNo, attachments);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO)
                .refNo1(id)
                .refNo2(apprSeqNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_REQUEST_APPROVAL_KEY)
                .build());

        log.info(
                "feature=internal-transfer request-approval bizKey={} scdulPymnNo={} apprSeqNo={} apprLineNo={} approvers={} attachments={}",
                bizKey, id, apprSeqNo, req.apprLineNo(), req.approvers().size(),
                attachments == null ? 0 : attachments.size());

        return new com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalResponse(apprSeqNo, docNo);
    }

    /**
     * Stream every {@code MultipartFile} in {@code attachments} to disk
     * and insert one {@code EXPT_RESOL_FILE} row per file. Files are
     * stored under
     * {@code <storage-root>/<biz_key>/approval-attachments/<apprSeqNo>/}
     * with a per-row composite filename so duplicates in the same
     * upload never collide. Empty / null list is a no-op.
     */
    private void persistApprovalAttachments(
            String bizKey,
            String apprSeqNo,
            java.util.List<org.springframework.web.multipart.MultipartFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        String userId = TenantContext.currentUserId();
        String userNm = TenantContext.currentUserNm();
        String nowDtm = ZonedDateTime.ofInstant(clock.instant(), VIETNAM_ZONE).format(DTM14);

        java.nio.file.Path apprDir = fileResolver.approvalAttachmentDirectory(bizKey, apprSeqNo);
        try {
            java.nio.file.Files.createDirectories(apprDir);
        } catch (java.io.IOException ex) {
            log.error("feature=internal-transfer request-approval attachment-mkdir-failed bizKey={} apprSeqNo={} err={}",
                    bizKey, apprSeqNo, ex.getMessage());
            throw new BusinessException("WBE2070");
        }

        int seq = 1;
        for (org.springframework.web.multipart.MultipartFile file : attachments) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String fileId    = Integer.toString(seq++);
            String orgFileNm = sanitiseOriginalFilename(file.getOriginalFilename());
            // Compose a collision-free disk name — appr seq + per-file
            // sequence + sanitised original filename. The original is
            // preserved in `ORG_FILE_NM` for display; the on-disk name
            // is internal.
            String saveFileNm = apprSeqNo + "_" + fileId + "_" + orgFileNm;
            java.nio.file.Path target = fileResolver.resolveApprovalAttachment(
                    bizKey, apprSeqNo, saveFileNm);
            try {
                // Use the Path overload to avoid the
                // @NonNull-warned `target.toFile()` indirection.
                file.transferTo(target);
            } catch (java.io.IOException ex) {
                log.error("feature=internal-transfer request-approval attachment-write-failed bizKey={} apprSeqNo={} fileId={} err={}",
                        bizKey, apprSeqNo, fileId, ex.getMessage());
                throw new BusinessException("WBE2070");
            }

            com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFile row =
                    new com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFile();
            row.setBizKey(bizKey);
            row.setApprSeqNo(apprSeqNo);
            row.setFileId(fileId);
            row.setOrgFileNm(orgFileNm);
            row.setSaveFileNm(saveFileNm);
            row.setFileSize(Long.toString(file.getSize()));
            row.setRegId(userId);
            row.setRegNm(userNm == null ? userId : userNm);
            row.setRegDtm(nowDtm);
            exptResolFileRepository.save(row);
        }
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

    private String stampApproval(
            ScdulPymnSumr master,
            String apprSeqNo,
            com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest req) {
        String bizKey  = master.getBizKey();
        String userId  = TenantContext.currentUserId();
        String userNm  = TenantContext.currentUserNm();
        String nowDtm  = ZonedDateTime.ofInstant(clock.instant(), VIETNAM_ZONE).format(DTM14);
        String draftDate = nowDtm.substring(0, 8);
        String draftTime = nowDtm.substring(8, 14);

        // 1. Stamp SCDUL_PYMN_SUMR
        master.setApprSeqNo(apprSeqNo);
        master.setApprStts(APPR_STTS_PENDING);
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
        mstr.setApprDv(ApprDv.INTERNAL_TRANSFER.code());
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
        List<com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest.ApproverInput> approvers = req.approvers();
        for (int i = 0; i < approvers.size(); i++) {
            com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest.ApproverInput ap = approvers.get(i);
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
                ApprDv.INTERNAL_TRANSFER.slug(), apprSeqNo, docNo, req.postTitle(),
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
     * Approve or reject the active approval request on one batch —
     * the action behind the Approve / Reject buttons in
     * {@code <ApprovalDetailsDialog>} (Figma {@code comm_0195_001}
     * frames 03 / 07 / 12 / 13 → footer lines 916-919).
     *
     * <p>Phase A scope: master-level stamp only. The endpoint:
     * <ul>
     *   <li>requires the batch to carry an {@code APPR_SEQ_NO}
     *       (i.e. the user previously hit Request Approval) — else
     *       {@code WBE2094}.</li>
     *   <li>requires the batch to be still actionable — the current
     *       {@code APPR_STTS} must be null / {@code "P"} / {@code "I"};
     *       any final state ({@code "C"} / {@code "X"}) returns
     *       {@code WBE2095}.</li>
     *   <li>stamps {@code APPR_STTS = "C"} on Approve, {@code "X"}
     *       on Reject.</li>
     *   <li>writes one audit row keyed by the action (Approve /
     *       Reject), carrying the batch id + the stamped state.</li>
     * </ul>
     *
     * <p>The per-approver chain (legacy
     * {@code EXPT_RESOL_APPRVR_INFM}) lands when that table is
     * ported. Until then the master-level stamp is the single source
     * of truth the frontend list mapper consumes via
     * {@code apprStts === "C" || "A"} → approved, and {@code "X"} →
     * rejected.
     *
     * @param id  batch {@code SCDUL_PYMN_NO} from the path
     * @param req {@code action} = {@code "APPROVE"} or {@code "REJECT"}
     *            (validated by bean-validation on the record), optional
     *            {@code comment} accepted-and-audited but not persisted
     *            on the master.
     */
    @Transactional
    public void actOnApproval(
            String id,
            com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalActionRequest req) {
        if (req == null) {
            throw new BusinessException("WBE_VALIDATION", "request body is required");
        }
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        // Guard 1: the batch must be in approval (apprSeqNo set).
        // Same vocabulary as the Delete guard's "cannot act on a draft"
        // but the inverse condition — Delete rejects rows WITH a seq,
        // approve/reject rejects rows WITHOUT one.
        if (master.getApprSeqNo() == null || master.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2094");
        }

        // Guard 2: the batch must still be actionable. Once stamped
        // approved / rejected (C / X), the action is final — re-acting
        // would silently overwrite history.
        String current = master.getApprStts();
        if (APPR_STTS_APPROVED.equals(current) || APPR_STTS_REJECTED.equals(current)) {
            throw new BusinessException("WBE2095");
        }

        // Delegate the approver-chain logic to the approval-box service —
        // it owns the APPRVR_STTS row updates, the next-approver promotion
        // (W → P), and the master APPR_TYPE_CD transition. Approving one
        // step in a multi-approver chain leaves the chain "In Progress"
        // until every approver has acted; rejecting cancels the chain
        // immediately.
        ApprovalBoxActionResponse result = req.isApprove()
                ? approvalBoxService.approve(master.getApprSeqNo())
                : approvalBoxService.reject(master.getApprSeqNo());

        // Mirror the resulting state onto SCDUL_PYMN_SUMR.APPR_STTS so the
        // Internal Transfer list page (which reads from this column, not
        // the EXPT_RESOL_* tables) reflects the chain progress. Approval-
        // box returns "R" for rejected; the SCDUL_PYMN_SUMR convention is
        // "X" — remap.
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
                "feature=internal-transfer approval-action bizKey={} scdulPymnNo={} apprSeqNo={} action={} apprStts={}",
                bizKey, id, master.getApprSeqNo(), req.action(), next);
    }

    /**
     * Withdraw an in-flight approval request on a batch. Deletes the
     * {@code EXPT_RESOL_MSTR} + {@code EXPT_RESOL_APPRVR_INFM} rows via
     * the shared {@link ApprovalBoxFanoutService#withdrawApproval} and
     * clears the batch's back-stamp ({@code APPR_SEQ_NO},
     * {@code ERP_FILE_NUM}, {@code APPR_STTS}) so the row returns to
     * the draftable state. Same Pending / In Progress guard as the
     * shared method — closed approvals stay immutable.
     *
     * <p>Mirrors the legacy {@code comm_0195_001} kebab → Delete path.
     */
    @Transactional
    public void withdrawApproval(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        String apprSeqNo = master.getApprSeqNo();
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            // Same code Approve/Reject uses — "this batch has no live
            // approval to act on" — so the frontend can surface a
            // single error envelope for every "operates on a request"
            // call.
            throw new BusinessException("WBE2094");
        }

        // Shared cleanup — deletes master + approver rows, enforces the
        // Pending / In Progress guard. Throws WBE_VALIDATION if the row
        // is already closed.
        approvalBoxFanoutService.withdrawApproval(apprSeqNo);

        // Clear the batch's back-stamp so it returns to draftable state
        // (the Request Approval popup re-opens, Delete row icon shows
        // again, list status badge flips back to Draft).
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

        log.info("feature=internal-transfer withdraw-approval bizKey={} scdulPymnNo={} apprSeqNo={}",
                bizKey, id, apprSeqNo);
    }

    /**
     * Replace the approver chain on an in-flight approval request with
     * the approvers attached to {@code apprLineNo}. Resets the master
     * back to Pending so the new first approver becomes active.
     * Delegates the per-row work to
     * {@link ApprovalBoxFanoutService#changeApprovalLine}; this method
     * just resolves the {@code APPR_SEQ_NO} from the batch and writes
     * the per-module audit row + mirrors the Pending state onto
     * {@code SCDUL_PYMN_SUMR.APPR_STTS}.
     *
     * <p>Mirrors the legacy {@code comm_0195_001} edit-mode flow used
     * by Card Transactions' Approval Details popup.
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

        // Master returns to "Pending" — the new first approver is the
        // active one. Mirror the state onto SCDUL_PYMN_SUMR so the
        // list page's status badge re-renders as Pending without a
        // round-trip through the Approval Box join.
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

        log.info("feature=internal-transfer change-approval-line bizKey={} scdulPymnNo={} apprSeqNo={} newApprLineNo={}",
                bizKey, id, apprSeqNo, apprLineNo);
    }

    /** Map the approval-box service's `apprStts` ("P"/"I"/"C"/"R") onto
     *  the SCDUL_PYMN_SUMR column convention ("P"/"I"/"C"/"X"). The only
     *  divergence is Rejected: approval-box returns "R", SCDUL_PYMN_SUMR
     *  stores "X". */
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
     *  Excel parse (Phase A stub)
     * ---------------------------------------------------------------- */

    /* ----------------------------------------------------------------
     *  Withdrawal-account lookup (legacy comm_1001_001)
     * ---------------------------------------------------------------- */

    /**
     * List firm-banking-capable withdrawal accounts the current tenant
     * may pick as the source of an Internal Transfer batch. Drives the
     * Step-3 picker in the legacy {@code pymn_0009_002} popup (Figma
     * 1:9972 — "Withdrawal Bank/Account No.").
     *
     * <p>Mirrors legacy {@code comm_1001_001_act.jsp:23-65} — see the
     * filter list on {@link com.kosign.wecms.domain.banking.repository.AcctMstrRepository#findWithdrawalAccounts}.
     * The legacy per-user {@code ACCT_AUTH_INFO} authority filter is
     * deferred until that table is migrated.
     *
     * @param ccyCd optional currency filter (matches legacy
     *              {@code CCY_CD} input; {@code null} skips).
     */
    @Transactional(readOnly = true)
    public java.util.List<com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow>
            listWithdrawalAccounts(String ccyCd) {
        String bizKey = requireTenant();
        String filter = (ccyCd == null || ccyCd.isBlank()) ? null : ccyCd.trim().toUpperCase();
        return acctMstrRepository.findWithdrawalAccounts(bizKey, filter).stream()
                .map(this::toWithdrawalAccountRow)
                .toList();
    }

    private com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow
            toWithdrawalAccountRow(
                    com.kosign.wecms.domain.banking.entity.AcctMstr a) {
        String bankNm = bankInfmRepository.findById(a.getBankCd())
                .map(com.kosign.wecms.domain.banking.entity.BankInfm::getRprsBankNm)
                .orElse(a.getBankCd());
        return new com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow(
                a.getBankCd(),
                bankNm,
                a.getAcctNo(),
                a.getAcctTypeCd(),
                a.getCcyCd(),
                a.getAcctNickNm());
    }

    /**
     * Parse the uploaded Internal Transfer template into a per-row
     * preview. Delegates the workbook walk + per-cell validation to
     * {@link InternalTransferExcelParser}; this method validates the
     * file-shape envelope (tenant context, non-empty, .xls/.xlsx
     * extension, withdrawal bank code present) before handing off.
     *
     * <p>Mirrors legacy
     * {@code PymnHelper.importDataFromExcelForInternalTransfer}.
     */
    public ParseExcelResponse parseExcel(MultipartFile file, String bankCd) {
        requireTenant();  // also guards against unauthenticated calls.
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

    /* ----------------------------------------------------------------
     *  Internal helpers
     * ---------------------------------------------------------------- */

    /** Load the master row scoped to tenant + type; map missing/wrong-tenant
     *  to the single {@code WBE2060} envelope (never leak the existence
     *  of another tenant's row). */
    private ScdulPymnSumr loadMaster(String bizKey, String scdulPymnNo) {
        return sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, scdulPymnNo, ScdulPymnTypeCd.INTERNAL.code())
                .orElseThrow(() -> new BusinessException("WBE2060"));
    }

    /* ----------------------------------------------------------------
     *  File storage — write on createDraft, read on download
     * ---------------------------------------------------------------- */

    /**
     * Strip the original filename of any directory components and
     * disallowed characters so the value is safe to use as a
     * filesystem-path leaf. Mirrors the legacy {@code FileUtils.safeName}
     * behaviour (path traversal guard via {@link InternalTransferFileResolver}
     * is the second line of defence, but cleaning the input here keeps
     * the stored filename readable).
     *
     * <p>Null / blank inputs default to {@code "upload.xlsx"} so the
     * {@code <SCDUL_PYMN_NO>_<name>} composition never produces a bare
     * trailing underscore.
     */
    private static String sanitiseOriginalFilename(String original) {
        if (original == null || original.isBlank()) return "upload.xlsx";
        String leaf = original;
        int sep = Math.max(leaf.lastIndexOf('/'), leaf.lastIndexOf('\\'));
        if (sep >= 0) leaf = leaf.substring(sep + 1);
        // Keep alphanumerics, dot, dash, underscore, space, parens, comma.
        // Everything else collapses to underscore — preserves readable
        // names ("WeCMS_Internal_Transfer_upload_EN.xlsx") while killing
        // shell metacharacters.
        leaf = leaf.replaceAll("[^A-Za-z0-9._\\- (),]", "_");
        return leaf.isBlank() ? "upload.xlsx" : leaf;
    }

    /**
     * Stream the uploaded multipart bytes to
     * {@code <storage-root>/<bizKey>/internal-transfer/<saveFileNm>}.
     * Creates the per-tenant subtree on first write. Translates IO
     * failures to {@code WBE2016} so callers see a clean envelope
     * instead of an HTTP 500.
     */
    private void persistUploadedFile(String bizKey, String saveFileNm, MultipartFile file) {
        Path target = fileResolver.resolve(bizKey, saveFileNm);
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("feature=internal-transfer file-write bizKey={} saveFileNm={} bytes={}",
                    bizKey, saveFileNm, file.getSize());
        } catch (IOException e) {
            log.error("feature=internal-transfer file-write-failed bizKey={} saveFileNm={}",
                    bizKey, saveFileNm, e);
            throw new BusinessException("WBE2070");
        }
    }

    /**
     * Resolve the physical file behind a batch's original Excel upload.
     * Mirrors the ERP Transaction History download contract:
     * <ul>
     *   <li>Row not found <em>or</em> not in tenant → {@code WBE2060}
     *       (single envelope reused from {@link #loadMaster}).</li>
     *   <li>{@code save_file_nm = null} (legacy DEV-seeded row with no
     *       refactor-uploaded file) or file missing / unreadable on
     *       storage root → {@code WBE2015}.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public DownloadResource resolveDownload(String scdulPymnNo) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, scdulPymnNo);

        String saveFileNm = master.getSaveFileNm();
        if (saveFileNm == null || saveFileNm.isBlank()) {
            // Row exists but has no file — could be a legacy DEV-imported
            // row, or a draft saved before the storage-write rollout.
            throw new BusinessException("WBE2069");
        }

        Path path = fileResolver.resolve(bizKey, saveFileNm);
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

    /**
     * Controller-only carrier for the resolved file. Service exposes
     * the trio (physical {@link Path}, display filename, saved
     * filename) so the controller can compose RFC 5987
     * {@code Content-Disposition} without re-loading the row.
     */
    public record DownloadResource(
            Path path, String orgFileNm, String saveFileNm, long fileSize) { }

    /**
     * Resolve the disk path + display filename for one approval-request
     * attachment. Mirrors {@link #resolveDownload}'s contract — same
     * tenant scoping, same {@code WBE2069} error envelope when the row
     * exists but the on-disk file is missing.
     */
    @Transactional(readOnly = true)
    public DownloadResource resolveApprovalAttachmentDownload(
            String scdulPymnNo, String apprSeqNo, String fileId) {
        String bizKey = requireTenant();
        // Tenant-scope the master lookup first — defends against a
        // caller passing an apprSeqNo from another tenant. The master
        // also gives us the cross-check that this APPR_SEQ_NO actually
        // belongs to this batch.
        ScdulPymnSumr master = loadMaster(bizKey, scdulPymnNo);
        if (master.getApprSeqNo() == null
                || !master.getApprSeqNo().equals(apprSeqNo)) {
            throw new BusinessException("WBE2069");
        }
        com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFile row =
                exptResolFileRepository
                        .findByBizKeyAndApprSeqNoAndFileId(bizKey, apprSeqNo, fileId)
                        .orElseThrow(() -> new BusinessException("WBE2069"));
        Path path = fileResolver.resolveApprovalAttachment(
                bizKey, apprSeqNo, row.getSaveFileNm());
        if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("WBE2069");
        }
        long fileSize = row.getFileSize() == null
                ? path.toFile().length()
                : Long.parseLong(row.getFileSize());
        return new DownloadResource(path, row.getOrgFileNm(), row.getSaveFileNm(), fileSize);
    }

    private String requireTenant() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) {
            // Should never happen — every endpoint is authenticated.
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

    private static <T extends com.kosign.wecms.common.web.SortableField> Pageable pageable(
            int page, int size, String sortField, String sortDirection,
            java.util.function.Function<String, java.util.Optional<T>> parse) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 10 : Math.min(size, 200);
        Sort sort = parse.apply(sortField)
                .map(sf -> {
                    SortDirection dir = SortDirection.parse(sortDirection);
                    Sort.Direction d = dir == SortDirection.DESC
                            ? Sort.Direction.DESC : Sort.Direction.ASC;
                    return Sort.by(d, sf.entityProperty()).and(Sort.by(Sort.Order.desc("regDtm")));
                })
                .orElseGet(() -> Sort.by(Sort.Order.desc("regDtm"), Sort.Order.desc("scdulPymnNo")));
        return PageRequest.of(safePage, safeSize, sort);
    }

    private Map<String, String> buildDocNoMap(String bizKey, List<ScdulPymnSumr> rows) {
        List<String> apprSeqNos = rows.stream()
                .map(ScdulPymnSumr::getApprSeqNo)
                .filter(seq -> seq != null && !seq.isBlank())
                .distinct()
                .toList();
        if (apprSeqNos.isEmpty()) return Map.of();
        return exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(bizKey, apprSeqNos)
                .stream()
                .filter(m -> m.getDocNo() != null)
                .collect(java.util.stream.Collectors.toMap(
                        ExptResolMstr::getApprSeqNo,
                        ExptResolMstr::getDocNo,
                        (a, b) -> a));
    }

    private ScheduledTransferRow toListRow(ScdulPymnSumr s, Map<String, String> docNoByApprSeqNo) {
        String docNo = s.getApprSeqNo() != null
                ? docNoByApprSeqNo.getOrDefault(s.getApprSeqNo(), s.getErpFileNum())
                : s.getErpFileNum();
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
                // appr_stts — sourced from SCDUL_PYMN_SUMR.APPR_STTS (V120).
                // Phase A stamps "C" (approved) / "X" (rejected) directly
                // on the master via the approval-action endpoint; the full
                // per-approver chain that produces "P" / "I" intermediate
                // states arrives with the EXPT_RESOL_APPRVR_INFM port.
                s.getApprStts(),
                null,  // appr_stts_key — Phase B (server-resolved i18n key)
                null,  // trnf_stts — Phase B
                s.getTrnfYn(),
                s.getOutBankCd(),
                s.getWdrlActNo(),
                s.getPayTranDt(),
                s.getOrgFileNm(),
                docNo,
                s.getWdrlCcyCd());
    }


    /** Compute per-currency totals on the **page slice** (matches the
     *  visible rows the frontend will paint in the totals footer). */
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

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String emptyToDefault(String s, String def) {
        String v = emptyToNull(s);
        return v == null ? def : v;
    }

    /**
     * Build a lowercase {@code "%foo%"} LIKE pattern from a raw search
     * keyword. Returns {@code null} when the input is blank so the JPQL
     * {@code IS NULL} short-circuit branches cleanly. Wildcards
     * ({@code %} / {@code _}) in the user input are NOT escaped — the
     * Internal Transfer search is a free-form contains-match (legacy
     * parity with {@code pymn_0009_001_r001}'s search behaviour),
     * not a strict LIKE-escape boundary.
     */
    private static String buildLikePattern(String raw) {
        String trimmed = emptyToNull(raw);
        if (trimmed == null) return null;
        return "%" + trimmed.toLowerCase() + "%";
    }
}
