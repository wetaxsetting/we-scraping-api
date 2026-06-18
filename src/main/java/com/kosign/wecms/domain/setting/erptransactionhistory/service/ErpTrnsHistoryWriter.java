package com.kosign.wecms.domain.setting.erptransactionhistory.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.erptransactionhistory.entity.ErpTrnsHstr;
import com.kosign.wecms.domain.setting.erptransactionhistory.repository.ErpTrnsHstrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Shared writer for {@code ERP_TRNS_HSTR}. Per the
 * {@link com.kosign.wecms.domain.setting.erptransactionhistory.entity.ErpTrnsHstr}
 * entity Javadoc — "writes happen on the JEX side ... future Payment /
 * Transfer / Account-Inquiry features that produce a file write their
 * own audit row from their own service" — every feature that emits a
 * file the user can later download through Settings &gt; ERP Transaction
 * History calls this writer.
 *
 * <p>The writer owns three steps that must succeed as a unit:
 *
 * <ol>
 *   <li>Allocate the next {@code SEQ_NO} via the per-tenant Java-side
 *       MAX+1 generator (no {@code wb_get_next_val} dependency).</li>
 *   <li>Copy the source file into the ERP-transaction-history tenant
 *       subtree, generating a unique {@code SAVE_FILE_NM} so concurrent
 *       writers don't collide.</li>
 *   <li>INSERT the row.</li>
 * </ol>
 *
 * <p>Concurrency caveat: two parallel callers may read the same MAX
 * seq_no and both attempt {@code seq+1}. The composite-PK rejects the
 * second insert; we retry once with a fresh MAX. Same precedent as
 * Approval Line / Chart of Accounts.
 *
 * <p>Transaction scope: each {@link #recordDeletedFile} call runs in
 * its own transaction (REQUIRES_NEW) so an audit-write failure cannot
 * roll back the caller's primary mutation. The file copy happens
 * before the INSERT so a partial state on failure is one orphaned file
 * (never a missing-file row in the table — that would surface as
 * {@code WBE2015} on later download attempts).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpTrnsHistoryWriter {

    private static final DateTimeFormatter REG_DTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ErpTrnsHstrRepository repository;
    private final ErpTrnsHistoryFileResolver fileResolver;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    /**
     * Record a deleted payment-batch file under ERP Transaction History
     * so the user can still download the Excel after the source batch
     * is gone. Writes {@code ERP_TYPE = 'D'} (deleted) so the row lands
     * in the "Deleted" tab.
     *
     * <p>Caller responsibilities:
     * <ul>
     *   <li>Compute the source path through its own feature's file
     *       resolver (e.g. {@code InternalTransferFileResolver.resolve}).
     *       The writer copies the bytes — it does not delete the
     *       source.</li>
     *   <li>Pass the {@code TRNS_TYPE_CD} that matches the feature
     *       ({@code "16"} for Internal Scheduled Transfer, {@code "17"}
     *       for Internal Transfer Result, etc.). See
     *       {@code settings.erp_transaction_history.type.*} locale
     *       entries for the full set.</li>
     *   <li>Pass the original filename (the name the user uploaded).
     *       The writer preserves it verbatim in {@code ORG_FILE_NM}.</li>
     * </ul>
     *
     * <p>No-op (logs at WARN, returns silently) when the source file
     * doesn't exist on disk — the batch row may have been created
     * without a file, or the file was already cleaned up. The caller's
     * primary delete should not fail just because the audit row can't
     * be written.
     *
     * @param trnsTypeCd  ERP transaction-type code (2-digit, "01".."17");
     *                    "16" = Internal Scheduled Transfer.
     * @param sourceFile  absolute path to the source file to copy. The
     *                    writer copies, never moves; the caller decides
     *                    whether to delete the source separately.
     * @param orgFileNm   original filename to preserve. Falls back to
     *                    the source file's name when blank.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordDeletedFile(String trnsTypeCd, Path sourceFile, String orgFileNm) {
        recordFile("D", trnsTypeCd, sourceFile, orgFileNm);
    }

    /**
     * Record an uploaded payment-batch file under ERP Transaction History
     * so the user can download the Excel from the ERP-history panel
     * after the batch is created. Writes {@code ERP_TYPE = 'E'} (ERP →
     * WeCMS upload) so the row lands in the "ERP" tab.
     *
     * <p>Mirrors legacy {@code ErpTrnsHisHelper.erpTrnsHisUploader} —
     * the only difference is the source bytes already live in the
     * payment-module storage tree (e.g.
     * {@code <storage-root>/<biz_key>/internal-transfer/<save_file_nm>}),
     * so the writer copies them into the ERP-history tenant subtree
     * before writing the row.
     *
     * <p>Caller responsibilities are identical to {@link #recordDeletedFile}:
     * resolve the source path via the feature's file resolver, pass the
     * matching {@code TRNS_TYPE_CD} ({@code "02"} Vendor Scheduled,
     * {@code "03"} Expense Scheduled, {@code "12"} Payroll Scheduled,
     * {@code "16"} Internal Scheduled Transfer), and pass the original
     * filename.
     *
     * <p>Same failure stance as the delete path — no-op + WARN log on a
     * missing source file or persistence failure. The caller's primary
     * write (the payment-batch INSERT) must not fail because the audit
     * copy could not be made.
     *
     * @param trnsTypeCd  ERP transaction-type code matching the
     *                    {@code settings.erp_transaction_history.type.*}
     *                    locale entries. {@code "02"} Vendor Scheduled,
     *                    {@code "16"} Internal Scheduled Transfer, etc.
     * @param sourceFile  absolute path to the original Excel on the
     *                    payment-module storage tree. Copied verbatim;
     *                    never moved.
     * @param orgFileNm   original filename (the name the user uploaded).
     *                    Preserved in {@code ORG_FILE_NM}; falls back to
     *                    the source file's leaf name when blank.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordUploadedFile(String trnsTypeCd, Path sourceFile, String orgFileNm) {
        recordFile("E", trnsTypeCd, sourceFile, orgFileNm);
    }

    /**
     * Record a WeCMS&rarr;ERP file the user pulled out of WeCMS — an original-file
     * download or an Excel export off a Payment Result screen. Writes
     * {@code ERP_TYPE = 'W'} (WeCMS&rarr;ERP) so the row lands in the "WeCMS" tab,
     * mirroring legacy {@code pymn_0002_002_r002}/{@code _r003}'s
     * {@code ErpTrnsHstrDAO.create(... ErpType.WECMS ...)}.
     *
     * <p>Same contract + failure stance as {@link #recordUploadedFile} /
     * {@link #recordDeletedFile}: {@code REQUIRES_NEW} so it never enlists in
     * the caller's (read-only) download transaction, and every failure is
     * caught + WARN-logged, never rethrown — matching legacy, where the whole
     * {@code saveExcelFile(...)} audit step is wrapped in a swallow-and-log
     * try/catch and the download/export proceeds regardless.
     *
     * @param trnsTypeCd  ERP transaction-type code (2-digit). The Payment
     *                    Result codes are {@code "04"} Vendor, {@code "05"}
     *                    Expense, {@code "06"} Payroll, {@code "14"} Overseas,
     *                    {@code "17"} Internal Transfer (legacy
     *                    {@code ErpTypeCd.*_RESULT}; see
     *                    {@code settings.erp_transaction_history.type.*}).
     * @param sourceFile  absolute path to the file the user is pulling. Copied
     *                    verbatim into the ERP-history tenant subtree.
     * @param orgFileNm   original filename to preserve in {@code ORG_FILE_NM}.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordExportedFile(String trnsTypeCd, Path sourceFile, String orgFileNm) {
        recordFile("W", trnsTypeCd, sourceFile, orgFileNm);
    }

    /** Shared body for {@link #recordDeletedFile} / {@link #recordUploadedFile}.
     *  The only per-caller variant is the {@code erpType} bucket. */
    private void recordFile(String erpType, String trnsTypeCd, Path sourceFile, String orgFileNm) {
        if (trnsTypeCd == null || trnsTypeCd.isBlank()) {
            log.warn("ERP audit-write skipped — trnsTypeCd is blank");
            return;
        }
        if (sourceFile == null) {
            log.warn("ERP audit-write skipped — sourceFile is null (trnsTypeCd={})", trnsTypeCd);
            return;
        }
        if (!Files.isRegularFile(sourceFile) || !Files.isReadable(sourceFile)) {
            log.warn("ERP audit-write skipped — source file not readable: {} (trnsTypeCd={})",
                    sourceFile, trnsTypeCd);
            return;
        }

        String bizKey = TenantContext.currentBizKey();
        String regId  = TenantContext.currentUserId();
        if (bizKey == null || regId == null) {
            log.warn("ERP audit-write skipped — TenantContext not populated (trnsTypeCd={})", trnsTypeCd);
            return;
        }

        String resolvedOrgFileNm = (orgFileNm == null || orgFileNm.isBlank())
                ? sourceFile.getFileName().toString()
                : orgFileNm;

        try {
            long fileSize = Files.size(sourceFile);
            String saveFileNm = buildSaveFileNm(resolvedOrgFileNm);

            // 1) Copy file into the ERP-transaction-history tenant subtree.
            //    Done BEFORE the INSERT so a row in the table always has
            //    a corresponding file (or no row at all on copy failure).
            Path destDir = fileResolver.tenantDirectory(bizKey);
            Files.createDirectories(destDir);
            Path dest = fileResolver.resolve(bizKey, saveFileNm);
            Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);

            // 2) Allocate SEQ_NO + INSERT, with one retry on PK race.
            //    Both attempts use the same `dest` file — no orphan on
            //    retry success; one orphan if both attempts fail (rare,
            //    surfaces as a periodic cleanup task in production).
            String regDtm = OffsetDateTime.now(ZoneOffset.UTC).format(REG_DTM_FORMAT);
            try {
                insertRow(bizKey, erpType, trnsTypeCd, resolvedOrgFileNm, saveFileNm, fileSize, regId, regDtm);
            } catch (DataIntegrityViolationException race) {
                log.info("ERP audit-write seq_no race detected — retrying once (bizKey={})", bizKey);
                insertRow(bizKey, erpType, trnsTypeCd, resolvedOrgFileNm, saveFileNm, fileSize, regId, regDtm);
            }

            log.info("ERP audit-write committed — bizKey={} erpType={} trnsTypeCd={} saveFileNm={} size={}",
                    bizKey, erpType, trnsTypeCd, saveFileNm, fileSize);
        } catch (IOException ex) {
            log.warn("ERP audit-write failed bizKey={} erpType={} trnsTypeCd={} sourceFile={}",
                    bizKey, erpType, trnsTypeCd, sourceFile, ex);
        } catch (RuntimeException ex) {
            log.warn("ERP audit-write failed bizKey={} erpType={} trnsTypeCd={} sourceFile={}",
                    bizKey, erpType, trnsTypeCd, sourceFile, ex);
        }
    }

    /* ----------------------------------------------------------------
     *  Internals
     * ---------------------------------------------------------------- */

    /**
     * Build a collision-resistant filename for the ERP-history copy.
     * Format: {@code {timestamp}_{uuid8}_{orgFileNm}}. Keeps the original
     * extension so OS / browsers pick the right icon on download.
     */
    private static String buildSaveFileNm(String orgFileNm) {
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).format(REG_DTM_FORMAT);
        String shortId   = UUID.randomUUID().toString().substring(0, 8);
        // Strip any directory component from orgFileNm — defence in
        // depth against a malformed input from a future caller.
        String safeName = orgFileNm.replace('\\', '/');
        int slash = safeName.lastIndexOf('/');
        if (slash >= 0) safeName = safeName.substring(slash + 1);
        return timestamp + "_" + shortId + "_" + safeName;
    }

    private void insertRow(
            String bizKey,
            String erpType,
            String trnsTypeCd,
            String orgFileNm,
            String saveFileNm,
            long fileSize,
            String regId,
            String regDtm) {
        // SEQ_NO via the single wb_get_next_val funnel (V140 #28 — per-tenant MAX+1).
        long nextSeqNo = Long.parseLong(legacyFunctions.nextErpTrnsSeqNo(bizKey));
        ErpTrnsHstr row = new ErpTrnsHstr();
        row.setBizKey(bizKey);
        row.setSeqNo(nextSeqNo);
        row.setOrgFileNm(orgFileNm);
        row.setSaveFileNm(saveFileNm);
        row.setFileSize(fileSize);
        row.setErpType(erpType);             // 'D' deleted · 'E' ERP→WeCMS · 'W' WeCMS→ERP
        row.setTrnsTypeCd(trnsTypeCd);
        row.setRegId(regId);
        row.setRegDtm(regDtm);
        repository.saveAndFlush(row);        // flush so race surfaces here, not on tx commit
    }
}
