package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.nxisas.NxisasBankConfig;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient.GatewayContinuation;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsResponse.CardSyncError;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstr;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardMstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Payment &gt; Card Transactions &mdash; Update / Sync orchestrator.
 * Mirrors legacy {@code nxisas_cardTranList.ts} ("Real-Time Online
 * Inquire" popup), collapsed into a single backend pass instead of a
 * client-driven per-credential loop.
 *
 * <h2>Flow</h2>
 *
 * <ol>
 *   <li>Load every active card the tenant owns that has a registered
 *       {@code BANK_CERT_INFO} ({@code CARD_MSTR.BANK_CERT_SEQ IS NOT NULL}
 *       and {@code ACTV_YN = 'Y'}).</li>
 *   <li>Group by credential ({@code BANK_CERT_SEQ}).</li>
 *   <li>For each credential, run one Coocon LOGIN. On success run
 *       {@code CARD_USING(cardNum, cardType, startDate, endDate)} for
 *       every card in the group. On challenge response (OTP / QNA /
 *       CAPTCHA) record a per-card {@code WBE_CARD_SYNC_NEEDS_RE_AUTH}
 *       failure and move on &mdash; the user finishes the challenge via
 *       the Manage Cards Add-Card flow's re-auth path (Phase B follow-up
 *       can wire a dedicated re-auth dialog if needed).</li>
 *   <li>For each {@code CARD_USING} success, insert resulting rows
 *       into {@code CARD_TRNS_HSTR} with dedup on the (BANK_CD, CARD_NO,
 *       TRSC_DT, AUTH_CODE) natural key (mirrors legacy
 *       {@code IDO.CARD_TRNS_HSTR_CHK}).</li>
 * </ol>
 *
 * <h2>Concurrency / failure</h2>
 *
 * <p>Per-credential failures don't abort the run &mdash; the
 * orchestrator collects errors per card and returns them in the
 * response so the user can see what worked and what needs re-auth.
 * A single {@code DataIntegrityViolationException} on insert (rare;
 * concurrent sync overlap) skips that one row + counts it as skipped.
 *
 * <p>Tenant scope: {@link TenantContext#currentBizKey()} is the only
 * authoritative tenant source. The request body never carries one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardTransactionSyncService {

    private static final String WSVC_ID                = "card-transactions";
    private static final String REF_NO_CARD_TRNS_HSTR  = "CARD_TRNS_HSTR";
    private static final String AUDIT_ACTIVITY_KEY     = "audit.card_transactions.activity";
    private static final String AUDIT_SYNC_DETAIL_KEY  = "audit.card_transactions.sync.detail";

    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Special-case bank code mirrored from legacy
     *  {@code nxisas_cardTranList.ts:198-211}: Woori Vietnam ({@code 11704020})
     *  sends CARD_USING without a {@code cardType} sub-discriminator;
     *  every other bank sends {@code "0"} as the default. */
    private static final String WOORI_VN_BANK_CD = "11704020";

    private final CardMstrRepository       cardMstrRepo;
    private final CardTrnsHstrRepository   cardTrnsRepo;
    private final BankInfmRepository       bankInfmRepo;
    private final BankCredentialService    credentialService;
    private final NxisasGatewayClient      gateway;
    private final NxisasBankConfig         bankConfig;
    private final LegacyFunctionRepository legacyFns;
    private final AuditLogService          auditLogService;

    /* ------------------------------------------------------------------
     *  Public entry — POST /api/v1/payment/card-transactions/sync.
     * ---------------------------------------------------------------- */

    @Transactional
    public SyncCardTransactionsResponse sync(SyncCardTransactionsRequest req) {
        if (req == null) throw new BusinessException("WBE_VALIDATION");
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE_VALIDATION");
        }
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(ZoneOffset.UTC).format(DTM14);

        // 1. Load active cards that have a saved credential.
        List<CardMstr> cards = cardMstrRepo.findActiveCardsForSync(bizKey);
        if (cards.isEmpty()) {
            return new SyncCardTransactionsResponse(0, 0, 0, 0, List.of());
        }

        // 2a. Resolve the effective date range. Auto mode derives the
        //     start from min(TRN_LST_SUC_DTM) across the eligible cards
        //     (or today − 90d when no card has ever been synced), and
        //     pins the end to today. Mirrors legacy
        //     nxisas_cardTranList getInitStartDate + the popup's
        //     "from latest updated date to today" copy.
        String fromDt = req.fromDt();
        String toDt   = req.toDt();
        if (Boolean.TRUE.equals(req.auto())) {
            String today = now.substring(0, 8);
            String minLastSuc = cards.stream()
                    .map(CardMstr::getTrnLstSucDtm)
                    .filter(s -> s != null && s.length() >= 8)
                    .map(s -> s.substring(0, 8))
                    .min(String::compareTo)
                    .orElse(null);
            fromDt = minLastSuc != null ? minLastSuc : minus90DaysYyyymmdd(today);
            toDt   = today;
        } else if (fromDt.compareTo(toDt) > 0) {
            throw new BusinessException("WBE_CARD_SYNC_BAD_RANGE");
        }
        // Effective range — locals from here on. Two new final vars so
        // the closures below capture the post-auto values.
        final String effFromDt = fromDt;
        final String effToDt   = toDt;

        // 2. Group cards by BANK_CERT_SEQ (one credential row may
        //    cover several cards at the same bank).
        Map<BigDecimal, List<CardMstr>> byCredential = new LinkedHashMap<>();
        for (CardMstr c : cards) {
            byCredential.computeIfAbsent(c.getBankCertSeq(), k -> new ArrayList<>()).add(c);
        }

        int cardsAttempted = cards.size();
        int cardsSucceeded = 0;
        int insertedCount  = 0;
        int skippedCount   = 0;
        List<CardSyncError> errors = new ArrayList<>();

        for (Map.Entry<BigDecimal, List<CardMstr>> entry : byCredential.entrySet()) {
            BigDecimal credSeqNo = entry.getKey();
            List<CardMstr> group = entry.getValue();

            // 3a. Decrypt credentials. Failure (missing row, wrong
            //     tenant, etc.) → mark every card in this group as
            //     "no credential" and skip.
            DecryptedCredential cred;
            try {
                cred = credentialService.loadForScraping(credSeqNo);
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                for (CardMstr c : group) {
                    errors.add(new CardSyncError(
                            c.getBankCd(),
                            resolveBankName(c.getBankCd()),
                            c.getCardNo(),
                            "WBE_CARD_SYNC_NO_CREDENTIAL",
                            msg));
                }
                continue;
            }

            // 3b. Resolve Coocon Module / Class for the bank + sub-type.
            String bankCd = group.get(0).getBankCd();
            Integer subSeq = cred.nxisasGbSeqNo() == null ? 0 : cred.nxisasGbSeqNo().intValue();
            NxisasBankConfig.Entry cfg = bankConfig.find(bankCd, subSeq).orElse(null);
            if (cfg == null) {
                for (CardMstr c : group) {
                    errors.add(new CardSyncError(
                            c.getBankCd(),
                            resolveBankName(c.getBankCd()),
                            c.getCardNo(),
                            "WBE_CARD_SYNC_UNSUPPORTED_BANK",
                            "no NXISAS module mapped for bank " + bankCd));
                }
                continue;
            }

            // 3c. LOGIN.
            String trSeq = gateway.newTrSeq();
            NxisasLoginJobInput loginInput = new NxisasLoginJobInput();
            loginInput.setLoginId(cred.username());
            loginInput.setLoginPwd(cred.password());
            loginInput.setLoginType(
                    cred.loginType() == null || cred.loginType().isBlank()
                            ? (WOORI_VN_BANK_CD.equals(bankCd)
                                    ? ""    // Woori Vietnam quirk per nxisas_v2.js:947
                                    : "ID")
                            : cred.loginType());
            loginInput.setCountryCode("VNM");
            if (cred.corporateId() != null && !cred.corporateId().isBlank()) {
                loginInput.setCorporateId(cred.corporateId());
                loginInput.setLoginPwd1(cred.corporateId());
            }

            NxisasApiOut<NxisasLoginJobResult> loginOut =
                    gateway.login(cfg.module(), cfg.category(), trSeq, loginInput);
            String loginResultCd = loginOut == null || loginOut.getCommon() == null
                    ? null : loginOut.getCommon().getResultCd();
            if (isGatewayError(loginResultCd)) {
                String msg = loginOut == null || loginOut.getCommon() == null
                        ? "Coocon LOGIN failed (no envelope)"
                        : loginOut.getCommon().getResultMsg();
                for (CardMstr c : group) {
                    errors.add(new CardSyncError(
                            c.getBankCd(), resolveBankName(c.getBankCd()), c.getCardNo(),
                            "WBE_CARD_SYNC_LOGIN_FAILED", msg));
                }
                continue;
            }
            NxisasLoginJobResult loginRes = gateway.firstResult(loginOut);
            if (loginRes != null && hasChallenge(loginRes)) {
                // OTP / QNA / CAPTCHA — sync flow doesn't pump
                // challenges; surface a re-auth notice per card.
                for (CardMstr c : group) {
                    errors.add(new CardSyncError(
                            c.getBankCd(), resolveBankName(c.getBankCd()), c.getCardNo(),
                            "WBE_CARD_SYNC_NEEDS_RE_AUTH",
                            "bank requires OTP / Q&A / CAPTCHA — finish via Manage Cards"));
                }
                continue;
            }

            GatewayContinuation cont = GatewayContinuation.from(loginOut);

            // 3d. CARD_USING per card.
            for (CardMstr card : group) {
                NxisasCardUsingJobInput cardInput = new NxisasCardUsingJobInput();
                cardInput.setCardNum(card.getCardNo());
                cardInput.setStartDate(effFromDt);
                cardInput.setEndDate(effToDt);
                // Woori Vietnam doesn't take a cardType discriminator.
                if (!WOORI_VN_BANK_CD.equals(card.getBankCd())) {
                    cardInput.setCardType("0");
                }
                String useTrSeq = gateway.newTrSeq();
                NxisasApiOut<NxisasCardUsingJobResult> useOut;
                try {
                    useOut = gateway.cardUsing(
                            cfg.module(), cfg.category(), useTrSeq, cardInput, cont);
                } catch (Exception ex) {
                    errors.add(new CardSyncError(
                            card.getBankCd(), resolveBankName(card.getBankCd()), card.getCardNo(),
                            "WBE_CARD_SYNC_GATEWAY_ERROR",
                            ex.getMessage() == null ? "gateway call failed" : ex.getMessage()));
                    continue;
                }
                String useResultCd = useOut == null || useOut.getCommon() == null
                        ? null : useOut.getCommon().getResultCd();
                if (isGatewayError(useResultCd)) {
                    String msg = useOut == null || useOut.getCommon() == null
                            ? "Coocon CARD_USING failed (no envelope)"
                            : useOut.getCommon().getResultMsg();
                    errors.add(new CardSyncError(
                            card.getBankCd(), resolveBankName(card.getBankCd()), card.getCardNo(),
                            "WBE_CARD_SYNC_GATEWAY_ERROR", msg));
                    continue;
                }
                NxisasCardUsingJobResult useRes = gateway.firstCardUsingResult(useOut);
                List<NxisasCardUsingEntry> scraped =
                        useRes == null || useRes.getTranList() == null
                                ? List.of()
                                : useRes.getTranList();

                // 3e. Insert with dedup. One inserted row per
                //     scraped transaction that doesn't already exist;
                //     dedup hit → skippedCount++ instead.
                int[] counts = persistScrapedRows(
                        bizKey, userId, now, card, scraped);
                insertedCount += counts[0];
                skippedCount  += counts[1];

                // Stamp the last-success on CARD_MSTR so subsequent
                // syncs default to the post-success window.
                card.setTrnLstSucDtm(now);
                card.setModId(userId);
                card.setModDtm(now);
                cardMstrRepo.save(card);

                cardsSucceeded++;
            }
        }

        // 4. Audit.
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(WSVC_ID)
                .refNo(REF_NO_CARD_TRNS_HSTR)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_SYNC_DETAIL_KEY)
                .msgArgs(new Object[] {
                        cardsAttempted, cardsSucceeded, insertedCount, skippedCount,
                })
                .build());

        log.info("feature=card-transactions sync bizKey={} cards={}/{} inserted={} skipped={} errors={}",
                bizKey, cardsSucceeded, cardsAttempted, insertedCount, skippedCount, errors.size());

        return new SyncCardTransactionsResponse(
                cardsAttempted, cardsSucceeded, insertedCount, skippedCount, errors);
    }

    /* ------------------------------------------------------------------
     *  Insert + dedup
     * ---------------------------------------------------------------- */

    /** Returns {@code [inserted, skipped]}. */
    private int[] persistScrapedRows(String bizKey, String userId, String now,
                                     CardMstr card,
                                     List<NxisasCardUsingEntry> scraped) {
        int inserted = 0;
        int skipped  = 0;
        for (NxisasCardUsingEntry tx : scraped) {
            String trscDt = normYyyymmdd(tx.getTranDate());
            if (trscDt == null) {
                // Tx without a date is unusable — Coocon shouldn't
                // emit this but tolerate it to avoid breaking the
                // batch on one bad row.
                continue;
            }
            String authCode = blankToNull(tx.getAuthCode());
            String trscTm   = normHhmmss(tx.getTranTime());
            BigDecimal amt  = parseAmount(tx.getAmount());

            // Dedup probe — auth code first (Coocon-stable), then
            // wider composite when auth code is absent.
            boolean dup;
            if (authCode != null) {
                dup = cardTrnsRepo
                        .existsByBizKeyAndBankCdAndCardNoAndTrscDtAndAuthCode(
                                bizKey, card.getBankCd(), card.getCardNo(), trscDt, authCode);
            } else {
                dup = cardTrnsRepo
                        .existsByBizKeyAndBankCdAndCardNoAndTrscDtAndTrscTmAndTrscAmt(
                                bizKey, card.getBankCd(), card.getCardNo(), trscDt,
                                trscTm == null ? "" : trscTm,
                                amt == null ? BigDecimal.ZERO : amt);
            }
            if (dup) {
                skipped++;
                continue;
            }

            String seqNo = legacyFns.nextCardSeqNo(bizKey);
            CardTrnsHstr row = new CardTrnsHstr();
            row.setBizKey(bizKey);
            row.setCardSeqNo(seqNo);
            row.setBankCd(card.getBankCd());
            row.setCardNo(card.getCardNo());
            row.setTrscDt(trscDt);
            row.setTrscTm(trscTm);
            row.setCardNm(card.getCardNm());
            row.setMerchNm(truncate(tx.getMerchant(), 50));
            row.setCcyCd(normCcy(tx.getCurrency()));
            row.setInoutDv(normInoutDv(tx.getInoutDv()));
            row.setTrscAmt(amt);
            row.setUsingType(truncate(tx.getUsingType(), 50));
            row.setPrchStts(truncate(tx.getPrchStts(), 50));
            row.setNote(truncate(tx.getNote(), 255));
            row.setAuthCode(truncate(authCode, 15));
            row.setCity(truncate(tx.getCity(), 100));
            row.setRegId(userId);
            row.setRegDtm(now);
            row.setModId(userId);
            row.setModDtm(now);
            row.setErpSndYn("N");

            try {
                cardTrnsRepo.save(row);
                inserted++;
            } catch (DataIntegrityViolationException ex) {
                // Concurrent sync collision — count as skip and move
                // on. Mirrors the appr_line_no / chart_acct_seq_no
                // retry-once discipline; here we just skip because
                // duplicate detection should already have caught it.
                skipped++;
            }
        }
        return new int[] { inserted, skipped };
    }

    /* ------------------------------------------------------------------
     *  Small helpers
     * ---------------------------------------------------------------- */

    private String resolveBankName(String bankCd) {
        return bankInfmRepo.findById(bankCd)
                .map(b -> b.getRprsBankNm() == null ? bankCd : b.getRprsBankNm())
                .orElse(bankCd);
    }

    private static boolean isGatewayError(String resultCd) {
        if (resultCd == null || resultCd.isBlank()) return false;
        if ("00000000".equals(resultCd)) return false;
        if ("0000".equals(resultCd)) return false;
        if ("OK".equalsIgnoreCase(resultCd)) return false;
        return true;
    }

    /** Detect a challenge-required LOGIN response shape. Coocon
     *  signals "next step needed" via the {@code CallBackfunc} field —
     *  e.g. {@code "setOTP"}, {@code "setCaptcha"}, {@code "setQNA"}.
     *  When it's null AND {@code req} is null the login is complete
     *  and the next job (CARD_USING) can dispatch immediately. */
    private static boolean hasChallenge(NxisasLoginJobResult res) {
        if (res == null) return false;
        if (res.getCallBackFunc() != null && !res.getCallBackFunc().isBlank()) return true;
        // Defensive: some adapters omit CallBackfunc but include a
        // populated req on the LOGIN result when a challenge is needed.
        return res.getReq() != null && !res.getReq().isNull();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String normYyyymmdd(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D+", "");
        if (digits.length() < 8) return null;
        return digits.substring(0, 8);
    }

    private static String normHhmmss(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D+", "");
        if (digits.isEmpty()) return null;
        return digits.length() > 6 ? digits.substring(0, 6) : digits;
    }

    private static String normCcy(String raw) {
        if (raw == null || raw.isBlank()) return "VND";
        String u = raw.trim().toUpperCase();
        return u.length() > 3 ? u.substring(0, 3) : u;
    }

    private static String normInoutDv(String raw) {
        if (raw == null || raw.isBlank()) return "2";
        String s = raw.trim();
        if (s.equals("1") || s.equals("2") || s.equals("0")) return s;
        // Coocon variants — IN / OUT / +/- amount, etc.
        String u = s.toUpperCase();
        if (u.contains("IN") || u.startsWith("+")) return "1";
        if (u.contains("OUT") || u.startsWith("-")) return "2";
        return "2";
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // Strip thousand separators / currency symbols.
            String cleaned = raw.replaceAll("[^0-9.\\-]+", "");
            return cleaned.isEmpty() ? null : new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Fallback default for Auto mode when no card has ever been
     *  synced &mdash; mirrors legacy nxisas_cardTranList's
     *  {@code _limitPeriodCnt = 90 days} default for first-time
     *  scrapes. */
    private static String minus90DaysYyyymmdd(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() < 8) return yyyymmdd;
        java.time.LocalDate d = java.time.LocalDate.of(
                Integer.parseInt(yyyymmdd.substring(0, 4)),
                Integer.parseInt(yyyymmdd.substring(4, 6)),
                Integer.parseInt(yyyymmdd.substring(6, 8))).minusDays(90);
        return String.format("%04d%02d%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    @SuppressWarnings("unused") // future last-update window helper
    private static Optional<String> latestLastSuccessDtm(List<CardMstr> cards) {
        return cards.stream()
                .map(CardMstr::getTrnLstSucDtm)
                .filter(s -> s != null && !s.isBlank())
                .max(String::compareTo);
    }
}
