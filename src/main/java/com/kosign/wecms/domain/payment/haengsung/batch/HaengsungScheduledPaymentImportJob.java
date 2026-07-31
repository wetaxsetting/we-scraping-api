package com.kosign.wecms.domain.payment.haengsung.batch;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.lock.JobLockNames;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.haengsung.dto.HaengsungRefreshResult;
import com.kosign.wecms.domain.payment.haengsung.service.ErpPaymentRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled trigger for the HAENGSUNG Oracle payment import
 * ({@code haengsung-scheduled-payment-import}, Step 3 — refactor of legacy
 * {@code ERP_0100_002}). It is ONLY a trigger: it reuses the Step-2
 * {@link ErpPaymentRefreshService} (one import implementation) — the legacy
 * batch's inline insert copy is NOT ported (roadmap §9A / M-9).
 *
 * <p><b>Gating (disabled by default):</b>
 * <ul>
 *   <li>The bean exists only when
 *       {@code haengsung.scheduled-payment-import.enabled=true}
 *       ({@code matchIfMissing=false}) — so with the default the cron is never
 *       even registered.</li>
 *   <li>It additionally no-ops when the Step-1 Oracle feature is off
 *       ({@code haengsung.oracle.enabled=false}): the shared import service bean
 *       is absent, so {@code refreshServiceProvider.getIfAvailable()} returns
 *       {@code null} and {@link #runOnce()} logs and returns.</li>
 * </ul>
 *
 * <p>Per run it selects every active HAENGSUNG company
 * ({@code PRDT_TYPE='H' AND DEL_YN='N'}) and, per company, imports all
 * un-received Oracle masters (every payment type) via
 * {@link ErpPaymentRefreshService#importUnreceivedForCurrentTenant()}. TenantContext
 * is set to a system actor per company (so the shared guard, key generation and
 * audit writer behave exactly as in the manual path) and cleared afterwards.
 * One company's failure is logged and the loop continues with the next.
 *
 * <p>{@code @SchedulerLock} co-located with {@code @Scheduled} (ShedLock proxy
 * mode) serialises the job across instances. Crons are anchored to ICT. No
 * Oracle credentials are logged.
 *
 * <p><b>Lock identity is shared with the interactive Refresh endpoint.</b> The
 * lock name is {@link JobLockNames#HAENGSUNG_PAYMENT_IMPORT}, the identity for the
 * {@code ErpPaymentRefreshService} import body rather than for this job alone.
 * {@code HaengsungPaymentRefreshController} acquires the same identity before
 * running the same body, so a user pressing Refresh can no longer import
 * concurrently with this job (scheduler-audit finding F-3).
 *
 * <p><b>COEXISTENCE PRECONDITION — read before enabling.</b>
 * {@code haengsung.scheduled-payment-import.enabled=true} is safe ONLY after
 * legacy {@code ERP_0100_002} is disabled in that environment. ShedLock does NOT
 * help — the legacy JexSimpleBatch runtime does not participate in the
 * {@code shedlock} table, so both sides would import the same un-received Oracle
 * masters. Note also that legacy {@code ERP_0100_004} (the WeCMS→ERP
 * vendor/expense/payroll/overseas export, {@code *}{@code /5 7-19}) has NO
 * refactor scheduler; whether the event-driven Step-5 write-back fully replaces it
 * is an unresolved blocking item (OQ-1). See
 * {@code docs/audits/scheduler-legacy-parity-audit.md} §7 + §10.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(HaengsungScheduledPaymentImportProperties.class)
@ConditionalOnProperty(
        prefix = "haengsung.scheduled-payment-import",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class HaengsungScheduledPaymentImportJob {

    private static final String VIETNAM_TZ_ID = "Asia/Ho_Chi_Minh";
    /** System actor stamped as REG_ID on batch-imported drafts (matches the
     *  Oracle receive write-back user). Not an interactive USER_INFM row. */
    private static final String SYSTEM_USER = "WEBCASH";

    /** Optional — present only when {@code haengsung.oracle.enabled=true}. */
    private final ObjectProvider<ErpPaymentRefreshService> refreshServiceProvider;
    private final BusiInfmRepository busiInfmRepository;
    private final HaengsungScheduledPaymentImportProperties properties;

    @Scheduled(cron = "${haengsung.scheduled-payment-import.cron:" +
            HaengsungScheduledPaymentImportProperties.DEFAULT_CRON + "}", zone = VIETNAM_TZ_ID)
    @SchedulerLock(name = JobLockNames.HAENGSUNG_PAYMENT_IMPORT,
                   lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void run() {
        runOnce();
    }

    /**
     * One import pass over all HAENGSUNG businesses. Returns the aggregated
     * summary (also logged) so it can be unit-tested without the scheduler /
     * ShedLock proxies.
     */
    public Summary runOnce() {
        ErpPaymentRefreshService service = refreshServiceProvider.getIfAvailable();
        if (service == null) {
            log.info("feature=haengsung-scheduled-import skipped — Oracle feature disabled "
                    + "(haengsung.oracle.enabled=false)");
            return Summary.EMPTY;
        }

        List<BusiInfm> businesses = busiInfmRepository.findByPrdtTypeAndDelYn("H", "N");
        log.info("feature=haengsung-scheduled-import start cron={} businesses={}",
                properties.cron(), businesses.size());

        int okBusinesses = 0, failedBusinesses = 0;
        int mastersFound = 0, imported = 0, skipped = 0, failed = 0, detailsImported = 0;

        for (BusiInfm busi : businesses) {
            try {
                // System actor + tenant context so the shared service's H-guard,
                // tax-id resolution, key generation and audit writer behave as in
                // the manual path. prdtType is "H" (this is an H business).
                TenantContext.set(SYSTEM_USER, SYSTEM_USER, busi.getBizKey(), LangCd.EN,
                        TenantContext.AUTH_GR_USER, busi.getPrdtType());
                HaengsungRefreshResult r = service.importUnreceivedForCurrentTenant();
                mastersFound    += r.mastersFound();
                imported        += r.imported();
                skipped         += r.skipped();
                failed          += r.failed();
                detailsImported += r.detailsImported();
                okBusinesses++;
            } catch (RuntimeException ex) {
                failedBusinesses++;
                // bizKey is not a credential; URL/user/password are never logged.
                log.warn("feature=haengsung-scheduled-import business import failed bizKey={}",
                        busi.getBizKey(), ex);
            } finally {
                TenantContext.clear();
            }
        }

        log.info("feature=haengsung-scheduled-import done businesses={} okBusinesses={} "
                        + "failedBusinesses={} mastersFound={} imported={} skipped={} failed={} detailsImported={}",
                businesses.size(), okBusinesses, failedBusinesses,
                mastersFound, imported, skipped, failed, detailsImported);

        return new Summary(businesses.size(), okBusinesses, failedBusinesses,
                mastersFound, imported, skipped, failed, detailsImported);
    }

    /** Aggregated result of one {@link #runOnce()} pass across all HAENGSUNG businesses. */
    public record Summary(int businesses, int okBusinesses, int failedBusinesses,
                          int mastersFound, int imported, int skipped, int failed, int detailsImported) {
        public static final Summary EMPTY = new Summary(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
