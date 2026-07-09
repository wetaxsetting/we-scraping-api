package com.kosign.wecms.domain.payment.haengsung.cardaccount;

import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HAENGSUNG Oracle account-history + card-approval sync
 * ({@code haengsung-card-account-sync}, Step 7 — refactor of legacy
 * {@code ERP_0100_003}). Pushes WeCMS-collected account transaction history and
 * corporate-card approvals out to the HAENGSUNG Oracle ERP. Pure one-way push:
 * <pre>WeCMS PostgreSQL → HAENGSUNG Oracle (TB_ACCT_HISTORY / TB_CARD_APPROVAL)</pre>
 * Touches NO payment tables ({@code TB_TRNS_*}, {@code SCDUL_*}, {@code OVRS_*})
 * and creates no new banking/card business logic — it only reads existing WeCMS
 * data and mirrors it to Oracle.
 *
 * <p><b>Gating.</b> The bean exists only when {@code haengsung.oracle.enabled=true}
 * (Step-1 foundation) — it needs the secondary Oracle datasource. The scheduled
 * trigger ({@link HaengsungCardAccountSyncJob}) is additionally gated OFF by
 * default ({@code haengsung.card-account-sync.enabled=false}).
 *
 * <p><b>Flow (legacy {@code ERP_0100_003}).</b> Iterate every active HAENGSUNG
 * company ({@code PRDT_TYPE='H' AND DEL_YN='N'}). Per company, two sections:
 * <ol>
 *   <li><b>Account history</b> — read unsent {@code ACCT_TRNS_HSTR}
 *       ({@code hand_reg_yn='N'}, {@code erp_snd_yn='N'}); insert each into
 *       {@code TB_ACCT_HISTORY}; count VND vs FCY by {@code CCY_CD}; flag each row
 *       sent. Two audit rows ({@code TRNS_TYPE_CD='01'} VND / {@code '08'} FCY),
 *       even on 0.</li>
 *   <li><b>Card approval</b> — read unsent {@code CARD_TRNS_HSTR} joined
 *       {@code CARD_MSTR}; insert each into {@code TB_CARD_APPROVAL}; flag each row
 *       sent. One audit row ({@code TRNS_TYPE_CD='07'}), even on 0.</li>
 * </ol>
 * All audit rows are {@code ERP_TYPE='W'} (WeCMS→ERP).
 *
 * <p><b>No XA / retry.</b> Oracle and WeCMS are separate datasources. Per row the
 * Oracle insert commits on its own; only then is the WeCMS {@code ERP_SND_YN}
 * flipped. If the Oracle insert throws, the WeCMS row is left untouched, the error
 * is logged, and the loop continues — the row retries next run (the idempotent
 * {@code NOT EXISTS} insert makes an un-flipped flag a no-op, never a duplicate-key
 * error). One company / one section / one row failure never blocks the rest.
 *
 * <p><b>Divergence from legacy (documented).</b> Legacy flags ALL unsent rows by
 * {@code BIZ_KEY} in one bulk update inside the section transaction (all-or-nothing).
 * Without a cross-datasource (XA) transaction that is unsafe — a partial Oracle
 * push followed by a bulk flag would mark un-pushed rows as sent. The refactor
 * therefore flags <b>per row</b> after that row's Oracle insert succeeds (the
 * prompt's "continue if one row fails" requirement), which is strictly safer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class HaengsungCardAccountSyncService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DTM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ERP_TRNS_HSTR TRNS_TYPE_CD audit buckets (legacy ErpTypeCd).
    private static final String AUDIT_ACCT_VND = "01"; // TRANSACTION_HISTORY_VND
    private static final String AUDIT_ACCT_FCY = "08"; // TRANSACTION_HISTORY_FCY
    private static final String AUDIT_CARD     = "07"; // CORPORATE_CARD_TRANSACTIONS

    private final BusiInfmRepository busiInfmRepository;
    private final AcctTrnsHstrRepository acctTrnsHstrRepository;
    private final CardTrnsHstrRepository cardTrnsHstrRepository;
    private final HaengsungCardAccountOracleRepository oracle;
    private final ErpTrnsHistoryWriter auditWriter;

    /**
     * One sync pass over all active HAENGSUNG companies. Returns the aggregated
     * summary (also logged) so it is unit-testable without the scheduler/ShedLock
     * proxies. Never throws for a per-company / per-section / per-row failure —
     * those are caught, logged, and counted.
     */
    public Summary sync() {
        List<BusiInfm> businesses = busiInfmRepository.findByPrdtTypeAndDelYn("H", "N");
        String nowVn = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);

        log.info("feature=haengsung-card-account-sync start businesses={}", businesses.size());

        int acctVnd = 0, acctFcy = 0, acctFailed = 0, cardOk = 0, cardFailed = 0, failedBiz = 0;

        for (BusiInfm busi : businesses) {
            String bizKey = busi.getBizKey();
            String bizNo = busi.getTaxId();
            if (bizNo == null || bizNo.isBlank()) {
                log.warn("feature=haengsung-card-account-sync skip business — no TAX_ID bizKey={}", bizKey);
                failedBiz++;
                continue;
            }

            // ---- Section 1: account history ----
            int bizVnd = 0, bizFcy = 0;
            try {
                for (AcctHistorySyncRow row : acctTrnsHstrRepository.findHaengsungAcctHistoryForSync(bizKey)) {
                    if (pushAcct(bizKey, bizNo, row, nowVn)) {
                        if ("VND".equals(row.getCcyCd())) { bizVnd++; } else { bizFcy++; }
                    } else {
                        acctFailed++;
                    }
                }
            } catch (RuntimeException ex) {
                // bizKey is not a credential; Oracle URL/user/password are never logged.
                log.warn("feature=haengsung-card-account-sync account section failed bizKey={}", bizKey, ex);
            }
            acctVnd += bizVnd;
            acctFcy += bizFcy;
            // Audit rows per company (legacy parity — written even on 0 records).
            auditWriter.recordBatchPush(bizKey, AUDIT_ACCT_VND, auditLabel("Account_History_VND", nowVn, bizVnd));
            auditWriter.recordBatchPush(bizKey, AUDIT_ACCT_FCY, auditLabel("Account_History_FCY", nowVn, bizFcy));

            // ---- Section 2: card approval ----
            int bizCard = 0;
            try {
                for (CardApprovalSyncRow row : cardTrnsHstrRepository.findHaengsungCardApprovalForSync(bizKey)) {
                    if (pushCard(bizKey, bizNo, row, nowVn)) { bizCard++; } else { cardFailed++; }
                }
            } catch (RuntimeException ex) {
                log.warn("feature=haengsung-card-account-sync card section failed bizKey={}", bizKey, ex);
            }
            cardOk += bizCard;
            auditWriter.recordBatchPush(bizKey, AUDIT_CARD, auditLabel("Card_Transactions", nowVn, bizCard));
        }

        Summary summary = new Summary(businesses.size(), failedBiz,
                acctVnd, acctFcy, acctFailed, cardOk, cardFailed);
        log.info("feature=haengsung-card-account-sync done businesses={} failedBusinesses={} "
                        + "acctVnd={} acctFcy={} acctFailed={} cardOk={} cardFailed={}",
                summary.businesses(), summary.failedBusinesses(),
                summary.acctVnd(), summary.acctFcy(), summary.acctFailed(),
                summary.cardOk(), summary.cardFailed());
        return summary;
    }

    /**
     * Push one account-history row to Oracle and, on success, flip its WeCMS
     * send-flag. No XA: the WeCMS flag is updated only after the Oracle insert
     * succeeds. Returns {@code true} when the Oracle insert did not throw (row
     * counted + flagged), {@code false} on Oracle failure (WeCMS unchanged, retried
     * next run).
     */
    private boolean pushAcct(String bizKey, String bizNo, AcctHistorySyncRow row, String nowVn) {
        OracleAcctHistory mapped = new OracleAcctHistory(
                bizNo,
                row.getBankCd(),
                row.getAcctNo(),
                row.getTrDt(),
                toNumber(row.getTrSeqno()),
                row.getCcyCd(),
                row.getTrAmt(),
                row.getTrCurBal(),
                row.getIoGb(),
                row.getAcctMemo(),
                row.getLastInqDttm(),
                nowVn);
        try {
            oracle.insertAcctHistory(mapped);
        } catch (RuntimeException ex) {
            log.warn("feature=haengsung-card-account-sync account Oracle insert failed (WeCMS unchanged) "
                    + "bizKey={} acctSeqNo={}", bizKey, row.getTrSeqno(), ex);
            return false;
        }
        acctTrnsHstrRepository.markErpSent(bizKey, row.getTrSeqno());
        return true;
    }

    /**
     * Push one card-approval row to Oracle and, on success, flip its WeCMS
     * send-flag. Same no-XA contract as {@link #pushAcct}.
     */
    private boolean pushCard(String bizKey, String bizNo, CardApprovalSyncRow row, String nowVn) {
        OracleCardApproval mapped = new OracleCardApproval(
                bizNo,
                row.getBankCd(),
                row.getCardNo(),
                row.getApprDt(),
                row.getApprTm(),
                row.getAuthNo(),
                row.getCardHldrNm(),   // legacy override: CARD_NM = CARD_HLDR_NM
                row.getMerchNm(),
                row.getCcyCd(),
                row.getApprAmt(),
                row.getPrchStts(),     // PURCHASE_ST
                row.getCity(),
                row.getLastInqDttm(),
                nowVn);
        try {
            oracle.insertCardApproval(mapped);
        } catch (RuntimeException ex) {
            log.warn("feature=haengsung-card-account-sync card Oracle insert failed (WeCMS unchanged) "
                    + "bizKey={} cardSeqNo={}", bizKey, row.getCardSeqno(), ex);
            return false;
        }
        cardTrnsHstrRepository.markErpSent(bizKey, row.getCardSeqno());
        return true;
    }

    private static String auditLabel(String section, String nowVn, int count) {
        return "Batch_WeCMS_to_ERP_" + section + "_" + nowVn
                + (count == 0 ? "_NO_DATA" : "_" + count + "_records");
    }

    private static BigDecimal toNumber(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Aggregated result of one {@link #sync()} pass across all HAENGSUNG companies. */
    public record Summary(int businesses, int failedBusinesses,
                          int acctVnd, int acctFcy, int acctFailed,
                          int cardOk, int cardFailed) {
        public static final Summary EMPTY = new Summary(0, 0, 0, 0, 0, 0, 0);
    }
}
