package com.kosign.wecms.domain.operation.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHis;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient.OsmcGdtRegistration;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient.OsmcGdtResult;
import com.kosign.wecms.domain.operation.entity.OprtSttsInfo;
import com.kosign.wecms.domain.operation.entity.OprtSttsInfoId;
import com.kosign.wecms.domain.operation.repository.OprtSttsInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Operation Status Inquiry <b>batch</b> — refactor of the legacy JEX BCS jobs
 * {@code bcs.jex.wabooks.oprt.OPRT_0001_001} (seed / discovery, create-only) and
 * {@code bcs.jex.wabooks.oprt.OPRT_0010_001} (refresh / update, update-only). Both
 * poll the Coocon OSMC → GDT bridge ({@code TRCO_TRCO001_00003}, svc {@code BI09})
 * for the tax-authority operating status of each counterparty tax code and cache
 * it in {@code OPRT_STTS_INFO}.
 *
 * <h2>Scheduling — OFF BY DEFAULT</h2>
 * <p>This service holds NO {@code @Scheduled} annotations. In the live-JEX-DB
 * coexistence model the LEGACY JEX batch already maintains {@code OPRT_STTS_INFO}
 * on the shared DB; if the refactor also wrote it would double-write / race the
 * legacy batch. The {@code @Scheduled} + {@code @SchedulerLock} triggers live on
 * the gated {@link OperationStatusBatchScheduler}, created ONLY when
 * {@code wecms.operation-status.batch.enabled=true} (absent/false → no cron fires).
 * This service bean is always present so DI and any future on-demand entry point
 * keep working regardless of the gate. Mirrors {@code ExchangeRateBatchService} /
 * {@code ExchangeRateBatchScheduler}.
 *
 * <h2>Per-tenant transaction</h2>
 * <p>The cross-tenant fan-out ({@link #seedAllTenants()} / {@link #refreshAllTenants()})
 * is NOT transactional; it invokes the per-tenant {@code @Transactional} units
 * ({@link #seedTenant(String)} / {@link #refreshTenant(String)}) through the
 * {@link #self} Spring proxy so each tenant commits independently. A single bad
 * tenant is caught, logged, and counted — it never aborts the whole run. This is a
 * deliberate, parity-safe improvement over the legacy single cross-tenant
 * transaction (audit §8); it changes failure isolation only, not the written data.
 *
 * <h2>Parity rules (audit §5)</h2>
 * <ul>
 *   <li>F/M tenant filter only ({@code del_yn='N'}, {@code busi_stts='N'},
 *       {@code prdt_type IN ('F','M')}) — HAENGSUNG {@code 'H'} excluded.</li>
 *   <li>Seed is create-only; refresh is update-only and touches ONLY
 *       {@code COMPANYSTATUS} + {@code MOD_DTM}.</li>
 *   <li>Audit rows written to {@code EXT_TRAN_CALL_HIS} keep the legacy
 *       {@code WEB_SVC_ID} task ids ({@code OPRT_0001_001} / {@code OPRT_0010_001})
 *       for cross-system audit continuity on the shared DB.</li>
 *   <li>NO notification ({@code GENL_NOTI_*}) writes; NO
 *       {@code NXSIAS_MODULE_TRAN_LOG_HIS} writes; NO {@code System.out} — SLF4J only.</li>
 *   <li>{@code SEQ_NO} allocation routes through {@link LegacyFunctionRepository}
 *       → {@code wb_get_next_val} (funnel rule).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationStatusBatchService {

    /** {@code del_yn = 'N'} — exclude soft-deleted tenants. */
    private static final String DEL_YN_NO = "N";
    /** {@code busi_stts = 'N'} — legacy {@code BusiStts.NORMAL}; excludes
     *  suspended / closed companies. */
    private static final String BUSI_STTS_NORMAL = "N";
    /** Legacy {@code OPRT_0001_001}/{@code OPRT_0010_001} tenant filter:
     *  {@code prdt_type IN ('F','M')} — HAENGSUNG {@code 'H'} is NOT polled. */
    private static final List<String> FAN_OUT_PRDT_TYPES = List.of("F", "M");

    /** {@code BUSA_DOC_MSTR.HAND_REG_YN = 'N'} — synced-from-GDT rows only (seed source). */
    private static final String BUSA_DOC_HAND_REG_YN = "N";

    /** {@code EXT_TRAN_CALL_HIS.SVC_ID} — legacy {@code TrcoHelper.API_ID}. */
    private static final String EXT_SVC_ID        = "BI09";
    /** Legacy seed task id — preserved verbatim in {@code EXT_TRAN_CALL_HIS.WEB_SVC_ID}. */
    private static final String WEB_SVC_ID_SEED    = "OPRT_0001_001";
    /** Legacy refresh task id — preserved verbatim in {@code EXT_TRAN_CALL_HIS.WEB_SVC_ID}. */
    private static final String WEB_SVC_ID_REFRESH = "OPRT_0010_001";

    /** REG_ID fallback when a tenant carries no {@code EML} (column is NOT NULL). */
    private static final String SYSTEM_USER = "system";

    private static final DateTimeFormatter REF_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM_14_FMT    = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BusiInfmRepository busiInfmRepository;
    private final OprtSttsInfoRepository oprtSttsInfoRepo;
    private final ExtTranCallHisRepository extTranCallHisRepo;
    private final BusaDocMstrRepository busaDocMstrRepo;
    private final OsmcGdtClient osmcClient;
    private final LegacyFunctionRepository legacyFunctions;

    /**
     * Self-reference so the cross-tenant loop reaches the per-tenant
     * {@code @Transactional} methods through the Spring proxy (a direct
     * {@code this.seedTenant(...)} call would bypass the transaction advice).
     * {@code @Lazy} breaks the construction-time self cycle. Package-private so
     * unit tests can wire it without reflection.
     */
    @Autowired
    @Lazy
    OperationStatusBatchService self;

    /* ------------------------------------------------------------------
     *  Cross-tenant fan-out (NOT transactional — per-tenant commits)
     * ------------------------------------------------------------------ */

    /** Seed every F/M tenant ({@code OPRT_0001_001}). */
    public BatchSummary seedAllTenants() {
        return runAllTenants(WEB_SVC_ID_SEED, true);
    }

    /** Refresh every F/M tenant ({@code OPRT_0010_001}). */
    public BatchSummary refreshAllTenants() {
        return runAllTenants(WEB_SVC_ID_REFRESH, false);
    }

    private BatchSummary runAllTenants(String job, boolean seed) {
        List<BusiInfm> tenants = busiInfmRepository.findByDelYnAndBusiSttsAndPrdtTypeIn(
                DEL_YN_NO, BUSI_STTS_NORMAL, FAN_OUT_PRDT_TYPES);

        log.info("feature=operation-status-batch start job={} tenants={}", job, tenants.size());

        int okTenants = 0, failedTenants = 0, affectedRows = 0;
        for (BusiInfm tenant : tenants) {
            String bizKey = tenant.getBizKey();
            try {
                int affected = seed ? self.seedTenant(bizKey) : self.refreshTenant(bizKey);
                affectedRows += affected;
                okTenants++;
            } catch (RuntimeException ex) {
                // bizKey is not a credential; the OSMC URL/key are never logged.
                failedTenants++;
                log.warn("feature=operation-status-batch tenant failed job={} bizKey={}", job, bizKey, ex);
            }
        }

        BatchSummary summary = new BatchSummary(tenants.size(), okTenants, failedTenants, affectedRows);
        log.info("feature=operation-status-batch done job={} tenants={} okTenants={} "
                        + "failedTenants={} affectedRows={}",
                job, summary.tenants(), summary.okTenants(), summary.failedTenants(), summary.affectedRows());
        return summary;
    }

    /* ------------------------------------------------------------------
     *  Per-tenant units (each its own transaction)
     * ------------------------------------------------------------------ */

    /**
     * Seed one tenant — legacy {@code OPRT_0001_001}. Discovers distinct
     * counterparty tax codes from {@code BUSA_DOC_MSTR} ({@code hand_reg_yn='N'}),
     * polls the GDT bridge, and <b>INSERTs</b> a full snapshot for every returned
     * tax code that is not already tracked. Create-only: existing
     * {@code (BIZ_KEY, TAXCODE)} rows are skipped, never updated.
     *
     * @return the number of new rows inserted.
     */
    @Transactional
    public int seedTenant(String bizKey) {
        List<String> taxCodes = busaDocMstrRepo
                .findDistinctTrcoTaxIdByBizKeyAndHandRegYn(bizKey, BUSA_DOC_HAND_REG_YN);
        if (taxCodes.isEmpty()) {
            return 0;
        }

        String refDate = LocalDate.now().format(REF_DATE_FMT);
        ExtTranCallHis audit = writeAuditRow(WEB_SVC_ID_SEED, bizKey, refDate);

        OsmcGdtResult result = osmcClient.lookup(taxCodes, pad7(audit.getSeqNo()));
        patchAudit(audit, result);
        if (!result.isSuccess()) {
            return 0;
        }

        String regUser = resolveRegUser(bizKey);
        Map<String, OsmcGdtRegistration> byTaxCode = firstByTaxCode(result.rows());

        int created = 0;
        for (String taxCode : taxCodes) {
            OsmcGdtRegistration r = byTaxCode.get(taxCode);
            if (r == null) {
                continue;                                   // GDT returned no detail for this code
            }
            OprtSttsInfoId pk = new OprtSttsInfoId(bizKey, taxCode);
            if (oprtSttsInfoRepo.existsById(pk)) {
                continue;                                   // create-only: never touch an existing row
            }
            String now = LocalDateTime.now(ZoneOffset.UTC).format(DTM_14_FMT);
            OprtSttsInfo row = new OprtSttsInfo();
            row.setBizKey(bizKey);
            row.setTaxcode(taxCode);
            row.setCompanyname(r.companyName());
            row.setCompanystatus(r.companyStatus());
            row.setOfficeadd(r.officeAdd());
            // Column is NOT NULL — coalesce a blank GDT representative to "".
            row.setRepresentativefirstname(
                    r.representativeFirstName() == null ? "" : r.representativeFirstName());
            row.setSearchdatetime(coalesceSearchDtm(r.searchDatetime(), now));
            row.setRegId(regUser);
            row.setRegDtm(now);
            row.setModId(regUser);
            row.setModDtm(now);
            oprtSttsInfoRepo.save(row);
            created++;
        }
        return created;
    }

    /**
     * Refresh one tenant — legacy {@code OPRT_0010_001}. Re-polls every already-tracked
     * {@code OPRT_STTS_INFO} row from the GDT bridge and <b>UPDATEs ONLY</b>
     * {@code COMPANYSTATUS} + {@code MOD_DTM}. Update-only: a tracked row with no GDT
     * detail is left untouched, and a tax code returned by GDT that is not already
     * tracked is never inserted (that is the seed job's responsibility).
     *
     * @return the number of rows whose status was refreshed.
     */
    @Transactional
    public int refreshTenant(String bizKey) {
        List<OprtSttsInfo> existing = oprtSttsInfoRepo.findAllByBizKey(bizKey);
        if (existing.isEmpty()) {
            return 0;
        }

        List<String> taxCodes = existing.stream().map(OprtSttsInfo::getTaxcode).toList();
        String refDate = LocalDate.now().format(REF_DATE_FMT);
        ExtTranCallHis audit = writeAuditRow(WEB_SVC_ID_REFRESH, bizKey, refDate);

        OsmcGdtResult result = osmcClient.lookup(taxCodes, pad7(audit.getSeqNo()));
        patchAudit(audit, result);
        if (!result.isSuccess()) {
            return 0;
        }

        Map<String, OsmcGdtRegistration> byTaxCode = firstByTaxCode(result.rows());
        String now = LocalDateTime.now(ZoneOffset.UTC).format(DTM_14_FMT);

        int updated = 0;
        for (OprtSttsInfo row : existing) {
            OsmcGdtRegistration r = byTaxCode.get(row.getTaxcode());
            if (r == null) {
                continue;                                   // update-only: skip rows GDT didn't return
            }
            // ONLY status + mod-time change. Company name / address / representative /
            // searchDatetime are deliberately preserved (legacy OPRT_0010_001 parity).
            row.setCompanystatus(r.companyStatus());
            row.setModDtm(now);
            oprtSttsInfoRepo.save(row);
            updated++;
        }
        return updated;
    }

    /* ------------------------------------------------------------------
     *  Helpers
     * ------------------------------------------------------------------ */

    private ExtTranCallHis writeAuditRow(String webSvcId, String bizKey, String refDate) {
        BigDecimal seqNo = new BigDecimal(
                legacyFunctions.nextExtTranCallHisSeqNo(EXT_SVC_ID, refDate));
        ExtTranCallHis audit = new ExtTranCallHis();
        audit.setSvcId(EXT_SVC_ID);
        audit.setRefDate(refDate);
        audit.setSeqNo(seqNo);
        audit.setWebSvcId(webSvcId);
        audit.setBizKey(bizKey);
        audit.setRegDtm(LocalDateTime.now(ZoneOffset.UTC).format(DTM_14_FMT));
        extTranCallHisRepo.save(audit);
        return audit;
    }

    private void patchAudit(ExtTranCallHis audit, OsmcGdtResult result) {
        // RSLT_CD is VARCHAR(10). On a GDT/OSMC failure the client returns long
        // sentinel codes (e.g. WBE_OSMC_NOT_CONFIGURED, WBE_OSMC_TRANSPORT) that
        // exceed 10 chars — storing them raw overflows the column and rolls back
        // the whole per-tenant transaction (audit row + status updates), so the run
        // leaves no trace. Truncate to the column width (full text stays in ERR_CONT).
        audit.setRsltCd(truncate(result.errorCode(), 10));
        audit.setErrCont(truncate(result.errorMessage(), 1000));
        extTranCallHisRepo.save(audit);
    }

    private String resolveRegUser(String bizKey) {
        return busiInfmRepository.findById(bizKey)
                .map(BusiInfm::getEml)
                .filter(eml -> eml != null && !eml.isBlank())
                .orElse(SYSTEM_USER);
    }

    /** First registration per tax code (defensive — GDT can echo duplicates). */
    private static Map<String, OsmcGdtRegistration> firstByTaxCode(List<OsmcGdtRegistration> rows) {
        Map<String, OsmcGdtRegistration> map = new HashMap<>();
        for (OsmcGdtRegistration r : rows) {
            map.putIfAbsent(r.taxCode(), r);
        }
        return map;
    }

    /** {@code SEARCHDATETIME} is NOT NULL (length 14); fall back to {@code now} and
     *  clamp to the column width. */
    private static String coalesceSearchDtm(String raw, String now) {
        if (raw == null || raw.isBlank()) {
            return now;
        }
        return raw.length() <= 14 ? raw : raw.substring(0, 14);
    }

    /** Legacy {@code StringUtils.leftPad(seq, 7, "0")} — the GDT request {@code UniqueKey}. */
    private static String pad7(BigDecimal seqNo) {
        String s = seqNo.toBigInteger().toString();
        return s.length() >= 7 ? s : "0".repeat(7 - s.length()) + s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Per-run counters (logged + returned for tests / future on-demand callers). */
    public record BatchSummary(int tenants, int okTenants, int failedTenants, int affectedRows) { }
}
