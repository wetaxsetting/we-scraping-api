package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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

    /** {@code yyyyMMddHHmmss} formatter shared with the audit writer. */
    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ScdulPymnSumrRepository      sumrRepository;
    private final ScdulPymnSumrDetlRepository  detlRepository;
    private final com.kosign.wecms.domain.banking.repository.AcctMstrRepository acctMstrRepository;
    private final com.kosign.wecms.domain.banking.repository.BankInfmRepository bankInfmRepository;
    private final InternalTransferKeyGenerator keyGenerator;
    private final InternalTransferExcelParser  excelParser;
    private final InternalTransferFileResolver fileResolver;
    private final AuditLogService              auditLogService;
    private final Clock                        clock;

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
            String fromDate, String toDate, String srchWd,
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

        Page<ScdulPymnSumr> rows = sumrRepository.search(
                bizKey, ScdulPymnTypeCd.INTERNAL.code(),
                fromDtm, toDtm,
                srchWdPattern,
                null,
                pageable);

        List<ScheduledTransferRow> dtoRows = rows.getContent().stream()
                .map(this::toListRow)
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
        ScdulPymnSumr master = loadMaster(bizKey, id);

        Sort lineSort = Sort.by(Sort.Order.asc("scdulPymnDetlNo"));
        List<TransferLineDetail> lines = detlRepository
                .findByBizKeyAndScdulPymnNo(bizKey, id, lineSort)
                .stream()
                .map(InternalTransferService::toLineDetail)
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
                master.getApprSeqNo(),
                null,  // appr_stts — joined from EXPT_RESOL_MSTR in a follow-up (Phase B)
                null,  // appr_stts_key
                master.getTrnfYn(),
                master.getTrnfDtm(),
                master.getCcyCd(),
                master.getWdrlCcyCd(),
                master.getOrgFileNm(),
                master.getFileSize(),
                lines);
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

        detlRepository.deleteByBizKeyAndScdulPymnNo(bizKey, id);
        sumrRepository.deleteById(new ScdulPymnSumrId(bizKey, id));

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
        return excelParser.parse(file);
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

    private ScheduledTransferRow toListRow(ScdulPymnSumr s) {
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
                null,  // appr_stts — Phase B (EXPT_RESOL_MSTR join)
                null,  // appr_stts_key
                null,  // trnf_stts — Phase B
                s.getTrnfYn(),
                s.getOutBankCd(),
                s.getWdrlActNo(),
                s.getPayTranDt(),
                s.getOrgFileNm(),
                s.getErpFileNum(),
                s.getWdrlCcyCd());
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
