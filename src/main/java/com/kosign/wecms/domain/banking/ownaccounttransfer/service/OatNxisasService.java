package com.kosign.wecms.domain.banking.ownaccounttransfer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService;
import com.kosign.wecms.domain.banking.nxisas.audit.NxsiasModuleTranLogHisRepository;
import com.kosign.wecms.domain.banking.nxisas.audit.entity.NxsiasModuleTranLogHis;
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

    private final BankCertInfoRepository           bankCertInfoRepo;
    private final BankCertCryptoService            bankCertCrypto;
    private final NxsiasModuleTranLogHisRepository nxsiasLogRepo;
    private final ObjectMapper                     objectMapper;

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
     * Refactor port of legacy {@code nxisas_decrypt} / {@code nxisas_encrypt}.
     * The legacy action runs Coocon SEED-CBC over the engine's output
     * {@code Result} payloads; the refactor does not bundle the Coocon
     * ISAS library and ships a structured pass-through that returns
     * {@code NXISAS_DATA} unchanged. Most VND OAT paths (Vietin /
     * Vietcom) emit plaintext JSON so the pass-through is functionally
     * neutral.
     *
     * <p>If a future bank's payloads genuinely need server-side decrypt,
     * port a JCE AES helper here and switch on {@code NXISAS_UID} /
     * {@code NXISAS_ACTION} to select the cipher.
     */
    public NxisasDecryptResponse passthrough(NxisasDecryptRequest req) {
        requireBizKey();
        return new NxisasDecryptResponse(req.NXISAS_DATA());
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
        row.setNxisasType(truncate(svcIdFor(job), 40));

        try {
            nxsiasLogRepo.save(row);
        } catch (Exception ex) {
            log.warn("oat nxisas/logging save failed bizKey={} job={} bankCd={}",
                    bizKey, job, bankCdHint, ex);
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

    private static String requireBizKey() {
        final String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) throw new BusinessException("WBE9999");
        return bizKey;
    }
}
