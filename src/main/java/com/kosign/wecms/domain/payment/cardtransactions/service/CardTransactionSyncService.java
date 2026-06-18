package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.nxisas.NxisasApiLog;
import com.kosign.wecms.domain.banking.nxisas.NxisasBankConfig;
import com.kosign.wecms.domain.banking.nxisas.NxisasCardBanks;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient.GatewayContinuation;
import com.kosign.wecms.domain.banking.nxisas.NxisasMask;
import com.kosign.wecms.domain.banking.nxisas.NxisasResultCodes;
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
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsResponse.SyncGroup;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsResponse.SyncGroupCard;
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

    /** Shinhan Vietnam bank code — its CARD_USING response returns
     *  Vietnamese purchase-status TEXT instead of a short code, so the
     *  service maps the strings to single-digit codes before persist
     *  per legacy
     *  {@code fnsh_0001_001_c001_act.jsp:1378-1402}. */
    private static final String SHINHAN_VN_BANK_CD = "11704088";

    private final CardMstrRepository       cardMstrRepo;
    private final CardTrnsHstrRepository   cardTrnsRepo;
    private final BankInfmRepository       bankInfmRepo;
    private final BankCertInfoRepository   bankCertInfoRepo;
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
        long startedNs = System.nanoTime();

        // Shared NXISAS debug trace id for this Update/sync run. Card sync
        // has no NxisasScrapeSession (challenges aren't pumped — they
        // surface as re-auth notices), so we mint a per-run id and use it
        // across every NxisasApiLog line for the run. flow = CARD_UPDATE_TRANSACTIONS.
        final String traceId = java.util.UUID.randomUUID().toString();
        final String FLOW = NxisasApiLog.FLOW_CARD_UPDATE_TRANSACTIONS;

        log.info("---------------------------- card-sync :: START ----------------------------");
        log.info("card-sync :: bizKey={} from={} to={} auto={}",
                bizKey, req.fromDate(), req.toDate(), req.auto());
        log.info("---------------------------- card-sync :: START ----------------------------");

        // 1. Load active cards that have a saved credential.
        List<CardMstr> cards = cardMstrRepo.findActiveCardsForSync(bizKey);
        log.info("card-sync :: loaded {} eligible card(s) (active + bank_cert_seq present)",
                cards.size());
        if (cards.isEmpty()) {
            log.info("---------------------------- card-sync :: DONE :: no eligible cards ({} ms) ----------------------------",
                    (System.nanoTime() - startedNs) / 1_000_000);
            NxisasApiLog.event(traceId, FLOW, "COMPLETE",
                    "success", 0, "attempted", 0, "reason", "NO_ELIGIBLE_CARDS");
            return new SyncCardTransactionsResponse(0, 0, 0, 0, List.of(), List.of());
        }

        // 2a. Resolve the effective date range. Auto mode derives the
        //     start from min(TRN_LST_SUC_DTM) across the eligible cards
        //     (or today − 90d when no card has ever been synced), and
        //     pins the end to today. Mirrors legacy
        //     nxisas_cardTranList getInitStartDate + the popup's
        //     "from latest updated date to today" copy.
        String fromDt = req.fromDate();
        String toDt   = req.toDate();
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
            log.info("card-sync :: auto mode resolved range from={} to={} (min_last_suc={})",
                    fromDt, toDt, minLastSuc);
        } else if (fromDt.compareTo(toDt) > 0) {
            throw new BusinessException("WBE_CARD_SYNC_BAD_RANGE");
        }
        // Effective range — locals from here on. Two new final vars so
        // the closures below capture the post-auto values.
        final String effFromDt = fromDt;
        final String effToDt   = toDt;

        // 2a. Auto-resolve BANK_CERT_SEQ for orphan cards. The
        //     original Add Card flow inserted cards with bank_cert_seq
        //     NULL (the scrape-session response didn't surface the
        //     SEQ_NO back to the frontend, so the create request
        //     omitted the field). Without this fallback, every
        //     orphan card would be excluded from the sync (legacy
        //     parity: legacy popup never had this problem because
        //     it held the cert in client memory throughout the
        //     Add Card flow).
        //
        //     For each NULL row, look up the most-recent
        //     BANK_CERT_INFO for (biz_key, bank_cd) and stamp it
        //     on the entity. The entity stays managed for the rest
        //     of the @Transactional method, so the flush at commit
        //     time persists the link — the card is un-orphaned
        //     permanently after the first successful Update click.
        //
        //     Cards whose bank has zero BANK_CERT_INFO rows at all
        //     stay NULL here; the per-cert loop below surfaces them
        //     as WBE_CARD_SYNC_NO_CREDENTIAL.
        List<CardMstr> unresolvableCards = new ArrayList<>();
        List<CardMstr> resolvableCards   = new ArrayList<>();
        for (CardMstr c : cards) {
            if (c.getBankCertSeq() == null) {
                BigDecimal resolved = bankCertInfoRepo
                        .findFirstByBizKeyAndBankCdOrderBySeqNoAsc(bizKey, c.getBankCd())
                        .map(x -> x.getSeqNo())
                        .orElse(null);
                if (resolved == null) {
                    log.warn("card-sync :: orphan card has NO BANK_CERT_INFO for bankCd={} cardNo={} — surfacing as WBE_CARD_SYNC_NO_CREDENTIAL",
                            c.getBankCd(), NxisasMask.card(c.getCardNo()));
                    unresolvableCards.add(c);
                    continue;
                }
                c.setBankCertSeq(resolved);
                c.setModId(userId);
                c.setModDtm(now);
                cardMstrRepo.saveAndFlush(c);
                log.info("card-sync :: auto-linked orphan card bankCd={} cardNo={} → bankCertSeq={} (persisting on flush)",
                        c.getBankCd(), NxisasMask.card(c.getCardNo()), resolved);
            }
            resolvableCards.add(c);
        }

        // 2b. Group cards by BANK_CERT_SEQ (one credential row may
        //     cover several cards at the same bank).
        Map<BigDecimal, List<CardMstr>> byCredential = new LinkedHashMap<>();
        for (CardMstr c : resolvableCards) {
            byCredential.computeIfAbsent(c.getBankCertSeq(), k -> new ArrayList<>()).add(c);
        }

        int cardsAttempted = cards.size();
        int cardsSucceeded = 0;
        int insertedCount  = 0;
        int skippedCount   = 0;
        List<CardSyncError> errors = new ArrayList<>();

        // Surface unresolvable cards as per-card errors so the
        // response reads consistently — every attempted card gets
        // a row in either `groups[].cards[]` or `errors[]`.
        for (CardMstr c : unresolvableCards) {
            errors.add(new CardSyncError(
                    c.getBankCd(), resolveBankName(c.getBankCd()), c.getCardNo(),
                    "WBE_CARD_SYNC_NO_CREDENTIAL",
                    "No BANK_CERT_INFO row exists for this bank. Register the credential via Login Credentials first."));
        }
        // Per-cert breakdown so the frontend can render the legacy
        // `---- orgObj ----` / `---- tranList Result(Unit) ----`
        // console blocks. One entry per BANK_CERT_SEQ bucket.
        List<SyncGroup> groups = new ArrayList<>();

        log.info("---------------------------- card-sync :: orgObj ----------------------------");
        log.info("card-sync :: grouped into {} credential bucket(s)", byCredential.size());
        NxisasApiLog.event(traceId, FLOW, "START",
                "cards", cards.size(), "groups", byCredential.size(),
                "from", effFromDt, "to", effToDt);

        for (Map.Entry<BigDecimal, List<CardMstr>> entry : byCredential.entrySet()) {
            BigDecimal credSeqNo = entry.getKey();
            List<CardMstr> group = entry.getValue();
            // Per-card outcome accumulator for this cert. Seeded with
            // every card in the bucket so the response carries a row
            // per card even when LOGIN fails (legacy parity — every
            // card surfaces its own error).
            List<SyncGroupCard> groupCards = new ArrayList<>();
            String groupBankCd = group.get(0).getBankCd();
            String groupBankNm = resolveBankName(groupBankCd);

            log.info("---------------------------- card-sync :: credential ----------------------------");
            log.info("card-sync :: credential seq_no={} bankCd={} bankNm={} cards={}",
                    credSeqNo, groupBankCd, groupBankNm, group.size());
            for (CardMstr c : group) {
                log.info("card-sync ::   card_no={} card_nm={}", NxisasMask.card(c.getCardNo()), c.getCardNm());
            }
            log.info("---------------------------- card-sync :: credential ----------------------------");
            // Card scraping supports only Shinhan + Woori — tag the flow so
            // the logs make the two-bank scope explicit and an out-of-scope
            // bank (only possible via a stale/hand-seeded card row) is
            // visible rather than silently treated as a card bank.
            boolean cardBankSupported = NxisasCardBanks.isSupported(groupBankCd);
            NxisasApiLog.event(traceId, FLOW, "GROUP_START",
                    "bankCd", groupBankCd, "cardBank", NxisasCardBanks.label(groupBankCd),
                    "cardBankSupported", cardBankSupported, "cards", group.size());
            if (!cardBankSupported) {
                log.warn("card-sync :: UNSUPPORTED card bank in scrape scope bankCd={} (only Shinhan/Woori supported)",
                        groupBankCd);
                NxisasApiLog.event(traceId, FLOW, "UNSUPPORTED_CARD_BANK", "bankCd", groupBankCd);
            }

            // 3a. Decrypt credentials. Failure (missing row, wrong
            //     tenant, etc.) → mark every card in this group as
            //     "no credential" and skip.
            DecryptedCredential cred;
            try {
                cred = credentialService.loadForScraping(credSeqNo);
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                log.warn("card-sync :: credential decrypt FAILED seq_no={} msg={}", credSeqNo, msg);
                for (CardMstr c : group) {
                    errors.add(new CardSyncError(
                            c.getBankCd(),
                            resolveBankName(c.getBankCd()),
                            c.getCardNo(),
                            "WBE_CARD_SYNC_NO_CREDENTIAL",
                            msg));
                    groupCards.add(new SyncGroupCard(
                            c.getCardNo(), 0, 0, 0,
                            "WBE_CARD_SYNC_NO_CREDENTIAL", msg));
                }
                groups.add(new SyncGroup(
                        groupBankCd, groupBankNm,
                        credSeqNo == null ? null : credSeqNo.toPlainString(),
                        false, "WBE_CARD_SYNC_NO_CREDENTIAL", msg,
                        groupCards));
                continue;
            }

            // 3b. Resolve Coocon Module / Class for the bank + sub-type.
            String bankCd = group.get(0).getBankCd();
            Integer subSeq = cred.nxisasGbSeqNo() == null ? 0 : cred.nxisasGbSeqNo().intValue();
            NxisasBankConfig.Entry cfg = bankConfig.find(bankCd, subSeq).orElse(null);
            if (cfg == null) {
                String msg = "no NXISAS module mapped for bank " + bankCd;
                for (CardMstr c : group) {
                    errors.add(new CardSyncError(
                            c.getBankCd(),
                            resolveBankName(c.getBankCd()),
                            c.getCardNo(),
                            "WBE_CARD_SYNC_UNSUPPORTED_BANK",
                            msg));
                    groupCards.add(new SyncGroupCard(
                            c.getCardNo(), 0, 0, 0,
                            "WBE_CARD_SYNC_UNSUPPORTED_BANK", msg));
                }
                groups.add(new SyncGroup(
                        groupBankCd, groupBankNm,
                        credSeqNo == null ? null : credSeqNo.toPlainString(),
                        false, "WBE_CARD_SYNC_UNSUPPORTED_BANK", msg,
                        groupCards));
                continue;
            }

            // 3c. LOGIN.
            String trSeq = gateway.newTrSeq();
            NxisasLoginJobInput loginInput = new NxisasLoginJobInput();
            loginInput.setLoginId(cred.username());
            loginInput.setLoginPwd(cred.password());
            // Per-bank loginType mirroring legacy nxisas_v2.js:944-963.
            // Default = 'ID' for every bank, with three documented
            // overrides:
            //   - SCBank (11704104) → 'S2BAPP_OTP'
            //   - Vietcombank personal (11704004 + nxisasGbSeqNo=2) → from cred
            //   - ABBank (11704106) → '' (legacy test override at :962
            //     overrides the cred-supplied value with empty)
            // No Woori Vietnam (11704020) override — it uses the
            // default 'ID' like every other VN bank. The earlier
            // refactor comment claiming a "Woori VN quirk" was wrong.
            loginInput.setLoginType(resolveLoginType(bankCd, subSeq, cred.loginType()));
            loginInput.setCountryCode("VNM");
            if (cred.corporateId() != null && !cred.corporateId().isBlank()) {
                loginInput.setCorporateId(cred.corporateId());
                loginInput.setLoginPwd1(cred.corporateId());
            }

            log.info("card-sync :: LOGIN bankCd={} module={} class={} loginType={}",
                    bankCd, cfg.module(), cfg.category(),
                    loginInput.getLoginType() == null ? "" : loginInput.getLoginType());
            NxisasApiLog.event(traceId, FLOW, "LOGIN_START",
                    "bankCd", bankCd, "module", cfg.module(), "class", cfg.category());

            NxisasApiOut<NxisasLoginJobResult> loginOut =
                    gateway.login(cfg.module(), cfg.category(), trSeq, loginInput);
            String loginResultCd = loginOut == null || loginOut.getCommon() == null
                    ? null : loginOut.getCommon().getResultCd();
            if (isGatewayError(loginResultCd)) {
                String msg = loginOut == null || loginOut.getCommon() == null
                        ? "Coocon LOGIN failed (no envelope)"
                        : loginOut.getCommon().getResultMsg();
                log.warn("card-sync :: LOGIN FAILED bankCd={} resultCd={} msg={}",
                        bankCd, loginResultCd, msg);
                NxisasApiLog.event(traceId, FLOW, "ERROR",
                        "stage", "LOGIN", "scope", "GATEWAY", "bankCd", bankCd, "resultCode", loginResultCd);
                for (CardMstr c : group) {
                    errors.add(new CardSyncError(
                            c.getBankCd(), resolveBankName(c.getBankCd()), c.getCardNo(),
                            "WBE_CARD_SYNC_LOGIN_FAILED", msg));
                    groupCards.add(new SyncGroupCard(
                            c.getCardNo(), 0, 0, 0,
                            "WBE_CARD_SYNC_LOGIN_FAILED", msg));
                }
                groups.add(new SyncGroup(
                        groupBankCd, groupBankNm,
                        credSeqNo == null ? null : credSeqNo.toPlainString(),
                        false, "WBE_CARD_SYNC_LOGIN_FAILED", msg,
                        groupCards));
                continue;
            }
            NxisasLoginJobResult loginRes = gateway.firstResult(loginOut);
            if (loginRes != null && hasChallenge(loginRes)) {
                // OTP / QNA / CAPTCHA — sync flow doesn't pump
                // challenges; surface a re-auth notice per card.
                String msg = "bank requires OTP / Q&A / CAPTCHA — finish via Manage Cards";
                log.warn("card-sync :: LOGIN CHALLENGE bankCd={} callBackFunc={}",
                        bankCd, loginRes.getCallBackFunc());
                // Card sync does not pump challenges — surfaced as a per-card
                // re-auth notice (the user finishes via Manage Cards).
                NxisasApiLog.event(traceId, FLOW, "CHALLENGE_REQUIRED",
                        "bankCd", bankCd, "callBackFunc", loginRes.getCallBackFunc(),
                        "outcome", "NEEDS_RE_AUTH");
                for (CardMstr c : group) {
                    errors.add(new CardSyncError(
                            c.getBankCd(), resolveBankName(c.getBankCd()), c.getCardNo(),
                            "WBE_CARD_SYNC_NEEDS_RE_AUTH", msg));
                    groupCards.add(new SyncGroupCard(
                            c.getCardNo(), 0, 0, 0,
                            "WBE_CARD_SYNC_NEEDS_RE_AUTH", msg));
                }
                groups.add(new SyncGroup(
                        groupBankCd, groupBankNm,
                        credSeqNo == null ? null : credSeqNo.toPlainString(),
                        false, "WBE_CARD_SYNC_NEEDS_RE_AUTH", msg,
                        groupCards));
                continue;
            }
            log.info("card-sync :: LOGIN OK bankCd={}", bankCd);
            NxisasApiLog.event(traceId, FLOW, "LOGIN_SUCCESS",
                    "bankCd", bankCd, "sasSessionId", NxisasMask.token(loginOut.getSasSessionID()));

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
                log.info("card-sync :: CARD_USING bankCd={} cardNo={} from={} to={}",
                        card.getBankCd(), NxisasMask.card(card.getCardNo()), effFromDt, effToDt);
                NxisasApiLog.event(traceId, FLOW, "CARD_USING_START",
                        "bankCd", card.getBankCd(), "cardNo", NxisasMask.card(card.getCardNo()),
                        "from", effFromDt, "to", effToDt);

                String useTrSeq = gateway.newTrSeq();
                NxisasApiOut<NxisasCardUsingJobResult> useOut;
                try {
                    useOut = gateway.cardUsing(
                            cfg.module(), cfg.category(), useTrSeq, cardInput, cont);
                } catch (Exception ex) {
                    String msg = ex.getMessage() == null ? "gateway call failed" : ex.getMessage();
                    log.warn("card-sync :: CARD_USING THREW bankCd={} cardNo={} msg={}",
                            card.getBankCd(), NxisasMask.card(card.getCardNo()), msg);
                    NxisasApiLog.event(traceId, FLOW, "ERROR",
                            "stage", "CARD_USING", "scope", "TRANSPORT",
                            "bankCd", card.getBankCd(), "cardNo", NxisasMask.card(card.getCardNo()));
                    errors.add(new CardSyncError(
                            card.getBankCd(), resolveBankName(card.getBankCd()), card.getCardNo(),
                            "WBE_CARD_SYNC_GATEWAY_ERROR", msg));
                    groupCards.add(new SyncGroupCard(
                            card.getCardNo(), 0, 0, 0,
                            "WBE_CARD_SYNC_GATEWAY_ERROR", msg));
                    continue;
                }
                String useResultCd = useOut == null || useOut.getCommon() == null
                        ? null : useOut.getCommon().getResultCd();
                if (isGatewayError(useResultCd)) {
                    String msg = useOut == null || useOut.getCommon() == null
                            ? "Coocon CARD_USING failed (no envelope)"
                            : useOut.getCommon().getResultMsg();
                    log.warn("card-sync :: CARD_USING FAILED bankCd={} cardNo={} resultCd={} msg={}",
                            card.getBankCd(), NxisasMask.card(card.getCardNo()), useResultCd, msg);
                    NxisasApiLog.event(traceId, FLOW, "ERROR",
                            "stage", "CARD_USING", "scope", "GATEWAY",
                            "bankCd", card.getBankCd(), "cardNo", NxisasMask.card(card.getCardNo()),
                            "resultCode", useResultCd);
                    errors.add(new CardSyncError(
                            card.getBankCd(), resolveBankName(card.getBankCd()), card.getCardNo(),
                            "WBE_CARD_SYNC_GATEWAY_ERROR", msg));
                    groupCards.add(new SyncGroupCard(
                            card.getCardNo(), 0, 0, 0,
                            "WBE_CARD_SYNC_GATEWAY_ERROR", msg));
                    continue;
                }
                NxisasCardUsingJobResult useRes = gateway.firstCardUsingResult(useOut);
                // Coocon returns the transaction list in a two-level
                // shape — `result.cardArr[0].cardUsing[]`. Mirrors
                // legacy fnsh_0001_001_c001_act.jsp:1319-1323
                // (`result.getcardArr()` → first entry →
                // `getCardUsing()`). Every refactor call covers one
                // card so we only read the first cardArr entry.
                List<NxisasCardUsingEntry> scraped;
                if (useRes == null
                        || useRes.getCardArr() == null
                        || useRes.getCardArr().isEmpty()
                        || useRes.getCardArr().get(0).getCardUsing() == null) {
                    scraped = List.of();
                } else {
                    scraped = useRes.getCardArr().get(0).getCardUsing();
                }

                log.info("card-sync :: CARD_USING OK bankCd={} cardNo={} scraped={}",
                        card.getBankCd(), NxisasMask.card(card.getCardNo()), scraped.size());

                // 3e. Insert with dedup. One inserted row per
                //     scraped transaction that doesn't already exist;
                //     dedup hit → skippedCount++ instead.
                int[] counts = persistScrapedRows(
                        bizKey, userId, now, card, scraped);
                insertedCount += counts[0];
                skippedCount  += counts[1];

                log.info("card-sync :: persisted bankCd={} cardNo={} inserted={} skipped={}",
                        card.getBankCd(), NxisasMask.card(card.getCardNo()), counts[0], counts[1]);
                NxisasApiLog.event(traceId, FLOW, "CARD_DONE",
                        "bankCd", card.getBankCd(), "cardNo", NxisasMask.card(card.getCardNo()),
                        "scraped", scraped.size(), "inserted", counts[0], "skipped", counts[1]);

                // Stamp the last-success on CARD_MSTR so subsequent
                // syncs default to the post-success window — but
                // ONLY when CARD_USING actually returned at least one
                // transaction. Mirrors legacy
                // fnsh_0001_001_c001_act.jsp:1413/1422 where
                // `cardMstr.setTrnLstSucDtm + patch` lives INSIDE the
                // per-transaction loop, so an empty cardArr / empty
                // cardUsings list never advances the cursor.
                //
                // Why: a 200 OK from Coocon with zero transactions is
                // ambiguous — the bank may genuinely have had no
                // activity, OR it may have returned an empty soft-OK
                // on a transient glitch. Advancing the cursor in that
                // case would make tomorrow's auto-run skip today and
                // miss any late-posting activity permanently. Holding
                // the cursor means tomorrow's auto-run re-checks
                // today — cheap insurance against a bank hiccup.
                //
                // Use saveAndFlush for parity with the CardTrnsHstr
                // insert path above — any future wb_get_next_val
                // branch that reads CARD_MSTR mid-batch wouldn't see
                // stale state.
                if (!scraped.isEmpty()) {
                    card.setTrnLstSucDtm(now);
                    card.setModId(userId);
                    card.setModDtm(now);
                    cardMstrRepo.saveAndFlush(card);
                }

                // Per-card outcome row for the group breakdown.
                groupCards.add(new SyncGroupCard(
                        card.getCardNo(),
                        scraped.size(), counts[0], counts[1],
                        null, null));

                cardsSucceeded++;
            }

            // Close the credential bucket — emit a tranList Result
            // (Unit) block per cert so the user can see the per-cert
            // result side-by-side with the legacy log shape.
            log.info("---------------------------- card-sync :: tranList Result(Unit) ----------------------------");
            log.info("card-sync :: cert seq_no={} bankCd={} cards={}",
                    credSeqNo, groupBankCd, groupCards.size());
            for (SyncGroupCard gc : groupCards) {
                log.info("card-sync ::   card_no={} scraped={} inserted={} skipped={} errorCode={}",
                        NxisasMask.card(gc.cardNo()), gc.scrapedCount(), gc.inserted(), gc.skipped(),
                        gc.errorCode() == null ? "-" : gc.errorCode());
            }
            log.info("---------------------------- card-sync :: tranList Result(Unit) ----------------------------");

            groups.add(new SyncGroup(
                    groupBankCd, groupBankNm,
                    credSeqNo == null ? null : credSeqNo.toPlainString(),
                    true, null, null,
                    groupCards));
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

        long elapsedMs = (System.nanoTime() - startedNs) / 1_000_000;
        log.info("---------------------------- card-sync :: tranList Result ----------------------------");
        log.info("card-sync :: bizKey={} cards={}/{} inserted={} skipped={} errors={} elapsed={}ms groups={}",
                bizKey, cardsSucceeded, cardsAttempted, insertedCount, skippedCount,
                errors.size(), elapsedMs, groups.size());
        log.info("---------------------------- card-sync :: tranList Result ----------------------------");
        NxisasApiLog.event(traceId, FLOW, "COMPLETE",
                "success", cardsSucceeded, "attempted", cardsAttempted,
                "inserted", insertedCount, "skipped", skippedCount,
                "errors", errors.size(), "durationMs", elapsedMs);

        return new SyncCardTransactionsResponse(
                cardsAttempted, cardsSucceeded, insertedCount, skippedCount, errors, groups);
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
            String trscDt = normYyyymmdd(tx.getUsingDate());
            if (trscDt == null) {
                // Tx without a date is unusable — Coocon shouldn't
                // emit this but tolerate it to avoid breaking the
                // batch on one bad row.
                continue;
            }
            String authCode = blankToNull(tx.getAuthCode());
            String trscTm   = normHhmmss(tx.getUsingTime());
            BigDecimal amt  = parseAmount(tx.getUsingAmt());

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
            // Prefer the scraped card name when present (legacy
            // `cardTrnsHstr.setCardNm(cardUsing.getCardName())`,
            // fnsh_0001_001_c001_act.jsp:1369); fall back to the
            // CARD_MSTR row's CARD_NM when Coocon omits the field.
            String cardNm = tx.getCardName() != null && !tx.getCardName().isBlank()
                    ? tx.getCardName()
                    : card.getCardNm();
            row.setCardNm(truncate(cardNm, 50));
            row.setMerchNm(truncate(tx.getFranchise(), 50));
            row.setCcyCd(normCcy(tx.getCurrency()));
            // Card transactions are always debits — Coocon's
            // CARD_USING response doesn't carry an inoutDv field, so
            // stamp '2' (OUT) unconditionally per legacy parity.
            row.setInoutDv("2");
            row.setTrscAmt(amt);
            row.setUsingType(truncate(tx.getUsingType(), 50));
            row.setPrchStts(truncate(mapPurchaseStatus(card.getBankCd(), tx.getPurchaseStatus()), 50));
            // Coocon's CARD_USING response doesn't carry a `note`
            // field — column stays null (legacy parity:
            // fnsh_0001_001_c001_act.jsp never calls setNote).
            row.setNote(null);
            row.setAuthCode(truncate(authCode, 15));
            row.setCity(truncate(tx.getCity(), 100));
            row.setRegId(userId);
            row.setRegDtm(now);
            row.setModId(userId);
            row.setModDtm(now);
            row.setErpSndYn("N");

            try {
                // saveAndFlush, not save: the next nextCardSeqNo()
                // call inside this loop is a native SELECT through
                // wb_get_next_val(...) which Hibernate cannot detect
                // as touching CARD_TRNS_HSTR, so auto-flush wouldn't
                // fire. Without an explicit flush, MAX+1 sees the
                // pre-batch state and every row in the same call gets
                // the same card_seq_no → PK collision at commit time.
                cardTrnsRepo.saveAndFlush(row);
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
        // Shared NXISAS result-code policy (also used by BankScrapingService
        // + BankTransactionSyncService). Identical behaviour to the former
        // inline copy.
        return NxisasResultCodes.isGatewayError(resultCd);
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

    /** Per-bank loginType resolver mirroring legacy nxisas_v2.js:944-963.
     *
     *  <p>Default: {@code "ID"} for every bank. Documented overrides:
     *  <ul>
     *    <li>SCBank ({@code 11704104}) &rarr; {@code "S2BAPP_OTP"}</li>
     *    <li>Vietcombank personal ({@code 11704004} +
     *        {@code nxisasGbSeqNo == 2}) &rarr; whatever the credential
     *        carries ({@code credLoginType}) — null/blank also falls
     *        back to {@code "ID"}.</li>
     *    <li>ABBank ({@code 11704106}) &rarr; {@code ""} (legacy test
     *        override at nxisas_v2.js:962 forces empty regardless of
     *        what the credential carries — preserved verbatim).</li>
     *  </ul>
     *
     *  <p>No override for Woori Vietnam ({@value #WOORI_VN_BANK_CD})
     *  or Shinhan VN ({@value #SHINHAN_VN_BANK_CD}) — they use the
     *  default {@code "ID"} like every other VN bank.
     */
    private static String resolveLoginType(String bankCd, int subSeq, String credLoginType) {
        if ("11704104".equals(bankCd)) return "S2BAPP_OTP";   // SCBank
        if ("11704004".equals(bankCd) && subSeq == 2) {
            return credLoginType == null || credLoginType.isBlank() ? "ID" : credLoginType;
        }
        if ("11704106".equals(bankCd)) return "";              // ABBank test override
        return "ID";
    }

    /** Per-bank purchase-status normaliser.
     *
     *  <p>Shinhan VN ({@value #SHINHAN_VN_BANK_CD}) and Woori VN
     *  ({@value #WOORI_VN_BANK_CD}) return Vietnamese status TEXT in
     *  the CARD_USING response (e.g. {@code "Chấp nhận"}). The
     *  legacy save action at
     *  {@code fnsh_0001_001_c001_act.jsp:1378-1402} maps those strings
     *  to single-digit codes ({@code "1"} accepted, {@code "2"}
     *  completed, {@code "9"} cancelled) so the PRCH_STTS column stays
     *  short and frontend i18n keys can resolve on the digit. Every
     *  other bank passes through unchanged.
     *
     *  <p>Trims the input before matching — bank responses sometimes
     *  carry trailing whitespace.
     */
    private static String mapPurchaseStatus(String bankCd, String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String trimmed = raw.trim();
        if (SHINHAN_VN_BANK_CD.equals(bankCd)) {
            switch (trimmed) {
                case "Chấp nhận":  return "1";
                case "Hoàn thành": return "2";
                case "Đã hủy":     return "9";
                default:           return trimmed;
            }
        }
        if (WOORI_VN_BANK_CD.equals(bankCd)) {
            if ("Giao dịch được duyệt".equals(trimmed)) return "1";
            return trimmed;
        }
        return trimmed;
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
