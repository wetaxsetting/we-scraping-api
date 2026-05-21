package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
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
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferRow;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineDetail;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineInput;
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
    static final String AUDIT_DELETE_KEY           = "audit.payroll_payment.delete.detail";
    static final String AUDIT_DISPATCH_KEY         = "audit.payroll_payment.dispatch.detail";
    static final String AUDIT_REQUEST_APPROVAL_KEY = "audit.payroll_payment.request_approval.detail";
    static final String AUDIT_APPROVE_KEY          = "audit.payroll_payment.approve.detail";
    static final String AUDIT_REJECT_KEY           = "audit.payroll_payment.reject.detail";
    static final String AUDIT_CREATE_KEY           = "audit.payroll_payment.create.detail";

    /** ERP Transaction History {@code TRNS_TYPE_CD} for Payroll Scheduled
     *  Payment rows. Mirrors legacy
     *  {@code wabooks.enums.ErpTypeCd.PAYROLL_SCHEDULED_PAYMENT} = "12"
     *  (NOT the same code used by Vendor/Expense/Internal). Pairs with
     *  the {@code settings.erp_transaction_history.type.12} locale
     *  entry ("Payroll Scheduled Payment"). */
    static final String ERP_TRNS_TYPE_PAYROLL_SCHEDULED_PAYMENT = "12";

    /** Legacy {@code APPRVR_STTS} code stamped on the master when an
     *  approver presses Approve. */
    static final String APPR_STTS_APPROVED = "C";

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
    private final PayrollPaymentExcelParser     excelParser;
    private final InternalTransferKeyGenerator  keyGenerator;
    private final InternalTransferFileResolver  fileResolver;
    private final ErpTrnsHistoryWriter          erpHistoryWriter;
    private final AuditLogService               auditLogService;
    private final TransferDispatchExecutor      dispatchExecutor;
    private final Clock                         clock;
    private final ExptResolMstrRepository       exptResolMstrRepository;
    private final ExptResolApprvrInfmRepository exptResolApprvrInfmRepository;
    private final DocNoGenerator                docNoGenerator;
    private final BusiInfmRepository            busiInfmRepository;

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

        Map<String, String> docNoByApprSeqNo = buildDocNoMap(bizKey, rows.getContent());
        List<ScheduledTransferRow> dtoRows = rows.getContent().stream()
                .map(r -> toListRow(r, docNoByApprSeqNo))
                .toList();
        List<CurrencyTotal> totals = computeCurrencyTotals(rows.getContent());

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
        Sort lineSort = Sort.by(Sort.Order.asc("scdulPymnDetlNo"));
        List<TransferLineDetail> lines = detlRepository
                .findByBizKeyAndScdulPymnNo(bizKey, resolvedId, lineSort)
                .stream()
                .map(PayrollPaymentService::toLineDetail)
                .toList();

        return new ScheduledTransferDetailResponse(
                master.getScdulPymnNo(),
                master.getScdulPymnTypeCd(),
                master.getOutBankCd(),
                master.getWdrlActNo(),
                master.getTitl(),
                master.getTotalTranAmt(),
                master.getTotTrnsCnt(),
                master.getErpFileNum(),
                master.getRegId(),
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
                lines);
    }

    /* ----------------------------------------------------------------
     *  Withdrawal-account picker (legacy comm_1001_001)
     * ---------------------------------------------------------------- */

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
        return excelParser.parse(file);
    }

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
        master.setScdulPymnTypeCd(ScdulPymnTypeCd.PAYROLL.code());
        master.setOutBankCd(req.bankCd());
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

    public record DownloadResource(
            java.nio.file.Path path, String orgFileNm, String saveFileNm, long fileSize) { }

    /* ----------------------------------------------------------------
     *  Delete draft batch (legacy pymn_0005_001_d001)
     * ---------------------------------------------------------------- */

    @Transactional
    public void delete(String id) {
        String bizKey = requireTenant();
        ScdulPymnSumr master = loadMaster(bizKey, id);

        if (master.getApprSeqNo() != null && !master.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2062");
        }

        String orgFileNm  = master.getOrgFileNm();
        String saveFileNm = master.getSaveFileNm();

        detlRepository.deleteByBizKeyAndScdulPymnNo(bizKey, id);
        sumrRepository.deleteById(new ScdulPymnSumrId(bizKey, id));

        if (saveFileNm != null && !saveFileNm.isBlank()) {
            try {
                java.nio.file.Path source = fileResolver.resolve(bizKey, saveFileNm);
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
            log.info("feature=payroll-payment apprSeqNo race retrying once bizKey={}", bizKey);
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
                "feature=payroll-payment request-approval bizKey={} scdulPymnNo={} apprSeqNo={} apprLineNo={} approvers={}",
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
        mstr.setApprDv(ApprDv.PAYROLL.code());
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
            throw new BusinessException("WBE2094");
        }

        String current = master.getApprStts();
        if (APPR_STTS_APPROVED.equals(current) || APPR_STTS_REJECTED.equals(current)) {
            throw new BusinessException("WBE2095");
        }

        String next     = req.isApprove() ? APPR_STTS_APPROVED : APPR_STTS_REJECTED;
        String auditKey = req.isApprove() ? AUDIT_APPROVE_KEY  : AUDIT_REJECT_KEY;
        master.setApprStts(next);
        master.setModDtm(OffsetDateTime.now(clock).format(DTM14));
        String userId = TenantContext.currentUserId();
        if (userId != null) master.setModId(userId);
        sumrRepository.saveAndFlush(master);

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
                bizKey, id, master.getApprSeqNo(), req.action(), next);
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

    private static TransferLineDetail toLineDetail(ScdulPymnSumrDetl d) {
        return new TransferLineDetail(
                d.getScdulPymnDetlNo(),
                d.getAntDivCd(),
                d.getRcptNm(),
                d.getTarBankCd(),
                d.getFincMdaNo(),
                d.getTranUsag(),
                d.getTranAmt(),
                d.getTrnfCcyCd(),
                d.getTrcoKey(),
                d.getHandTrcoKey(),
                d.getDocNo(),
                d.getNote(),
                d.getCitadBankCd(),
                d.getChartAcctCd(),
                d.getChartAcctSeqNo(),
                d.getDepoCcyCd(),
                d.getBankTrnsShortId());
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
                        com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr::getApprSeqNo,
                        com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr::getDocNo,
                        (a, b) -> a));
    }

    private static ScheduledTransferRow toListRow(ScdulPymnSumr s,
                                                   Map<String, String> docNoByApprSeqNo) {
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
                s.getApprStts(),
                null,
                null,
                s.getTrnfYn(),
                s.getOutBankCd(),
                s.getWdrlActNo(),
                s.getPayTranDt(),
                s.getOrgFileNm(),
                docNo,
                s.getWdrlCcyCd());
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
