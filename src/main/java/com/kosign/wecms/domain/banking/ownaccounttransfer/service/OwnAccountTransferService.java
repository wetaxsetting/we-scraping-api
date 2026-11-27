package com.kosign.wecms.domain.banking.ownaccounttransfer.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.PrdtType;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.NapasBankInfo;
import com.kosign.wecms.domain.banking.ownaccounttransfer.OwnAccountTransferAuditActions;
import com.kosign.wecms.domain.banking.ownaccounttransfer.OwnAccountTransferConstants;
import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstr;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.BalanceRefreshRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.EngineConnectionCheckRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.ScrapingResultIntakeRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.BalanceRefreshResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NapasBankInfoRow;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.ScrapingResultResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.SourceAccountListResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.SourceAccountRow;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.TargetAccountListResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.TargetAccountRow;
import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.banking.repository.NapasBankInfoRepository;
import com.kosign.wecms.domain.banking.repository.NapasRemitTrnsHstrRepository;
import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfo;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Banking &gt; Own Account Transfer — service for the four endpoints
 * pinned in finalized Phase-1 design ([backend/banking/own-account-transfer-api.md](../../../../../../../../../docs/refactor-wecms/backend/banking/own-account-transfer-api.md)):
 * <ul>
 *   <li>{@link #listSourceAccounts(String, String, String)} — port of legacy
 *       {@code trns_0001_001_r001} (source-account picker).</li>
 *   <li>{@link #applyBalanceRefresh(BalanceRefreshRequest)} — port of legacy
 *       {@code trns_0001_001_u001} (post-scrape balance update).</li>
 *   <li>{@link #recordEngineConnectionCheck(EngineConnectionCheckRequest)} —
 *       audit-only sink for the engine-handshake report posted by the
 *       browser bundle at page mount.</li>
 *   <li>{@link #lookupNapasBankInfo(String)} — port of legacy
 *       {@code trns_0001_001_r003} (NAPAS bank-code lookup).</li>
 * </ul>
 *
 * <p><b>NXiSAS boundary:</b> Spring is strictly post-scrape. The local
 * {@code NXiSAS.exe} runs in the browser process, scrapes the bank IB
 * directly, and reports back into the frontend JS which then POSTs
 * the structured outcome to {@link #applyBalanceRefresh}. This service
 * never invokes the engine; it persists what the engine reported and
 * audits every call.
 *
 * <p><b>Tenant scoping:</b> every read / write constrains on
 * {@link TenantContext#currentBizKey()}. Request bodies / query
 * parameters never carry {@code biz_key}.
 *
 * <p><b>Audit policy (OQ-4, PO 2026-05-18):</b> every endpoint writes
 * one event to {@code AUDIT_LOGS_INFM} with
 * {@code wsvc_id = "own-account-transfer"} (the route slug — not the
 * legacy WSVC id {@code trns_0001_001}, per workspace CLAUDE.md
 * 2026-05-09). The shared {@link AuditLogService} handles locale
 * fan-out, seq_no allocation, and the REQUIRES_NEW boundary; failures
 * there are swallowed and logged.
 *
 * <p><b>Path A scope ({@code 2026-05-18}):</b> this MR explicitly does
 * NOT ship {@code /scraping-result}, {@code /target-accounts}, or
 * {@code /transfer-history}. Those endpoints depend on the composer's
 * transfer-execution surface (canonical {@code NAPAS_REMIT_TRNS_HSTR}
 * write, tolerance-band validation, idempotency cache, tx-id regex)
 * and ship together in the future composer MR. The
 * {@code /recipient-banks} endpoint is also deferred — it needs the
 * {@code NAPAS_BANK_REMIT_INFO} table the composer MR will provision.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnAccountTransferService {

    private static final DateTimeFormatter DTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DT_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AcctMstrRepository           acctMstrRepo;
    private final AcctAuthInfoRepository       acctAuthRepo;
    private final BankCdResolver               bankCdResolver;
    private final NapasBankInfoRepository      napasBankInfoRepo;
    private final NapasRemitTrnsHstrRepository napasRemitTrnsHstrRepo;
    private final BankCertInfoRepository       bankCertInfoRepo;
    private final BankCertCryptoService        bankCertCrypto;
    private final AuditLogService              auditLog;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    /* =================================================================
     *  GET /api/v1/banking/own-account-transfer/source-accounts
     * ================================================================= */

    /**
     * Source-account picker. Mirrors legacy
     * {@code trns_0001_001_r001_act.jsp}:
     * <ol>
     *   <li>Compute allowed bank codes (HAENGSUNG {@code H} → VIETIN
     *       only; everything else → 5-bank default list).</li>
     *   <li>Coerce unsupported {@code bank_cd} filter to "all" (legacy
     *       parity, OQ-3).</li>
     *   <li>Fetch active VND {@code 03}/{@code 05} accounts via
     *       {@link AcctMstrRepository#findOwnTransferSourceRows} — the
     *       HAENGSUNG saving-account gate is in the query.</li>
     *   <li>For non-admin USERs, intersect with their grants from
     *       {@code ACCT_AUTH_INFO}.</li>
     *   <li>Project each row to {@link SourceAccountRow} with the
     *       bank name resolved via {@code BANK_INFM} and the
     *       {@code canTransfer} flag computed server-side from the
     *       legacy {@code W}-grant condition.</li>
     *   <li>Write one {@code LIST_SOURCE_ACCOUNTS} audit row.</li>
     * </ol>
     *
     * @param bankCdFilter   optional sender-bank filter; unsupported /
     *                       blank values are coerced to "all" per legacy
     *                       parity ({@code _r001_act.jsp:160-162}).
     * @param sortField      View Setting {@code FIELD_ID} (e.g.
     *                       {@code BAL}); resolved through
     *                       {@link OwnAccountTransferSortField}. Unknown /
     *                       blank values fall back to legacy default
     *                       ({@code BANK_CD ASC, ACCT_NO ASC}).
     * @param sortDirection  {@code ASC} / {@code DESC}; null / unknown
     *                       defaults to {@code ASC} when a valid
     *                       {@code sortField} is supplied.
     */
    @Transactional(readOnly = true)
    public SourceAccountListResponse listSourceAccounts(
            String bankCdFilter, String sortField, String sortDirection) {

        final String  bizKey      = requireBizKey();
        final String  userId      = requireUserId();
        final boolean isAdmin     = TenantContext.currentIsAdmin();
        final boolean isHaengsung = PrdtType.fromCode(TenantContext.currentPrdtType())
                .map(p -> p == PrdtType.H).orElse(false);

        final List<String> allowedBankCds = isHaengsung
                ? OwnAccountTransferConstants.ALLOWED_BANK_CDS_HAENGSUNG
                : OwnAccountTransferConstants.ALLOWED_BANK_CDS_DEFAULT;

        // Coerce unsupported / blank bank_cd → null = "all allowed
        // banks" (legacy parity _r001_act.jsp:160-162, OQ-3).
        final String requestedBank = (bankCdFilter != null && !bankCdFilter.isBlank()
                && allowedBankCds.contains(bankCdFilter))
                ? bankCdFilter
                : null;

        final List<String> effectiveBanks = requestedBank != null
                ? List.of(requestedBank)
                : allowedBankCds;

        final String todayYmd = OffsetDateTime.now(ZoneOffset.UTC).format(DT_FORMAT);

        // Resolve sort. Whitelist-only — unknown values fall back to
        // the legacy default (BANK_CD ASC, ACCT_NO ASC) so a typo never
        // reaches JPA as a raw property name.
        final Sort sort = buildSort(sortField, sortDirection);

        final List<AcctMstr> rows = acctMstrRepo.findOwnTransferSourceRows(
                bizKey, effectiveBanks, isHaengsung, todayYmd, sort);

        // Pre-load every active grant the user holds in this tenant so
        // per-row checks are O(1) map lookups. Empty for ADMIN.
        final Map<AcctKey, GrantFlags> userGrants = isAdmin
                ? Map.of()
                : loadUserGrants(bizKey, userId);

        // Phase B: batch-load BankCertInfo rows keyed by SEQ_NO so per-row
        // cert projection is O(1) (avoids N+1 lookup per source-account).
        // Per-row strategy: ACCT_MSTR.BANK_CERT_SEQ → BANK_CERT_INFO.SEQ_NO.
        // Pre-Phase-B rows (no BANK_CERT_SEQ) get null cert fields and the
        // composer's `hasSavedBankCert(source)` pre-flight handles them.
        final Map<BigDecimal, BankCertInfo> certBySeq = loadBankCerts(bizKey, rows);

        final List<SourceAccountRow> projected = new java.util.ArrayList<>(rows.size());
        BigDecimal totBal     = BigDecimal.ZERO;
        BigDecimal totAviBal  = BigDecimal.ZERO;

        for (AcctMstr a : rows) {
            final AcctKey key = new AcctKey(a.getBankCd(), a.getAcctNo());
            final GrantFlags grants = userGrants.getOrDefault(key, GrantFlags.EMPTY);
            if (!isAdmin && !grants.hasAny()) continue;
            final boolean canTransfer = isAdmin || grants.hasW();
            final BankCertInfo cert = resolveCertForAccount(a, certBySeq);
            projected.add(toSourceRow(a, canTransfer, cert));
            if (a.getBal()     != null) totBal    = totBal.add(a.getBal());
            if (a.getRealAmt() != null) totAviBal = totAviBal.add(a.getRealAmt());
        }

        final SourceAccountListResponse response = new SourceAccountListResponse(
                projected, projected.size(), totBal, totAviBal, allowedBankCds);

        appendAudit(OwnAccountTransferAuditActions.LIST_SOURCE_ACCOUNTS,
                OwnAccountTransferConstants.AUDIT_REF_ACCT_MSTR,
                requestedBank == null ? "ALL" : requestedBank,
                String.valueOf(projected.size()));

        log.debug("oat source-accounts bizKey={} bankFilter={} rows={}",
                bizKey, requestedBank, projected.size());
        return response;
    }

    /* =================================================================
     *  POST /api/v1/banking/own-account-transfer/balance-refresh
     * ================================================================= */

    /**
     * Post-scrape balance refresh. Mirrors legacy
     * {@code trns_0001_001_u001_act.jsp}:
     * <ol>
     *   <li>Resolve PK from {@code TenantContext} + request.</li>
     *   <li>Validate ccy (when supplied, must be {@code VND}).</li>
     *   <li>Look up the existing {@code ACCT_MSTR} row — missing or
     *       wrong tenant → {@code WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND}
     *       (single envelope merges both cases — never leak existence
     *       of another tenant's row).</li>
     *   <li>Block inactive rows → {@code WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_INACTIVE}.</li>
     *   <li>For non-admin USERs, re-verify withdraw authority — forged
     *       requests get {@code WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN}.</li>
     *   <li>Compute the patch shape (legacy lines 107-130):
     *       <ul>
     *         <li>Always: {@code ACCT_BAL_INQ_STTS} /
     *             {@code _MSG} / {@code _LST_INQ_DTM}.</li>
     *         <li>On engine success AND balance delta:
     *             {@code BAL} / {@code CORC_BF_BAL} / {@code ACCT_BAL_UPD_DTM}.</li>
     *         <li>On engine success AND {@code real_amt} present:
     *             {@code REAL_AMT}.</li>
     *       </ul>
     *   </li>
     *   <li>Apply the patch via {@link AcctMstrRepository#applyOwnTransferBalanceRefresh}.</li>
     *   <li>Write one {@code BALANCE_REFRESH} audit row — success
     *       AND failure both write (the row IS the engine outcome).</li>
     *   <li>Return the post-patch BAL on success / the pre-existing
     *       BAL on failure ({@code _u001_act.jsp:136-140}).</li>
     * </ol>
     *
     * <p>Audit + balance write commit/rollback atomically — they share
     * the same {@code @Transactional} boundary. Per the plan §8.1 this
     * feature deliberately does NOT use REQUIRES_NEW for the audit row
     * here, because a failed refresh is itself a recorded event (not a
     * rolled-back ghost). The {@link AuditLogService} swallow-on-error
     * contract still applies — a DB blip during the audit insert won't
     * surface to the caller.
     */
    @Transactional
    public BalanceRefreshResponse applyBalanceRefresh(BalanceRefreshRequest req) {
        final String  bizKey  = requireBizKey();
        final String  userId  = requireUserId();
        final boolean isAdmin = TenantContext.currentIsAdmin();

        // CCY guard — VND-only (OQ-A). null on the wire = legacy
        // behaviour (no echo); any other value rejects.
        if (req.ccyCd() != null && !req.ccyCd().isBlank()
                && !OwnAccountTransferConstants.CCY_VND.equals(req.ccyCd())) {
            throw new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY");
        }

        final AcctMstr existing = acctMstrRepo
                .findByBizKeyAndBankCdAndAcctNo(bizKey, req.bankCd(), req.acctNo())
                .orElseThrow(() -> {
                    log.warn("oat balance-refresh missing-row bizKey={} bankCd={} acctNo={}",
                            bizKey, req.bankCd(), req.acctNo());
                    return new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND");
                });

        if (!OwnAccountTransferConstants.ACTV_YN_ACTIVE.equals(existing.getActvYn())) {
            throw new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_INACTIVE");
        }
        // Defensive ccy parity — the persisted row should be VND too.
        if (!OwnAccountTransferConstants.CCY_VND.equals(existing.getCcyCd())) {
            throw new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY");
        }

        if (!isAdmin && !userHasWithdrawAuth(bizKey, userId, req.bankCd(), req.acctNo())) {
            log.warn("oat balance-refresh forbidden bizKey={} userId={} bankCd={} acctNo={}",
                    bizKey, userId, req.bankCd(), req.acctNo());
            throw new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN");
        }

        final String     now            = utcNow14();
        final boolean    engineSuccess  = OwnAccountTransferConstants.ENGINE_SUCCESS_STTS
                                            .equals(req.acctBalInqStts());
        final boolean    hasBalDelta    = engineSuccess
                                            && req.bal() != null
                                            && (existing.getBal() == null
                                                || existing.getBal().compareTo(req.bal()) != 0);

        final BigDecimal newBal         = hasBalDelta ? req.bal()         : null;
        final BigDecimal newCorcBfBal   = hasBalDelta ? existing.getBal() : null;
        final String     newUpdDtm      = hasBalDelta ? now               : null;
        final BigDecimal newRealAmt     = engineSuccess && req.realAmt() != null
                                            ? req.realAmt() : null;

        final int patched = acctMstrRepo.applyOwnTransferBalanceRefresh(
                bizKey, req.bankCd(), req.acctNo(),
                req.acctBalInqStts(), req.acctBalInqMsg(), now, userId,
                newBal, newRealAmt, newCorcBfBal, newUpdDtm);
        if (patched != 1) {
            // Defensive — the existence check above already ran; a row
            // count of zero here means a concurrent delete. Surface as
            // not-found for parity with the explicit pre-check.
            log.warn("oat balance-refresh patched={} bizKey={} bankCd={} acctNo={}",
                    patched, bizKey, req.bankCd(), req.acctNo());
            throw new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND");
        }

        // Build response BAL: post-patch value when the engine reported
        // success (legacy _u001_act.jsp:136-138); else echo the pre-
        // existing BAL (legacy line 139).
        final BigDecimal responseBal = engineSuccess && req.bal() != null
                ? req.bal()
                : existing.getBal();

        appendAudit(OwnAccountTransferAuditActions.BALANCE_REFRESH,
                OwnAccountTransferConstants.AUDIT_REF_ACCT_MSTR,
                req.bankCd(),
                req.acctNo());

        log.info("oat balance-refresh bizKey={} bankCd={} acctNo={} stts={} balDelta={}",
                bizKey, req.bankCd(), req.acctNo(), req.acctBalInqStts(), hasBalDelta);

        return new BalanceRefreshResponse(
                req.bankCd(), req.acctNo(), responseBal,
                OwnAccountTransferConstants.CCY_VND, now);
    }

    /* =================================================================
     *  POST /api/v1/banking/own-account-transfer/engine-connection-check
     * ================================================================= */

    /**
     * Engine-handshake audit-only sink. The browser bundle reports the
     * outcome of its {@code _v2nxisas.openNxisas(...)} probe; the row
     * IS the only artifact — there's no domain state to mutate. The
     * controller returns {@code 204 No Content}.
     */
    @Transactional
    public void recordEngineConnectionCheck(EngineConnectionCheckRequest req) {
        final String bizKey = requireBizKey();
        requireUserId();      // populate TenantContext for the audit writer

        appendAudit(OwnAccountTransferAuditActions.ENGINE_CONNECTION_CHECK,
                OwnAccountTransferConstants.AUDIT_REF_NXISAS,
                req.result(),
                (req.engineVendor() == null ? "" : req.engineVendor())
                        + " "
                        + (req.engineVersion() == null ? "" : req.engineVersion()));

        log.info("oat engine-check bizKey={} result={} vendor={} version={}",
                bizKey, req.result(), req.engineVendor(), req.engineVersion());
    }

    /* =================================================================
     *  GET /api/v1/banking/own-account-transfer/napas-bank-info
     * ================================================================= */

    /**
     * NAPAS bank-code lookup. Mirrors legacy
     * {@code trns_0001_001_r003_act.jsp}: returns the dictionary row
     * for one {@code NAPAS_BANK_CD}. The dictionary is global (no
     * {@code BIZ_KEY} on {@code NAPAS_BANK_INFO}) so tenant scope
     * applies to the audit row, not the lookup itself.
     *
     * @throws BusinessException {@code WBE_OWN_ACCOUNT_TRANSFER_NAPAS_NOT_FOUND}
     *         when the code is not in the dictionary.
     */
    @Transactional(readOnly = true)
    public NapasBankInfoRow lookupNapasBankInfo(String napasBankCd) {
        requireBizKey();
        requireUserId();

        final NapasBankInfo row = napasBankInfoRepo.findById(napasBankCd)
                .orElseThrow(() -> {
                    log.warn("oat napas-bank-info miss napasBankCd={}", napasBankCd);
                    return new BusinessException("WBE_OWN_ACCOUNT_TRANSFER_NAPAS_NOT_FOUND");
                });

        appendAudit(OwnAccountTransferAuditActions.LOOKUP_NAPAS_BANK_INFO,
                OwnAccountTransferConstants.AUDIT_REF_NAPAS_BANK_INFO,
                napasBankCd,
                row.getBankCd());

        // B9 (2026-06-04): the dictionary's display-name columns are now
        // mapped on NapasBankInfo, so the lookup returns the recipient
        // bank names instead of nulls — matching legacy
        // trns_0001_001_r003 which returns SHORT_NAME / LONG_NAME_EN /
        // LONG_NAME_VN.
        return new NapasBankInfoRow(
                row.getNapasBankCd(),
                row.getShortName(),
                row.getLongNameEn(),
                row.getLongNameVn(),
                row.getBankCd());
    }

    /* =================================================================
     *  GET /api/v1/banking/own-account-transfer/target-accounts
     * ================================================================= */

    /**
     * Target-account picker for the composer. Mirrors the source-account
     * list shape but excludes the row the user is transferring FROM
     * (legacy {@code trns_0001_002}/{@code trns_0001_005} "My Account"
     * popup which the composer falls back to when contacts / recent are
     * empty). Path A scope (OQ-A): own-only VND — external NAPAS
     * recipients are deferred.
     *
     * <p>Same gating as {@link #listSourceAccounts}: HAENGSUNG narrows
     * to VietinBank only, USER-mode filters via the {@code ACCT_AUTH_INFO}
     * pivot. Source row exclusion is best-effort — when both keys are
     * supplied, the matching row is filtered out; absent keys return
     * the full list (the composer never asks without keys today, but
     * defensive).
     */
    @Transactional(readOnly = true)
    public TargetAccountListResponse listTargetAccounts(
            String sourceBankCd, String sourceAcctNo) {

        final String  bizKey      = requireBizKey();
        final String  userId      = requireUserId();
        final boolean isAdmin     = TenantContext.currentIsAdmin();
        final boolean isHaengsung = PrdtType.fromCode(TenantContext.currentPrdtType())
                .map(p -> p == PrdtType.H).orElse(false);

        final List<String> allowedBankCds = isHaengsung
                ? OwnAccountTransferConstants.ALLOWED_BANK_CDS_HAENGSUNG
                : OwnAccountTransferConstants.ALLOWED_BANK_CDS_DEFAULT;
        final String       todayYmd = OffsetDateTime.now(ZoneOffset.UTC).format(DT_FORMAT);
        final Sort         sort     = Sort.by(Sort.Order.asc("bankCd"), Sort.Order.asc("acctNo"));

        final List<AcctMstr> rows = acctMstrRepo.findOwnTransferSourceRows(
                bizKey, allowedBankCds, isHaengsung, todayYmd, sort);

        // USER-mode pivot (same as source-accounts).
        final Map<AcctKey, GrantFlags> userGrants = isAdmin
                ? Map.of()
                : loadUserGrants(bizKey, userId);

        final List<TargetAccountRow> projected = new ArrayList<>(rows.size());
        for (AcctMstr a : rows) {
            // Skip the row the user is transferring FROM.
            if (sourceBankCd != null && sourceAcctNo != null
                    && sourceBankCd.equals(a.getBankCd())
                    && sourceAcctNo.equals(a.getAcctNo())) {
                continue;
            }
            final AcctKey key = new AcctKey(a.getBankCd(), a.getAcctNo());
            if (!isAdmin && !userGrants.getOrDefault(key, GrantFlags.EMPTY).hasAny()) {
                continue;
            }
            // Lowest NAPAS code wins for multi-branch banks (IBK / KB /
            // KEB Hana / VPBank) — legacy IDO.ACCT_MSTR_R008 parity.
            final String napasBankCd = napasBankInfoRepo
                    .findFirstByBankCdOrderByNapasBankCdAsc(a.getBankCd())
                    .map(NapasBankInfo::getNapasBankCd).orElse(null);
            projected.add(new TargetAccountRow(
                    TargetAccountRow.KIND_OWN_ACCOUNT,
                    a.getBankCd(),
                    resolveBankName(a.getBankCd()),
                    a.getAcctNo(),
                    a.getAcctNickNm() != null ? a.getAcctNickNm() : a.getAcctNm(),
                    a.getAcctTypeCd(),
                    a.getCcyCd(),
                    napasBankCd));
        }

        appendAudit(OwnAccountTransferAuditActions.LIST_TARGET_ACCOUNTS,
                OwnAccountTransferConstants.AUDIT_REF_ACCT_MSTR,
                sourceBankCd == null ? "ALL" : sourceBankCd,
                String.valueOf(projected.size()));

        log.debug("oat target-accounts bizKey={} sourceBankCd={} rows={}",
                bizKey, sourceBankCd, projected.size());

        return new TargetAccountListResponse(projected, projected.size());
    }

    /* =================================================================
     *  POST /api/v1/banking/own-account-transfer/scraping-result
     * ================================================================= */

    /**
     * Persist the post-scrape engine result the browser bundle (local
     * NXiSAS) reported back. Mirrors legacy
     * {@code trns_0002_001_c001_act.jsp} — same canonical write to
     * {@code NAPAS_REMIT_TRNS_HSTR}, plus the refactor's additions
     * (audit row, server-side ownership re-validation).
     *
     * <p><b>Spring is strictly post-scrape.</b> The engine runs on the
     * user's machine (the local {@code NXiSAS.exe} reached via the
     * browser bundle); Spring never invokes it. The intake validates
     * what the engine reported, persists if the validation passes,
     * and audits the outcome.
     *
     * <p>Validation order (mirrors the legacy + Phase-1 spec §9.2):
     * <ol>
     *   <li><b>CCY</b> — must be {@code VND} (OQ-A).</li>
     *   <li><b>Transfer-type shape</b> — {@code SAME_BANK} requires
     *       {@code targetBankCd}; {@code NAPAS} requires
     *       {@code targetNapasBankCd}.</li>
     *   <li><b>Amount drift</b> — {@code requestedAmt} vs
     *       {@code actualAmt} is <b>logged, never rejected</b>. Legacy has
     *       no such comparison and always records the bank-reported amount;
     *       rejecting would discard the record of an executed transfer.</li>
     *   <li><b>Source ownership</b> — row must exist for the tenant,
     *       be {@code ACTV_YN='Y'}, be VND; non-admin USERs must hold
     *       a {@code W} grant.</li>
     *   <li><b>Target shape</b> — under OQ-A the target is the
     *       tenant's own account. NAPAS resolves to internal bank_cd
     *       via the dictionary; same-bank uses the supplied bank_cd.
     *       The resolved (bank_cd, acct_no) must exist + be active +
     *       be VND.</li>
     * </ol>
     *
     * <p>The audit row is written for BOTH success and reject paths so
     * the trail is complete (OQ-4 audit-everywhere policy). Reject
     * audits use {@code SCRAPING_RESULT_REJECT}; success audits use
     * {@code SCRAPING_RESULT_OK}.
     */
    @Transactional
    public ScrapingResultResponse recordScrapingResult(ScrapingResultIntakeRequest req) {
        final String  bizKey  = requireBizKey();
        final String  userId  = requireUserId();
        final boolean isAdmin = TenantContext.currentIsAdmin();

        // -- 1. CCY guard --------------------------------------------
        // NOTE — idempotency/replay removed 2026-06-10: the legacy JEX
        // NAPAS_REMIT_TRNS_HSTR table has no OAT_IDEMPOTENCY_KEY column,
        // so the synthetic replay guard (lookup + tampering check) was
        // dropped to keep the entity aligned with the live schema. Each
        // intake now inserts a fresh row, matching legacy JEX behaviour
        // (which deduped only on duplicate TRS_ID). idempotency_key still
        // rides the wire and is recorded in the audit trail.
        if (!OwnAccountTransferConstants.CCY_VND.equals(req.ccyCd())) {
            rejectAndAudit(req, "WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY");
        }

        // -- 2. Transfer-type / target-field shape -------------------
        final String transferType = req.transferType();
        if (OwnAccountTransferConstants.TRANSFER_TYPE_NAPAS.equals(transferType)) {
            if (req.targetNapasBankCd() == null || req.targetNapasBankCd().isBlank()) {
                rejectAndAudit(req, "WBE_OAT_TARGET_NAPAS_REQUIRED");
            }
        } else if (OwnAccountTransferConstants.TRANSFER_TYPE_SAME_BANK.equals(transferType)) {
            if (req.targetBankCd() == null || req.targetBankCd().isBlank()) {
                rejectAndAudit(req, "WBE_OAT_TARGET_BANK_REQUIRED");
            }
            if (!req.targetBankCd().equals(req.sourceBankCd())) {
                rejectAndAudit(req, "WBE_OAT_SAME_BANK_MISMATCH");
            }
        }

        // -- 3. Amount drift — LOGGED, never rejected (legacy parity) --
        // Legacy has no "requested vs actual" concept at all: the composer
        // sends a single TRSC_AMT — the bank's `Result.transfer[0].remitAmt`
        // on success, the entered amount on failure (trns_0001_007.ts:2158,
        // 2169) — and trns_0002_001_c001_act.jsp validates only that it is a
        // positive number (ValidationHelper.validateAmountOnlyPositive). It
        // cannot reject on a mismatch because it has nothing to compare
        // against; it records whatever the bank reported.
        //
        // Rejecting here would discard the ONLY record of a transfer the bank
        // may already have executed, so the divergence is logged and the row
        // is still written with the engine-reported amount. (legacy-parity fix
        // 2026-08-18 — see docs/bugs/internal-transfer-intake-legacy-parity-2026-08-18.md)
        if (!Objects.equals(req.requestedAmt(), req.actualAmt())) {
            log.warn("oat scraping-result amount-drift (recorded, not rejected) "
                            + "bizKey={} idem={} requested={} actual={}",
                    bizKey, req.idempotencyKey(), req.requestedAmt(), req.actualAmt());
        }

        // -- 4. Source ownership re-validation -----------------------
        final AcctMstr source = acctMstrRepo
                .findByBizKeyAndBankCdAndAcctNo(bizKey, req.sourceBankCd(), req.sourceAcctNo())
                .orElse(null);
        if (source == null
                || !OwnAccountTransferConstants.ACTV_YN_ACTIVE.equals(source.getActvYn())
                || !OwnAccountTransferConstants.CCY_VND.equals(source.getCcyCd())) {
            rejectAndAudit(req, "WBE_OAT_SOURCE_NOT_FOUND");
        }
        if (!isAdmin && !userHasWithdrawAuth(
                bizKey, userId, req.sourceBankCd(), req.sourceAcctNo())) {
            rejectAndAudit(req, "WBE_OAT_SOURCE_FORBIDDEN");
        }

        // -- 5. Target shape + ownership -----------------------------
        String resolvedTargetBankCd = req.targetBankCd();
        if (OwnAccountTransferConstants.TRANSFER_TYPE_NAPAS.equals(transferType)) {
            final NapasBankInfo napas = napasBankInfoRepo
                    .findById(req.targetNapasBankCd()).orElse(null);
            if (napas == null || napas.getBankCd() == null) {
                rejectAndAudit(req, "WBE_OAT_TARGET_NAPAS_UNKNOWN");
            }
            if (req.targetBankCd() != null && !req.targetBankCd().isBlank()
                    && !req.targetBankCd().equals(napas.getBankCd())) {
                rejectAndAudit(req, "WBE_OAT_TARGET_BANK_NAPAS_MISMATCH");
            }
            resolvedTargetBankCd = napas.getBankCd();
        }

        final AcctMstr target = acctMstrRepo
                .findByBizKeyAndBankCdAndAcctNo(bizKey, resolvedTargetBankCd, req.targetAcctNo())
                .orElse(null);
        if (target == null
                || !OwnAccountTransferConstants.ACTV_YN_ACTIVE.equals(target.getActvYn())
                || !OwnAccountTransferConstants.CCY_VND.equals(target.getCcyCd())) {
            rejectAndAudit(req, "WBE_OAT_TARGET_NOT_FOUND");
        }

        // -- 5b. Duplicate settlement-id guard (legacy parity) -------
        // Mirrors trns_0002_001_c001_act.jsp:94-99 +
        // ValidationHelper.validateTrnsStlmId: when the engine returned
        // a bank settlement id, reject a second intake carrying the same
        // id that is not yet settled (TRNS_STLM_YN <> 'Y'). Stops a
        // double-submit / engine-retry from writing a duplicate
        // NAPAS_REMIT_TRNS_HSTR row. Legacy runs the check only when the
        // TRS_ID is present, so a blank bankTxId skips the guard.
        final String bankTxId = req.bankTxId();
        if (bankTxId != null && !bankTxId.isBlank()
                && napasRemitTrnsHstrRepo.existsByBizKeyAndTrnsStlmIdAndTrnsStlmYnNot(
                        bizKey, bankTxId, OwnAccountTransferConstants.NRTH_STLM_YN_YES)) {
            log.warn("oat scraping-result duplicate bank-tx-id bizKey={} idem={} bankTxId={}",
                    bizKey, req.idempotencyKey(), bankTxId);
            rejectAndAudit(req, "WBE_OAT_DUPLICATE_BANK_TX_ID");
        }

        // -- 6. Persist ----------------------------------------------
        final NapasRemitTrnsHstr row = insertHistoryRow(req, bizKey, userId, resolvedTargetBankCd);

        final boolean engineSuccess = OwnAccountTransferConstants.ENGINE_SUCCESS_STTS
                .equals(req.trnsStts());

        // -- 6b. Persist the bank-account password(s) on success -------
        // Model A (PO-approved 2026-08-26): the user types the password
        // every transfer; the refactor NEVER reads it back. The write
        // exists solely so the live JEX system keeps working on the same
        // account — legacy takes ACCT_PWD straight from the row and sends
        // it without ever showing a field (trns_0001_007.ts:675-682 →
        // :1943). Because JEX consumes it silently, storing a value the
        // bank rejected would send that wrong password from the legacy
        // screen with no chance to correct it, and repeated failures walk
        // the bank's lockout ladder (80002421 → 80002422 → 80002423).
        // Hence: success only.
        if (engineSuccess) {
            persistTransferPasswords(req, bizKey, userId);
        }

        appendAudit(engineSuccess
                        ? OwnAccountTransferAuditActions.SCRAPING_RESULT_OK
                        : OwnAccountTransferAuditActions.SCRAPING_RESULT_REJECT,
                OwnAccountTransferConstants.AUDIT_REF_NAPAS_REMIT,
                row.getSeqNo() == null ? "" : row.getSeqNo().toPlainString(),
                req.idempotencyKey());

        log.info("oat scraping-result persisted bizKey={} seqNo={} idem={} stts={}",
                bizKey, row.getSeqNo(), req.idempotencyKey(), req.trnsStts());
        return toIntakeResponse(row, req.idempotencyKey());
    }

    /* =================================================================
     *  Internals
     * ================================================================= */

    /** Translate the wire sort tokens to a Spring Data {@link Sort} via
     *  the {@link OwnAccountTransferSortField} whitelist. Unknown /
     *  blank values fall back to the legacy default
     *  ({@code BANK_CD ASC, ACCT_NO ASC}). */
    private static Sort buildSort(String sortField, String sortDirection) {
        final OwnAccountTransferSortField field =
                OwnAccountTransferSortField.parse(sortField);
        if (field == null) {
            return Sort.by(Sort.Order.asc("bankCd"), Sort.Order.asc("acctNo"));
        }
        SortDirection dir = SortDirection.parse(sortDirection);
        if (dir == null) dir = SortDirection.ASC;
        Sort.Order primary = dir == SortDirection.DESC
                ? Sort.Order.desc(field.entityProperty())
                : Sort.Order.asc(field.entityProperty());
        // Tie-break on (bankCd, acctNo) so equal-keyed rows render in
        // a stable order across requests.
        return Sort.by(primary)
                .and(Sort.by(Sort.Order.asc("bankCd"), Sort.Order.asc("acctNo")));
    }

    /** Re-verify the current user's withdraw authority on
     *  {@code (bizKey, bankCd, acctNo)}. ADMIN callers bypass this
     *  check at the call site (legacy parity — admins can refresh any
     *  row). */
    private boolean userHasWithdrawAuth(String bizKey, String userId,
                                         String bankCd, String acctNo) {
        return acctAuthRepo.findAllByAccount(
                        bizKey, bankCd, acctNo,
                        OwnAccountTransferConstants.AUTH_TYPE_WITHDRAW).stream()
                .anyMatch(a -> userId.equals(a.getUserId())
                        && OwnAccountTransferConstants.USE_YN_ACTIVE.equals(a.getUseYn()));
    }

    /**
     * Encrypt and store whichever bank-account password(s) the composer
     * sent, after the bank accepted the transfer.
     *
     * <p>Encryption goes through {@link BankCertCryptoService}, whose
     * output is byte-identical to the legacy JEX DB function
     * {@code public.encrypt(txt)} (AES-256-CBC, zero IV, key =
     * {@code sha256('wabooksvn')}, base64). This is what makes the write
     * readable by live JEX; an AES-GCM value here would be undecryptable
     * on the legacy side and would silently break IBK / KEB Hana
     * transfers there after cutover.
     *
     * <p>Blank values are normalised to {@code null} so the repository's
     * {@code COALESCE} leaves the corresponding column untouched — a
     * bank that needs only the account password must not blank an
     * existing {@code TRNS_PWD}.
     *
     * <p><b>Best-effort by design.</b> The transfer has already executed
     * at the bank when this runs; failing the intake because a
     * convenience write did not land would discard the only record of a
     * real money movement. A miss is logged and swallowed. Nothing is
     * logged that could reveal the password itself.
     */
    private void persistTransferPasswords(
            ScrapingResultIntakeRequest req, String bizKey, String userId) {

        final String acctPwdEnc = encryptOrNull(req.acctPwd());
        final String trnsPwdEnc = encryptOrNull(req.trnsPwd());
        if (acctPwdEnc == null && trnsPwdEnc == null) {
            return;                       // password-less bank — nothing to store
        }

        try {
            final int updated = acctMstrRepo.applyTransferPasswords(
                    bizKey, req.sourceBankCd(), req.sourceAcctNo(),
                    acctPwdEnc, trnsPwdEnc, userId, utcNow14());
            if (updated == 0) {
                log.warn("oat password persist matched no acct_mstr row "
                                + "bizKey={} bankCd={} acctNo={}",
                        bizKey, req.sourceBankCd(), req.sourceAcctNo());
            } else {
                log.info("oat password persisted bizKey={} bankCd={} acctNo={} acct={} trns={}",
                        bizKey, req.sourceBankCd(), req.sourceAcctNo(),
                        acctPwdEnc != null, trnsPwdEnc != null);
            }
        } catch (RuntimeException e) {
            // Never fail an already-executed transfer over this.
            log.error("oat password persist failed bizKey={} bankCd={} acctNo={}",
                    bizKey, req.sourceBankCd(), req.sourceAcctNo(), e);
        }
    }

    /** Legacy-format ciphertext for a non-blank value, else {@code null}
     *  (which the repository's {@code COALESCE} reads as "leave as is"). */
    private String encryptOrNull(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        return bankCertCrypto.encrypt(plaintext);
    }

    /** Insert the post-scrape row into {@code NAPAS_REMIT_TRNS_HSTR}.
     *  Two-attempt seq_no allocator — the first uses
     *  {@code MAX(seq_no) + 1}; on PK race we retry once with a fresh
     *  allocation before giving up. Mirrors legacy
     *  {@code trns_0002_001_c001_act.jsp:101-108}. */
    private NapasRemitTrnsHstr insertHistoryRow(
            ScrapingResultIntakeRequest req,
            String bizKey,
            String userId,
            String resolvedTargetBankCd) {
        final NapasRemitTrnsHstr row = new NapasRemitTrnsHstr();
        row.setBizKey(bizKey);
        row.setOutBankCode(req.sourceBankCd());
        // Account numbers + currency: the BANK-echoed value wins when the
        // engine reported one, else the composed value — legacy
        // trns_0001_007.ts:2157/2163/2158 (success) vs :2167/2168/2170
        // (failure). These are RECORD-only; the ownership re-validation above
        // ran on the composed keys, never on these, so a bank-normalized
        // account number can never reject an already-executed transfer.
        row.setOutAcctNo(firstNonBlank(req.engineOutAcctNo(), req.sourceAcctNo()));
        row.setTransType(OwnAccountTransferConstants.TRANSFER_TYPE_SAME_BANK.equals(req.transferType())
                ? OwnAccountTransferConstants.NRTH_TRANS_TYPE_SAME_BANK
                : OwnAccountTransferConstants.NRTH_TRANS_TYPE_NAPAS);
        row.setInBankCode(resolvedTargetBankCd);
        row.setInNapasBankCode(req.targetNapasBankCd());
        row.setInAcctNo(firstNonBlank(req.engineInAcctNo(), req.targetAcctNo()));
        row.setOwacNm(req.targetOwacNm());
        row.setCcyCd(firstNonBlank(req.engineCcyCd(), req.ccyCd()));
        row.setTrscAmt(new BigDecimal(req.actualAmt()));
        row.setFee(req.fee());
        // A/B (wire) → 1/2 (legacy column) — see toLegacyFeeDfrSbj.
        row.setFeeDfrSbj(OwnAccountTransferConstants.toLegacyFeeDfrSbj(req.feeDfrSbj()));
        row.setRmrk(req.rmrk());
        row.setRmrk2(req.rmrk2());
        row.setTrnsStts(req.trnsStts());
        row.setTrnsMsg(req.trnsMsg());
        row.setTrnsStlmId(req.bankTxId());
        row.setTrnsStlmYn(OwnAccountTransferConstants.NRTH_STLM_YN_NO);
        row.setRegId(userId);
        row.setRegDtm(utcNow14());

        for (int attempt = 0; attempt < 2; attempt++) {
            // SEQ_NO via the single wb_get_next_val funnel (V140 #22 — per-tenant
            // MAX+1). Re-allocated each attempt so the one-shot retry below still
            // picks a fresh slot after a PK race.
            row.setSeqNo(new BigDecimal(legacyFunctions.nextNapasRemitSeqNo(bizKey)));
            try {
                return napasRemitTrnsHstrRepo.saveAndFlush(row);
            } catch (DataIntegrityViolationException collision) {
                if (attempt == 1) {
                    log.error("oat scraping-result seq_no race after retry bizKey={} idem={}",
                            bizKey, req.idempotencyKey(), collision);
                    throw collision;
                }
                log.warn("oat scraping-result seq_no PK race — retrying once bizKey={} idem={}",
                        bizKey, req.idempotencyKey());
            }
        }
        // Unreachable — loop returns or rethrows.
        throw new IllegalStateException("oat seq_no allocator exhausted unexpectedly");
    }

    /** First non-blank of the two, else {@code null}. Used to prefer a
     *  bank-echoed result field over the composed request value while
     *  treating {@code null} and {@code ""} identically (legacy's
     *  {@code common.app.null2void} inputs make blank and absent the same
     *  thing on the wire). */
    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        return fallback;
    }

    /** Write a reject audit row, then throw the business exception.
     *  The audit row is best-effort (REQUIRES_NEW inside
     *  {@link AuditLogService}) — its failure must not mask the
     *  domain rejection. */
    private void rejectAndAudit(ScrapingResultIntakeRequest req, String code) {
        appendAudit(OwnAccountTransferAuditActions.SCRAPING_RESULT_REJECT,
                OwnAccountTransferConstants.AUDIT_REF_NAPAS_REMIT,
                req.idempotencyKey() == null ? "<no-key>" : req.idempotencyKey(),
                code);
        throw new BusinessException(code);
    }

    /** Build the wire response from the freshly persisted row. The
     *  {@code idempotencyKey} is echoed back from the request (it is no
     *  longer persisted — see the idempotency-removal note in
     *  {@link #recordScrapingResult}). {@code alreadyRecorded} is always
     *  {@code false} now that the replay short-circuit is gone; the wire
     *  field is retained for frontend contract stability. */
    private static ScrapingResultResponse toIntakeResponse(
            NapasRemitTrnsHstr row, String idempotencyKey) {
        final boolean engineSuccess = OwnAccountTransferConstants.ENGINE_SUCCESS_STTS
                .equals(row.getTrnsStts());
        return new ScrapingResultResponse(
                row.getSeqNo() == null ? null : row.getSeqNo().toPlainString(),
                idempotencyKey,
                row.getTrnsStlmId(),
                row.getTrscAmt(),
                row.getTrnsStts(),
                engineSuccess ? ScrapingResultResponse.STATUS_SUCCESS
                              : ScrapingResultResponse.STATUS_FAILED,
                /*alreadyRecorded=*/ false);
    }

    /** Pre-load every active permission the user holds in this tenant.
     *  Returns one entry per (bankCd, acctNo) with the V/W flags
     *  collapsed. Always empty for ADMIN (the caller short-circuits). */
    private Map<AcctKey, GrantFlags> loadUserGrants(String bizKey, String userId) {
        final List<AcctAuthInfo> grants = acctAuthRepo.findByBizKeyAndUserIdAndUseYn(
                bizKey, userId, OwnAccountTransferConstants.USE_YN_ACTIVE);
        final Map<AcctKey, GrantFlags> map = new HashMap<>();
        for (AcctAuthInfo g : grants) {
            final AcctKey k = new AcctKey(g.getBankCd(), g.getAcctNo());
            map.put(k, map.getOrDefault(k, GrantFlags.EMPTY).with(g.getAuthType()));
        }
        return map;
    }

    /** Project a managed {@link AcctMstr} into the wire shape. Bank
     *  name resolves via {@code BANK_INFM}; falls back to the code
     *  itself when the dictionary has no row.
     *
     *  <p>Cert fields (Phase B activation): when a {@link BankCertInfo}
     *  row is resolved (non-null), populate {@code certInfoCon} /
     *  {@code certInfoDetlCon} (decrypted) / {@code certInfoDetlCon2} /
     *  {@code nxisasGbSeqNo}. Otherwise leave them null and the
     *  composer's pre-flight surfaces the "cert not configured"
     *  warning. */
    private SourceAccountRow toSourceRow(AcctMstr a, boolean canTransfer, BankCertInfo cert) {
        final String certInfoCon       = cert == null ? null : cert.getCertInfoCon();
        // Login password → STRICT decrypt: this value becomes loginPWD and
        // is forwarded to the NXiSAS engine (and on to the bank). A value
        // that doesn't decrypt under the legacy key (corrupt / foreign
        // cipher, e.g. a retired-AES-GCM blob) MUST project as null so the
        // composer's hasSavedBankCert pre-flight blocks the call — never be
        // echoed to the bank (which rejects it with an opaque 8000FFFF).
        final String certInfoDetlCon   = cert == null ? null : decryptPasswordOrNull(cert.getCertInfoDetlCon());
        final String certInfoDetlCon2  = cert == null ? null : decryptOrNull(cert.getCertInfoDetlCon2());
        final String nxisasGbSeqNo     = cert == null || cert.getNxisasGbSeqNo() == null
                ? null
                : cert.getNxisasGbSeqNo().toPlainString();
        return new SourceAccountRow(
                a.getBankCd(),
                resolveBankName(a.getBankCd()),
                a.getAcctNo(),
                // Account Nickname column — legacy parity
                // (`trns_0001_001_v001.ts:497` renders
                // `null2void(ACCT_NICK_NM)`: the raw nickname, blank when
                // null). Do NOT fall back to ACCT_NM (the product name,
                // e.g. "E-Time Deposit (VND)") — legacy never shows it in
                // this column.
                a.getAcctNickNm(),
                a.getAcctTypeCd(),
                a.getBal(),
                a.getRealAmt(),
                a.getCcyCd(),
                a.getAcctBalInqStts(),
                a.getAcctBalInqMsg(),
                // Ship the RAW UTC `yyyyMMddHHmmss` wall-clock — the frontend
                // owns the single UTC→Vietnam-local conversion (composer
                // "Last updated time" via `formatUtcDtmByLanguage`, main-page
                // "Last Refresh" via `humanizeDatetime`, which parses UTC).
                // This mirrors Account Inquiry (`AccountService`), which also
                // ships raw UTC for the same `ACCT_BAL_*_DTM` columns. Doing
                // the shift here as well produced a double-shift on the
                // relative-time cell (it re-parsed the pre-shifted value as
                // UTC), so the conversion is now done exactly once, on the FE.
                a.getAcctBalUpdDtm(),
                a.getAcctBalLstInqDtm(),
                a.getBankCertSeq() == null ? null : a.getBankCertSeq().toString(),
                certInfoCon,
                certInfoDetlCon,
                certInfoDetlCon2,
                nxisasGbSeqNo,
                canTransfer);
    }

    /** Batch-load every {@link BankCertInfo} row referenced by the
     *  source rows' {@code BANK_CERT_SEQ} values. Avoids the N+1 query
     *  pattern when projecting cert fields per row.
     *
     *  <p>Implementation note: {@link BankCertInfoRepository} doesn't
     *  expose a batch-by-seq finder yet, so we filter the tenant's
     *  cert list once and index by seq. Tenant cert tables are small
     *  (single-digit to ~30 rows) so the full list is cheap. If a
     *  future tenant grows beyond ~100 cert rows, add a
     *  {@code findByBizKeyAndSeqNoIn} query and switch to it. */
    private Map<BigDecimal, BankCertInfo> loadBankCerts(String bizKey, List<AcctMstr> rows) {
        final Set<BigDecimal> wantedSeqs = new HashSet<>();
        for (AcctMstr a : rows) {
            if (a.getBankCertSeq() != null) {
                wantedSeqs.add(BigDecimal.valueOf(a.getBankCertSeq()));
            }
        }
        if (wantedSeqs.isEmpty()) return Collections.emptyMap();
        final List<BankCertInfo> all = bankCertInfoRepo.findByBizKeyOrderBySeqNoAsc(bizKey);
        final Map<BigDecimal, BankCertInfo> bySeq = new HashMap<>(all.size());
        for (BankCertInfo b : all) {
            if (b.getSeqNo() != null && wantedSeqs.contains(b.getSeqNo())) {
                bySeq.put(b.getSeqNo(), b);
            }
        }
        return bySeq;
    }

    /**
     * Resolve the credential an account is registered against — with a
     * {@code BANK_CD} equality guard.
     *
     * <p>{@code BANK_CERT_INFO}'s PK is {@code (BIZ_KEY, SEQ_NO)}: the seq is
     * tenant-global, shared by every bank the tenant has credentials for.
     * Neither legacy's IDO {@code BANK_CERT_INFO_R006} nor
     * {@link #loadBankCerts} filters {@code BANK_CD}, so a stale
     * {@code ACCT_MSTR.BANK_CERT_SEQ} can resolve a DIFFERENT bank's login ID
     * and password. Projecting those onto this account would make the browser
     * engine attempt a login at bank A with bank B's credentials — a
     * guaranteed authentication failure that feeds the bank's 3-strike
     * lockout.
     *
     * <p>A cross-bank hit is therefore treated as "no credential": the row
     * projects null cert fields and the frontend gate disables Transfer with
     * the register-credentials message, exactly as an unlinked account does.
     *
     * <p>Deliberate divergence from legacy (approved 2026-08-19) — legacy has
     * no such guard. See
     * {@code docs/bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md}.
     */
    private BankCertInfo resolveCertForAccount(AcctMstr a, Map<BigDecimal, BankCertInfo> certBySeq) {
        if (a.getBankCertSeq() == null) return null;
        final BankCertInfo cert = certBySeq.get(BigDecimal.valueOf(a.getBankCertSeq()));
        if (cert == null) return null;
        if (cert.getBankCd() != null
                && a.getBankCd() != null
                && !cert.getBankCd().equals(a.getBankCd())) {
            log.warn("oat cert bank mismatch — acct {}/{} points at BANK_CERT_SEQ={} owned by bank {}; treating as no-cert",
                    a.getBankCd(), a.getAcctNo(), a.getBankCertSeq(), cert.getBankCd());
            return null;
        }
        return cert;
    }

    /** Run {@link BankCertCryptoService#decrypt(String)} but swallow
     *  failures into a null projection — a cert column the crypto
     *  service rejects must not crash the whole page load. The
     *  decrypt path itself is already plaintext-tolerant for R&D
     *  seed data; this is just defence in depth. */
    private String decryptOrNull(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) return null;
        try {
            return bankCertCrypto.decrypt(ciphertext);
        } catch (Exception ex) {
            log.warn("oat source-row cert decrypt failed — projecting as null", ex);
            return null;
        }
    }

    /** Strict decrypt for the login password column ({@code CERT_INFO_DETL_CON}).
     *  Unlike {@link #decryptOrNull(String)} this never echoes an
     *  undecryptable value — a blob that doesn't decrypt under the legacy
     *  key (corrupt row, or one written by the retired AES-GCM cipher)
     *  projects as null so {@code loginPWD} is empty and the composer's
     *  {@code hasSavedBankCert} pre-flight blocks the engine call. Without
     *  this, the ciphertext was forwarded to the bank as the password,
     *  surfacing as an opaque institution error (Shinhan {@code 8000FFFF}).
     *  Cure for a logged failure: re-save the credential via
     *  Settings &gt; Login Credentials. */
    private String decryptPasswordOrNull(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) return null;
        String plain = bankCertCrypto.decryptStrict(ciphertext);
        if (plain == null) {
            log.warn("oat source-row login password failed strict decrypt — credential is not "
                    + "legacy-cipher ciphertext (re-save via Settings > Login Credentials). "
                    + "Projecting loginPWD as null so the engine is never sent an undecryptable value.");
        }
        return plain;
    }

    private String resolveBankName(String bankCd) {
        if (bankCd == null || bankCd.isBlank()) return null;
        // UI display change (REQUESTED — not a parity fix): show the legacy
        // SHORT bank name wb_get_bank_nm(BANK_CD,'s') (e.g. "Shinhan VN",
        // "VIETIN") instead of the full RPRS_BANK_NM ("Shinhan(Vietnam)").
        // BankCdResolver.resolveBankNm wraps wb_get_bank_nm(bank_cd,'s') via
        // LegacyFunctionRepository and already falls back to the bank code
        // when no name resolves. Display-only; no transfer/payment logic uses
        // this value.
        return bankCdResolver.resolveBankNm(bankCd);
    }

    private void appendAudit(String action, String refNo, String refNo1, String refNo2) {
        auditLog.append(AuditLogContext.builder()
                .wsvcId(OwnAccountTransferConstants.WSVC_SLUG)
                .refNo(refNo)
                .refNo1(refNo1 == null ? "" : refNo1)
                .refNo2(refNo2)
                .activityKey("audit.own_account_transfer.activity")
                .detailKey("audit.own_account_transfer." + action.toLowerCase() + ".detail")
                .msgArgs(new Object[] { refNo, refNo1, refNo2 })
                .build());
    }

    private static String requireBizKey() {
        final String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) throw new BusinessException("WBE9999");
        return bizKey;
    }

    private static String requireUserId() {
        final String userId = TenantContext.currentUserId();
        if (userId == null || userId.isBlank()) throw new BusinessException("WBE9999");
        return userId;
    }

    private static String utcNow14() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FORMAT);
    }


    /* ---------- value-type helpers ---------- */

    private record AcctKey(String bankCd, String acctNo) {
        AcctKey {
            // Defensive — keys must be non-null to use as Map key.
            Objects.requireNonNull(bankCd, "bankCd");
            Objects.requireNonNull(acctNo, "acctNo");
        }
    }

    private record GrantFlags(boolean hasV, boolean hasW) {
        static final GrantFlags EMPTY = new GrantFlags(false, false);
        boolean hasAny() { return hasV || hasW; }
        GrantFlags with(String authType) {
            if (OwnAccountTransferConstants.AUTH_TYPE_WITHDRAW.equals(authType)) {
                return new GrantFlags(hasV, true);
            }
            if (OwnAccountTransferConstants.AUTH_TYPE_VIEW.equals(authType)) {
                return new GrantFlags(true, hasW);
            }
            return this;
        }
    }
}
