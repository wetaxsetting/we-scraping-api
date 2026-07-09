package com.kosign.wecms.domain.payment.haengsung.exchangerate;

import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.entity.BankExchRateInfm;
import com.kosign.wecms.domain.banking.repository.BankExchRateInfmRepository;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * HAENGSUNG Oracle exchange-rate sync ({@code haengsung-exchange-rate-sync},
 * Step 6 — refactor of legacy {@code ERP_0100_001}). Pushes WeCMS-collected FX
 * rates out to the HAENGSUNG Oracle ERP. Pure one-way push:
 * <pre>WeCMS PostgreSQL (BANK_EXCH_RATE_INFM) → HAENGSUNG Oracle (TB_FCY_EXRATE)</pre>
 * Touches no payment tables, creates no new FX business flow — it only reads the
 * existing WeCMS rate data (populated by the FX scraping batch) and mirrors it to
 * Oracle.
 *
 * <p><b>Gating.</b> The bean exists only when {@code haengsung.oracle.enabled=true}
 * (Step-1 foundation) — it needs the secondary Oracle datasource. The scheduled
 * trigger ({@link HaengsungExchangeRateSyncJob}) is additionally gated OFF by
 * default ({@code haengsung.exchange-rate-sync.enabled=false}).
 *
 * <p><b>Flow (legacy {@code ERP_0100_001}).</b> Iterate every active HAENGSUNG
 * company ({@code PRDT_TYPE='H' AND DEL_YN='N'}). Per company, read today's
 * {@code BANK_EXCH_RATE_INFM} rows (all currencies/rounds, any {@code ERP_SND_YN}).
 * Per rate row (own unit of work, no XA):
 * <ol>
 *   <li>Upsert Oracle {@code TB_FCY_EXRATE} — {@code UPDATE} when the source row's
 *       {@code ERP_SND_YN='Y'} (already sent), else idempotent {@code INSERT}.</li>
 *   <li>On Oracle success, set WeCMS {@code ERP_SND_YN='Y'} for that row.</li>
 * </ol>
 * Then write one {@code ERP_TRNS_HSTR} audit row per company
 * ({@code ERP_TYPE='W'}, {@code TRNS_TYPE_CD='15'}), even on 0 records.
 *
 * <p><b>No XA / retry.</b> Oracle and WeCMS are separate datasources. Each rate
 * row's Oracle write commits on its own; only then is the WeCMS flag flipped. If
 * the Oracle write throws, the WeCMS row is left untouched ({@code ERP_SND_YN}
 * unchanged), the error is logged, and the loop continues with the next row — the
 * row is retried on the next run. The Oracle insert is idempotent
 * ({@code NOT EXISTS} on the PK) so a flag that did not flip causes a no-op insert,
 * never a duplicate-key error. One company's failure never blocks the others.
 *
 * <p><b>Bank-code mapping.</b> WeCMS stores the original bank code; Oracle expects
 * the CITAD code. Legacy resolves this via {@code CITAD_BANK_CD_LOOKUP} (read) +
 * a hardcoded 2-entry reverse map (write-back). The refactor reads the entity
 * directly (original code → used as the WeCMS write-back PK, no reverse map needed)
 * and forward-maps original→CITAD for the Oracle key via the same 2 entries the
 * legacy round-trip supports. A row whose bank code is outside that set is skipped
 * (logged) rather than pushed with an unmappable code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class HaengsungExchangeRateSyncService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DTM_FMT  = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Legacy stamps the WeCMS flag write + the audit row with the SYSTEM actor. */
    private static final String SYSTEM_USER = "SYSTEM";
    /** ERP_TRNS_HSTR TRNS_TYPE_CD for Exchange Rate (legacy ErpTypeCd.EXCHANGE_RATE = 15). */
    private static final String AUDIT_EXCHANGE_RATE = "15";
    private static final String ERP_SND_DONE = "Y";

    /**
     * Original WeCMS bank code → Oracle CITAD bank code. Mirrors the exact set the
     * legacy round-trip supports (legacy hardcodes the inverse CITAD→original map:
     * {@code 01663001→11704020}, {@code 79616001→11704088}). A rate row on any
     * other bank is skipped (legacy could not flip its send-flag either).
     */
    private static final Map<String, String> CITAD_BY_ORIGINAL = Map.of(
            "11704020", "01663001",
            "11704088", "79616001");

    private final BusiInfmRepository busiInfmRepository;
    private final BankExchRateInfmRepository bankExchRateInfmRepository;
    private final HaengsungFcyExRateOracleRepository fcyExRateOracle;
    private final ErpTrnsHistoryWriter auditWriter;

    /**
     * One sync pass over all active HAENGSUNG companies. Returns the aggregated
     * summary (also logged) so it is unit-testable without the scheduler/ShedLock
     * proxies. Never throws for a per-company / per-row failure — those are caught,
     * logged, and counted.
     */
    public Summary sync() {
        List<BusiInfm> businesses = busiInfmRepository.findByPrdtTypeAndDelYn("H", "N");

        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        String today = now.format(DATE_FMT);
        String nowVn = now.format(DTM_FMT);

        log.info("feature=haengsung-exchange-rate-sync start refDate={} businesses={}",
                today, businesses.size());

        int okBiz = 0, failedBiz = 0, processed = 0, skipped = 0, failed = 0;

        for (BusiInfm busi : businesses) {
            String bizKey = busi.getBizKey();
            String bizNo = busi.getTaxId();
            if (bizNo == null || bizNo.isBlank()) {
                log.warn("feature=haengsung-exchange-rate-sync skip business — no TAX_ID bizKey={}", bizKey);
                failedBiz++;
                continue;
            }

            int bizProcessed = 0;
            try {
                List<BankExchRateInfm> rows = bankExchRateInfmRepository.findByBizKeyAndRefDate(bizKey, today);
                log.info("feature=haengsung-exchange-rate-sync bizKey={} rates={}", bizKey, rows.size());

                for (BankExchRateInfm rate : rows) {
                    Outcome outcome = pushRate(bizNo, rate, nowVn);
                    switch (outcome) {
                        case PROCESSED -> { bizProcessed++; processed++; }
                        case SKIPPED   -> skipped++;
                        case FAILED    -> failed++;
                    }
                }
                okBiz++;
            } catch (RuntimeException ex) {
                // bizKey is not a credential; Oracle URL/user/password are never logged.
                failedBiz++;
                log.warn("feature=haengsung-exchange-rate-sync business failed bizKey={}", bizKey, ex);
            }

            // Audit row per company (legacy parity — written even on 0 records).
            auditWriter.recordBatchPush(bizKey, AUDIT_EXCHANGE_RATE,
                    "Batch_WeCMS_to_ERP_Exchange_Rate_" + nowVn
                            + (bizProcessed == 0 ? "_NO_DATA" : "_" + bizProcessed + "_records"));
        }

        Summary summary = new Summary(businesses.size(), okBiz, failedBiz, processed, skipped, failed);
        log.info("feature=haengsung-exchange-rate-sync done businesses={} okBusinesses={} "
                        + "failedBusinesses={} processed={} skipped={} failed={}",
                summary.businesses(), summary.okBusinesses(), summary.failedBusinesses(),
                summary.processed(), summary.skipped(), summary.failed());
        return summary;
    }

    /**
     * Push one WeCMS rate row to Oracle and, on success, flip its WeCMS send-flag.
     * No XA: the Oracle write is its own unit; the WeCMS flag is updated only after
     * the Oracle write succeeds. An Oracle failure leaves WeCMS unchanged and is
     * reported as {@link Outcome#FAILED} (retried next run).
     */
    private Outcome pushRate(String bizNo, BankExchRateInfm rate, String nowVn) {
        String citadBankCd = CITAD_BY_ORIGINAL.get(rate.getBankCd());
        if (citadBankCd == null) {
            // Unmappable bank (outside the legacy-supported set) — skip rather than
            // push with a code the WeCMS send-flag write-back could not target.
            log.warn("feature=haengsung-exchange-rate-sync skip rate — bank not in CITAD map bankCd={}",
                    rate.getBankCd());
            return Outcome.SKIPPED;
        }

        OracleFcyExRate mapped = map(bizNo, citadBankCd, rate, nowVn);
        try {
            if (ERP_SND_DONE.equalsIgnoreCase(rate.getErpSndYn())) {
                fcyExRateOracle.update(mapped);     // already sent → refresh (U001)
            } else {
                fcyExRateOracle.insert(mapped);     // new → idempotent insert (C002)
            }
        } catch (RuntimeException ex) {
            // WeCMS row untouched; the next run re-attempts this row.
            log.warn("feature=haengsung-exchange-rate-sync Oracle write failed (WeCMS unchanged) "
                    + "bizNo={} bankCd={} ccyCd={} refDt={} refSeq={}",
                    bizNo, citadBankCd, mapped.ccyCd(), mapped.refDt(), mapped.refSeq(), ex);
            return Outcome.FAILED;
        }

        // Oracle write committed — mark the WeCMS row sent (its own transaction).
        bankExchRateInfmRepository.markErpSent(
                rate.getBizKey(), rate.getBankCd(), rate.getRefDate(), rate.getCcyCd(), rate.getExchSeqNo(),
                SYSTEM_USER, utcNow());
        return Outcome.PROCESSED;
    }

    /**
     * Map a WeCMS rate row to the Oracle {@code TB_FCY_EXRATE} shape — verbatim
     * legacy {@code ERP_0100_001.UpsertTbFcyExRateData} (see {@link OracleFcyExRate}
     * for the field-by-field source). {@code CCY_CD = REF_CCY_CD} (base currency)
     * is kept exactly as legacy maps it.
     */
    static OracleFcyExRate map(String bizNo, String citadBankCd, BankExchRateInfm r, String nowVn) {
        String refDt = r.getLastUpdateDate();
        String refTm = r.getLastUpdateTime();
        String lastInqDttm = (refDt == null ? "" : refDt) + (refTm == null ? "" : refTm);
        return new OracleFcyExRate(
                bizNo,
                citadBankCd,
                refDt,
                r.getRefCcyCd(),
                toNumber(r.getSeqNo()),
                refTm,
                r.getBuyAmt(),
                r.getSellAmt(),
                r.getTranSellAmt(),
                r.getTranBuyAmt(),
                r.getBasicRate(),
                lastInqDttm,
                nowVn);
    }

    private static BigDecimal toNumber(String seqNo) {
        if (seqNo == null || seqNo.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(seqNo.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String utcNow() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
    }

    /** Per-row push outcome. */
    enum Outcome { PROCESSED, SKIPPED, FAILED }

    /** Aggregated result of one {@link #sync()} pass across all HAENGSUNG companies. */
    public record Summary(int businesses, int okBusinesses, int failedBusinesses,
                          int processed, int skipped, int failed) {
        public static final Summary EMPTY = new Summary(0, 0, 0, 0, 0, 0);
    }
}
