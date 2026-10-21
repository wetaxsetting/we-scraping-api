package com.kosign.wecms.domain.banking.ownaccounttransfer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.nxisas.SeedCbcDecryptService;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService;
import com.kosign.wecms.domain.banking.nxisas.audit.NxsiasModuleTranLogHisRepository;
import com.kosign.wecms.domain.banking.nxisas.audit.entity.NxsiasModuleTranLogHis;
import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHis;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasCertAcctListRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasDecryptRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasLoggingRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NxisasCertAcctListResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NxisasDecryptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Service for the bundle-facing NXiSAS endpoints. Each method ports
 * one legacy JEX {@code nxisas_*} action; the bundle invokes them via
 * {@code jexjs.createAjaxUtil(...)} routed through the frontend
 * {@code jexjs-adapter.ts ENDPOINT_MAP}.
 *
 * <p>This service is intentionally separate from
 * {@link OwnAccountTransferService} so the four bundle-facing
 * endpoints don't pollute the OAT domain surface (source-accounts /
 * balance-refresh / scraping-result / target-accounts / napas-bank-info).
 * They share tenant scoping and audit channels but live on different
 * concerns.
 *
 * <p>All endpoints are tenant-scoped via {@link TenantContext}; no
 * endpoint trusts a wire {@code biz_key}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OatNxisasService {

    private static final DateTimeFormatter DTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_FORMAT8 =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /** SVC_IDs that Settings &gt; Firm Banking History renders from
     *  {@code EXT_TRAN_CALL_HIS} (V_FIRM_BANKING_HISTORY Leg-S EXT_TRAN →
     *  "Scraping Balance" / "Scraping Transaction" / "Scraping Card
     *  Transaction"). We write an {@code EXT_TRAN_CALL_HIS} row only for
     *  these so the scrape shows up in Firm Banking History exactly like
     *  legacy (B10). Transfers are NOT written here — they already appear
     *  via the NAPAS_REMIT_TRNS_HSTR leg ("Funds Transfer (Scraping)"),
     *  matching legacy {@code nxisas_logging_act.jsp} (no EXT_TRAN row for
     *  a pure TRANSFER). */
    private static final Set<String> FBH_EXT_TRAN_SVC_IDS =
            Set.of("BALANCE_INQUIRY", "REAL_TIME_ONLINE_INQUIRY", "CARD_INQUIRY");

    private final BankCertInfoRepository           bankCertInfoRepo;
    private final BankCertCryptoService            bankCertCrypto;
    private final NxsiasModuleTranLogHisRepository nxsiasLogRepo;
    private final ExtTranCallHisRepository         extTranRepo;
    private final ObjectMapper                     objectMapper;
    private final SeedCbcDecryptService            seedCbc;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    /* =================================================================
     *  POST /nxisas/cert-acct-list
     * ================================================================= */

    /**
     * Refactor port of legacy {@code nxisas_acctList_r003}. Given a
     * {@code BANK_CERT_INFO.SEQ_NO}, return the five cert payload
     * fields the bundle's {@code _v2nxisas.getCertInfo} consumes
     * (legacy {@code trns_0001_007.ts:1582-1599}).
     *
     * <p>Used today as a fallback — the composer pre-populates these
     * fields on the source-account row, so this endpoint only fires
     * when the bundle does a fresh lookup mid-flow. Tenant scope rides
     * on the JWT; a SEQ_NO from another tenant is treated as missing.
     */
    @Transactional(readOnly = true)
    public NxisasCertAcctListResponse certAcctList(NxisasCertAcctListRequest req) {
        final String bizKey = requireBizKey();
        final BigDecimal seqNo;
        try {
            seqNo = new BigDecimal(req.seqNo().trim());
        } catch (NumberFormatException nfe) {
            log.warn("oat nxisas/cert-acct-list invalid seqNo='{}'", req.seqNo());
            throw new BusinessException("WBE_VALIDATION");
        }
        final BankCertInfo cert = bankCertInfoRepo.findByBizKeyAndSeqNo(bizKey, seqNo)
                .orElseThrow(() -> {
                    log.warn("oat nxisas/cert-acct-list miss bizKey={} seqNo={}", bizKey, seqNo);
                    return new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND");
                });
        return new NxisasCertAcctListResponse(
                cert.getCertInfoCon(),
                decryptOrEcho(cert.getCertInfoDetlCon()),
                decryptOrEcho(cert.getCertInfoDetlCon2()),
                decryptOrEcho(cert.getCertInfoDetlCon3()),
                cert.getNxisasGbSeqNo() == null ? null : cert.getNxisasGbSeqNo().toPlainString());
    }

    /* =================================================================
     *  POST /nxisas/decrypt   and   POST /nxisas/encrypt
     * ================================================================= */

    /**
     * Refactor port of legacy {@code nxisas_decrypt}
     * ({@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_decrypt_act.jsp}).
     * Decrypts each engine {@code Output.Result} via the Coocon
     * {@code ISASSeedCBC} cipher, keyed by the session
     * {@code nxisas_uid}/{@code nxisas_action}, so the frontend sees the same
     * plain {@code Result.balance[]} / {@code Result.transfer[]} object legacy
     * produced.
     *
     * <p><b>Runs unconditionally</b>, matching legacy. The bundle's guard at
     * {@code nxisas_v2.js:2353} fires whenever {@code nonDecrypt} is
     * {@code undefined} — which it is on the Own Account Transfer screen — and
     * the decrypt happens <em>before</em> the success/fail dispatch at
     * {@code :2361} / {@code :2389}, so every consumer downstream reads a
     * decrypted payload. The engine encrypts {@code Output.Result} on every
     * response, including {@code ErrorCode "00000000"}.
     *
     * <p>Corrected 2026-08-18: this was previously gated by
     * {@code wecms.oat.seed-decrypt.enabled} (default {@code false}, set in no
     * profile or env file), so it returned its input unchanged and Update
     * Balance failed with "Balance is required." while a successful transfer
     * silently recorded composed values instead of the bank-echoed ones. The
     * flag was built on the premise that "VND payloads mostly arrive
     * plaintext"; the SIT capture disproves that for both Vietcom and Shinhan.
     * The e-Invoice sibling ({@code EInvoiceScrapeDecryptService}) has decrypted
     * unconditionally since 2026-07-14 — this now matches it.
     */
    public NxisasDecryptResponse decrypt(NxisasDecryptRequest req) {
        requireBizKey();
        return new NxisasDecryptResponse(
                seedCbc.decryptNxisasData(req.nxisasData(), req.nxisasUid(), req.nxisasAction()));
    }

    /**
     * Refactor port of legacy {@code nxisas_encrypt} — the inverse of
     * {@link #decrypt}, used by the 2nd-factor (OTP / QnA) re-send path during
     * a TRANSFER. Runs unconditionally for the same reason {@link #decrypt}
     * does: an engine that encrypts its output also expects encrypted input on
     * the re-send leg, so a pass-through here would corrupt the retry.
     */
    public NxisasDecryptResponse encrypt(NxisasDecryptRequest req) {
        requireBizKey();
        return new NxisasDecryptResponse(
                seedCbc.encryptNxisasData(req.nxisasData(), req.nxisasUid(), req.nxisasAction()));
    }

    /* =================================================================
     *  POST /nxisas/logging
     * ================================================================= */

    /**
     * Refactor port of legacy {@code nxisas_logging}. The browser
     * bundle posts a serialised {@code _threadArray} as {@code JSON_STR};
     * we walk it and write one {@code NXSIAS_MODULE_TRAN_LOG_HIS} row
     * per terminal job (LOGIN / BALANCE / TRANSFER / OTP / QNA /
     * CAPTCHA / ACCT_LIST).
     *
     * <p>Audit failures are non-fatal — the call returns success even
     * if a single row fails to insert. Mirrors legacy behaviour
     * ({@code nxisas_logging_act.jsp} writes inside an isolated tx
     * that the bundle never inspects).
     *
     * <p>Runs in {@link Propagation#REQUIRES_NEW} so a partial failure
     * doesn't roll back the caller's chain (the caller is the browser
     * bundle, which has no transaction context, but this matches the
     * legacy isolation pattern).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogging(NxisasLoggingRequest req) {
        final String bizKey = requireBizKey();
        final String userId = TenantContext.currentUserId();
        if (req == null || req.JSON_STR() == null || req.JSON_STR().isBlank()) {
            return;
        }
        try {
            final JsonNode root = objectMapper.readTree(req.JSON_STR());
            walkAndRecord(root, bizKey, userId);
        } catch (Exception ex) {
            log.warn("oat nxisas/logging parse-or-write failed bizKey={} jsonLen={}",
                    bizKey, req.JSON_STR().length(), ex);
        }
    }

    /* =================================================================
     *  Internals
     * ================================================================= */

    /** Walk the bundle's nested thread/result structure and emit one
     *  audit row per terminal job. Mirrors the legacy {@code runLogging}
     *  recursion in {@code nxisas_logging_act.jsp} but trimmed to the
     *  fields the refactor stores. */
    private void walkAndRecord(JsonNode node, String bizKey, String userId) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                walkAndRecord(it.next(), bizKey, userId);
            }
            return;
        }
        if (!node.isObject()) return;
        // Two shapes: a top-level org-obj with {bankCd, job:[...], result:[...]}
        // OR a job-record with {Class, Module, Job, Input, Output, ...}.
        if (node.has("result") || node.has("job")) {
            final String bankCd = textOrEmpty(node, "bankCd");
            final JsonNode resultArr = node.get("result");
            if (resultArr != null && resultArr.isArray()) {
                for (Iterator<JsonNode> it = resultArr.elements(); it.hasNext(); ) {
                    final JsonNode entry = it.next();
                    if (entry.isArray()) {
                        // Some banks emit a nested array (per-job thread). Walk one level.
                        for (Iterator<JsonNode> sub = entry.elements(); sub.hasNext(); ) {
                            recordJobNode(sub.next(), bankCd, bizKey, userId);
                        }
                    } else {
                        recordJobNode(entry, bankCd, bizKey, userId);
                    }
                }
            }
            return;
        }
        // Already a job-record.
        recordJobNode(node, /*bankCdHint=*/ textOrEmpty(node, "bankCd"), bizKey, userId);
    }

    private void recordJobNode(JsonNode jobNode, String bankCdHint, String bizKey, String userId) {
        if (jobNode == null || !jobNode.isObject()) return;
        final String job = textOrEmpty(jobNode, "Job");
        if (job == null || job.isBlank()) return;
        final JsonNode output = jobNode.get("Output");
        final String errCode = output == null ? "" : truncate(textOrEmpty(output, "ErrorCode"), 40);
        final String errMsg  = output == null ? "" : textOrEmpty(output, "ErrorMessage");
        final JsonNode input = jobNode.get("Input");

        final NxsiasModuleTranLogHis row = new NxsiasModuleTranLogHis();
        row.setUuid(UUID.randomUUID().toString().replace("-", ""));
        row.setRegDtm(utcNow14());
        row.setBizKey(bizKey);
        row.setNxisasClass(truncate(textOrEmpty(jobNode, "Class"), 100));
        row.setNxisasModule(truncate(textOrEmpty(jobNode, "Module"), 100));
        row.setNxisasJob(truncate(job, 40));
        row.setNxisasErrCode(errCode == null || errCode.isEmpty() ? "UNKNOWN" : errCode);
        row.setNxisasErrCont(errMsg);
        row.setReqData(input == null ? null : input.toString());
        row.setRegId(userId);
        final String svcId = svcIdFor(job);
        row.setNxisasType(truncate(svcId, 40));

        try {
            nxsiasLogRepo.save(row);
        } catch (Exception ex) {
            log.warn("oat nxisas/logging save failed bizKey={} job={} bankCd={}",
                    bizKey, job, bankCdHint, ex);
        }

        // Legacy parity (B10): also write EXT_TRAN_CALL_HIS for the scraping
        // service categories Settings > Firm Banking History renders
        // (V_FIRM_BANKING_HISTORY Leg-S EXT_TRAN). Without this row the
        // "Scraping Balance" entry is missing after Update Balance. Mirrors
        // legacy nxisas_logging_act.jsp → ExtTranCallHisDAO.create.
        if (FBH_EXT_TRAN_SVC_IDS.contains(svcId)) {
            writeExtTranCallHis(svcId, output, input, bankCdHint, bizKey, userId);
        }
    }

    /** Insert one {@code EXT_TRAN_CALL_HIS} audit row (legacy
     *  {@code buildExtTranCallHis} + {@code ExtTranCallHisDAO.create}).
     *  {@code SEQ_NO} is a per-{@code (SVC_ID, REF_DATE)} MAX+1 in Java —
     *  same pattern as the e-invoice scrape audit; no
     *  {@code wb_get_next_val} branch. Best-effort: a failure is logged
     *  and never breaks the logging response. */
    private void writeExtTranCallHis(String svcId, JsonNode output, JsonNode input,
                                     String bankCdHint, String bizKey, String userId) {
        try {
            final String refDate = utcDate8();
            final BigDecimal seqNo = new BigDecimal(
                    legacyFunctions.nextExtTranCallHisSeqNo(svcId, refDate));

            final ExtTranCallHis call = new ExtTranCallHis();
            call.setSvcId(svcId);
            call.setRefDate(refDate);
            call.setSeqNo(seqNo);
            call.setWebSvcId(svcId);                 // legacy sets web_svc_id = svc_id
            call.setRegDtm(utcNow14());
            call.setRsltCd(truncate(textOrEmpty(output, "ErrorCode"), 10));
            call.setErrCont(truncate(textOrEmpty(output, "ErrorMessage"), 1000));
            call.setBankCd(truncate(bankCdHint, 20));
            call.setBizKey(bizKey);
            call.setUserId(userId);
            if (input != null) {
                call.setAcctNo(truncate(textOrEmpty(input, "acctNum"), 100));
            }
            extTranRepo.save(call);
        } catch (Exception ex) {
            log.warn("oat ext-tran-call-his write failed bizKey={} svcId={} bankCd={}",
                    bizKey, svcId, bankCdHint, ex);
        }
    }

    /** Map an engine job kind to the legacy {@code SVC_ID} bucket. */
    private static String svcIdFor(String job) {
        if (job == null) return "UNKNOWN";
        return switch (job) {
            case "LOGIN", "CAPTCHA", "OTP", "QNA", "ACCT_LIST" -> "BANKING_CREDENTIAL";
            case "BALANCE", "BALANCE_FIXED_DEPOSIT"            -> "BALANCE_INQUIRY";
            case "TRANSFER", "TRANSFER_ML",
                 "TRANSFER_INTERNAL", "TRANSFER_INTERNAL_ML"   -> "OWN_ACCOUNT_TRANSFER";
            case "CARD_USING"                                  -> "CARD_INQUIRY";
            default                                            -> "UNKNOWN";
        };
    }

    private static String textOrEmpty(JsonNode parent, String key) {
        if (parent == null || !parent.has(key)) return "";
        final JsonNode n = parent.get(key);
        if (n == null || n.isNull()) return "";
        if (n.isTextual()) return n.asText();
        return n.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String decryptOrEcho(String value) {
        if (value == null || value.isEmpty()) return value;
        try {
            return bankCertCrypto.decrypt(value);
        } catch (Exception ex) {
            log.warn("oat nxisas/cert-acct-list decrypt fall-through; echoing input", ex);
            return value;
        }
    }

    private static String utcNow14() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FORMAT);
    }

    /** {@code yyyyMMdd} (UTC) — the {@code EXT_TRAN_CALL_HIS.REF_DATE}
     *  partition the per-day SEQ_NO sequence resets on. */
    private static String utcDate8() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DATE_FORMAT8);
    }

    private static String requireBizKey() {
        final String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) throw new BusinessException("WBE9999");
        return bizKey;
    }
}
