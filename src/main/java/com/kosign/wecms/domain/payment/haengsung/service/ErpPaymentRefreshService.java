package com.kosign.wecms.domain.payment.haengsung.service;

import com.kosign.wecms.common.enums.PrdtType;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.haengsung.HaengsungPaymentType;
import com.kosign.wecms.domain.payment.haengsung.dto.HaengsungRefreshRequest;
import com.kosign.wecms.domain.payment.haengsung.dto.HaengsungRefreshResult;
import com.kosign.wecms.domain.payment.haengsung.oracle.HaengsungErpOracleRepository;
import com.kosign.wecms.domain.payment.haengsung.oracle.OracleTransDetail;
import com.kosign.wecms.domain.payment.haengsung.oracle.OracleTransMaster;
import com.kosign.wecms.domain.payment.internaltransfer.service.ScdulPymnTypeCd;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Orchestrates the HAENGSUNG Oracle Refresh import ({@code haengsung-payment-refresh}
 * §5.1, Phase 1 = manual Refresh only). Single import implementation (the
 * consolidated {@code ErpDataRefreshHelper} model, §9A) — a future scheduled
 * batch would call this same service, never a second copy.
 *
 * <p>Flow: product-type {@code H} guard → resolve tenant tax id → read pending
 * Oracle masters (filtered by type + period) → per master: read details, import
 * to WeCMS (its own transaction via {@link ErpPaymentRefreshMasterImporter}),
 * then — only after that commit and with no XA — write the receive status back to
 * Oracle and log an audit row. One master's failure rolls back that master only
 * and the loop continues.
 *
 * <p>Gated on {@code haengsung.oracle.enabled=true}; absent (disabled) the
 * controller surfaces {@code WBE_HS_REFRESH_DISABLED}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class ErpPaymentRefreshService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DTM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String ERP_RCV_DONE = "Y";

    // ERP_TRNS_HSTR TRNS_TYPE_CD audit buckets (settings.erp_transaction_history.type.*)
    private static final String AUDIT_VENDOR   = "02";
    private static final String AUDIT_EXPENSE   = "03";
    private static final String AUDIT_PAYROLL   = "12";
    private static final String AUDIT_OVERSEAS  = "14";

    private final HaengsungErpOracleRepository oracle;
    private final ErpPaymentRefreshMasterImporter importer;
    private final BusiInfmRepository busiInfmRepository;
    private final ErpTrnsHistoryWriter auditWriter;

    /**
     * Manual Refresh entrypoint — user-selected payment type + period, tenant
     * resolved from the signed JWT (TenantContext).
     */
    public HaengsungRefreshResult refresh(HaengsungRefreshRequest request) {
        GuardedTenant tenant = resolveAndGuard();
        HaengsungPaymentType paymentType = HaengsungPaymentType.fromWire(request.paymentType())
                .orElseThrow(() -> new BusinessException("WBE_VALIDATION", "Invalid payment_type"));
        String[] period = parsePeriod(request.periodDate());
        List<OracleTransMaster> masters =
                oracle.findMasters(tenant.taxId(), period[0], period[1], paymentType);
        log.info("feature=haengsung-refresh mode=manual bizKey={} type={} period={}..{} mastersFound={}",
                tenant.bizKey(), paymentType, period[0], period[1], masters.size());
        return processMasters(tenant.bizKey(), tenant.userId(), masters);
    }

    /**
     * Scheduled-batch entrypoint ({@code haengsung-scheduled-payment-import},
     * legacy {@code ERP_0100_002}) — import ALL un-received masters
     * ({@code LAST_STATUS='00' AND ERP_RCV_FLAG='N'}), every payment type, no
     * period, for the CURRENT tenant. The scheduled job sets TenantContext per
     * HAENGSUNG business before calling, so this runs the SAME guard + per-master
     * import + receive write-back + audit as the manual path — one import
     * implementation, never a second copy of the legacy batch.
     */
    public HaengsungRefreshResult importUnreceivedForCurrentTenant() {
        GuardedTenant tenant = resolveAndGuard();
        List<OracleTransMaster> masters = oracle.findUnreceivedMasters(tenant.taxId());
        log.info("feature=haengsung-refresh mode=scheduled bizKey={} mastersFound={}",
                tenant.bizKey(), masters.size());
        return processMasters(tenant.bizKey(), tenant.userId(), masters);
    }

    /**
     * Product-type {@code H} guard (from the signed JWT / TenantContext) +
     * active-tenant check + tax-id (BIZ_NO) resolution. Shared by both
     * entrypoints so manual and scheduled enforce identical tenancy rules.
     */
    private GuardedTenant resolveAndGuard() {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        if (PrdtType.fromCode(TenantContext.currentPrdtType()).filter(p -> p == PrdtType.H).isEmpty()) {
            throw new BusinessException("WBE_HS_REFRESH_NOT_HAENGSUNG");
        }
        BusiInfm busi = busiInfmRepository.findById(bizKey)
                .filter(b -> "H".equals(b.getPrdtType()) && !"Y".equals(b.getDelYn()))
                .orElseThrow(() -> new BusinessException("WBE_HS_REFRESH_NOT_HAENGSUNG"));
        String taxId = busi.getTaxId();
        if (taxId == null || taxId.isBlank()) {
            throw new BusinessException("WBE_HS_REFRESH_NO_TAX_ID");
        }
        return new GuardedTenant(bizKey, userId, taxId);
    }

    /**
     * The single per-master import loop shared by manual Refresh and the
     * scheduled batch: per master read details, import in its own transaction,
     * then (post-commit, no XA) write the receive status back to Oracle and log
     * an audit row. One master's failure rolls back only that master.
     */
    private HaengsungRefreshResult processMasters(String bizKey, String userId,
                                                  List<OracleTransMaster> masters) {
        String nowVn = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);
        int imported = 0, skipped = 0, failed = 0, detailsImported = 0;

        for (OracleTransMaster master : masters) {
            List<OracleTransDetail> details = oracle.findDetails(master.fileDate(), master.fileNum());
            if (details.isEmpty()) {
                skipped++;
                continue;
            }

            MasterImportOutcome outcome;
            try {
                outcome = importWithRetry(bizKey, userId, nowVn, master, details);
            } catch (RuntimeException ex) {
                failed++;
                log.warn("feature=haengsung-refresh master import failed bizKey={} fileDate={} fileNum={}",
                        bizKey, master.fileDate(), master.fileNum(), ex);
                continue;
            }

            if (outcome.status() == MasterImportOutcome.Status.SKIPPED_APPROVAL_LOCK) {
                skipped++;
                continue;
            }

            imported++;
            detailsImported += outcome.detailsImported();

            // Audit (non-blocking, fileless — never aborts the import; M-7).
            auditWriter.recordImportedBatch(auditTypeCd(master),
                    master.fileDate() + "-" + master.fileNum());

            // Oracle receive write-back — AFTER the WeCMS commit, no XA (§8).
            writeBackReceiveStatus(master, details, nowVn);
        }

        return new HaengsungRefreshResult(masters.size(), imported, skipped, failed, detailsImported);
    }

    /** Per-master import with a one-shot retry on a key-allocation race
     *  (wb_get_next_val MAX+1 has no row lock — same stance as the Excel paths). */
    private MasterImportOutcome importWithRetry(String bizKey, String userId, String nowVn,
                                                OracleTransMaster master, List<OracleTransDetail> details) {
        try {
            return importer.importMaster(bizKey, userId, nowVn, master, details);
        } catch (DataIntegrityViolationException race) {
            log.info("feature=haengsung-refresh key race — retrying once fileDate={} fileNum={}",
                    master.fileDate(), master.fileNum());
            return importer.importMaster(bizKey, userId, nowVn, master, details);
        }
    }

    /**
     * Write the receive status back to Oracle for a successfully-imported master.
     * Master flag set only when not already {@code 'Y'} (§7). Failures are logged,
     * never propagated — the WeCMS draft stays imported and the next Refresh
     * re-reads the master (idempotency, not atomicity — §8).
     */
    private void writeBackReceiveStatus(OracleTransMaster master, List<OracleTransDetail> details, String nowVn) {
        try {
            if (!ERP_RCV_DONE.equalsIgnoreCase(master.erpRcvFlag())) {
                oracle.markMasterReceived(master.fileDate(), master.fileNum(), nowVn);
            }
            for (OracleTransDetail d : details) {
                oracle.markDetailReceived(master.fileDate(), master.fileNum(), d.fileSeq(), nowVn);
            }
        } catch (RuntimeException ex) {
            log.warn("feature=haengsung-refresh Oracle write-back failed (import kept) fileDate={} fileNum={}",
                    master.fileDate(), master.fileNum(), ex);
        }
    }

    /** Map the master's route to the ERP_TRNS_HSTR TRNS_TYPE_CD audit bucket. */
    private static String auditTypeCd(OracleTransMaster master) {
        if (ErpPaymentRefreshMasterImporter.isOverseas(master.fileGb())) {
            return AUDIT_OVERSEAS;
        }
        String typeCd = ErpPaymentRefreshMasterImporter.deriveScheduledTypeCd(master);
        if (ScdulPymnTypeCd.PAYROLL.code().equals(typeCd)) return AUDIT_PAYROLL;
        if (ScdulPymnTypeCd.EXPENSE.code().equals(typeCd)) return AUDIT_EXPENSE;
        return AUDIT_VENDOR;
    }

    /** {@code YYYYMMDD} → {from,to}=same; {@code YYYYMMDD-YYYYMMDD} → {from,to}. */
    private static String[] parsePeriod(String periodDate) {
        int dash = periodDate.indexOf('-');
        if (dash < 0) {
            return new String[] { periodDate, periodDate };
        }
        return new String[] { periodDate.substring(0, dash), periodDate.substring(dash + 1) };
    }

    /** Resolved + guarded tenant identity shared by the manual and scheduled entrypoints. */
    private record GuardedTenant(String bizKey, String userId, String taxId) { }
}
