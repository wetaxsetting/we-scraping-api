package com.kosign.wecms.common.auditlog.service;

import com.kosign.wecms.common.auditlog.entity.AuditLogsInfm;
import com.kosign.wecms.common.auditlog.repository.AuditLogEntryRepository;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Cross-feature shared writer for {@code AUDIT_LOGS_INFM}. Single
 * source of truth for the locale-fan-out + seq_no-allocation +
 * REQUIRES_NEW transaction semantics that every CRUD-with-audit
 * feature (firm-banking-settings, future purpose / approval-line /
 * contacts / …) relies on.
 *
 * <p>One {@link #append(AuditLogContext)} call writes <b>one row per
 * supported locale</b> ({@link #SUPPORTED_LOCALES} = EN, KO, VI, KM)
 * for a single business event. All rows share the same
 * {@code audit_logs_seq_no} (verified DEV-side: legacy JEX writes
 * one seq_no per event, reused across the per-locale rows). The PK
 * {@code (biz_key, audit_logs_seq_no, wsvc_id, lang_cd)} stays unique
 * because {@code lang_cd} differs per row.
 *
 * <h2>Transaction semantics</h2>
 *
 * Method is annotated {@link Transactional @Transactional(propagation
 * = REQUIRES_NEW)} per Phase 1 §11.1. The audit insert lives in its
 * <b>own transaction</b>, separate from the caller's business write:
 * <ul>
 *   <li>Caller's tx commits → audit tx commits (or fails → see below).</li>
 *   <li>Caller's tx rolls back → audit tx <b>still commits</b> if it
 *       had completed successfully. Audit is a forensic record; it
 *       must outlive the business outcome it describes.</li>
 *   <li>Audit tx fails (e.g. seq_no race, DB outage) → caught
 *       internally, logged at WARN, <b>swallowed</b>. Caller's tx
 *       proceeds unaffected. Phase 1 §11.1 + §13 OQ-6 explicitly
 *       call this trade-off out: a missed audit row is preferable
 *       to a stranded business write.</li>
 * </ul>
 *
 * <h2>Locale fan-out</h2>
 *
 * For each entry in {@link #SUPPORTED_LOCALES} the writer resolves
 * {@link AuditLogContext#activityKey()} and
 * {@link AuditLogContext#detailKey()} via
 * {@link MessageResolver#resolve(String, Locale, Object...)} and
 * inserts one {@link AuditLogsInfm} row. The legacy JEX detail-audit
 * query at {@code comm_0220_003_r001_act.jsp:74-86} filters by
 * {@code lang_cd}, so each user sees pre-localized strings without
 * server-side re-translation. KM is included even though DEV's
 * Vietnam tenant only has EN/KO/VI rows — the refactor extends the
 * coverage going forward; pre-existing JEX rows stay as-is.
 *
 * <h2>Seq_no allocation</h2>
 *
 * Allocated <b>once</b> per call via {@link LegacyFunctionRepository#nextAuditLogsSeqNo},
 * the single funnel to the legacy {@code wb_get_next_val} PL/pgSQL
 * function (V140 #17 carries the {@code audit_logs_infm} branch).
 * The legacy function uses {@code MAX(seq_no::numeric) + 1} — race-
 * prone under high concurrency (two parallel callers read the same
 * MAX, both insert seq+1, the second insert fails the PK). The
 * refactor accepts the race for parity. A migration to
 * {@code nextval('seq')} is a future hardening item if contention
 * becomes real.
 *
 * <h2>Tenant scoping</h2>
 *
 * {@code biz_key} comes from {@link TenantContext}. {@code reg_id}
 * (user id) and {@code reg_nm} (display name) come from the same
 * source — the JWT filter populates both since this MR.
 * {@code reg_dtm} is server time at append-call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    /** Supported locales for the audit-row fan-out. Order is stable
     *  but not semantically significant — the audit-read query
     *  filters by the caller's locale, so the iteration order only
     *  affects insert order. EN comes first to match legacy JEX
     *  iteration order ({@code List<LangCd> {EN, KO, local}}). */
    static final List<LangCd> SUPPORTED_LOCALES =
            List.of(LangCd.EN, LangCd.KO, LangCd.VI, LangCd.KM);

    /** Legacy {@code yyyyMMddHHmmss} format used by every audit
     *  {@code reg_dtm} value DEV-side. Server time at append, in UTC
     *  to match {@code DateTimeUtils.utcNow()} on the JEX side. */
    private static final DateTimeFormatter REG_DTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AuditLogEntryRepository  entryRepository;
    private final LegacyFunctionRepository legacyFunctions;
    private final MessageResolver          messages;

    /**
     * Append one audit event — fans out to N rows (one per locale)
     * inside a {@code REQUIRES_NEW} transaction. See class Javadoc
     * for transaction semantics + failure handling.
     *
     * <p>This method <b>never throws</b>. Validation failures (missing
     * tenant context, missing required field on the context object)
     * and DB failures (seq_no race, connection lost, constraint
     * violation) are all caught internally and logged at WARN. The
     * caller's business write is the source of truth for "did the
     * operation succeed"; an audit gap is observable in logs (grep
     * for the trace_id) but does not affect the caller.
     *
     * @param ctx required — describes the event being audited. See
     *            {@link AuditLogContext} Javadoc for field semantics.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(AuditLogContext ctx) {
        try {
            doAppend(ctx);
        } catch (RuntimeException ex) {
            // Catch-and-log: an audit failure must not propagate up
            // and disturb the caller. The REQUIRES_NEW propagation
            // guarantees the audit tx is independent, but a thrown
            // exception would still surface in the caller's stack
            // and could trigger their own @Transactional rollback if
            // they don't handle it. Swallowing here keeps the
            // contract clean.
            log.warn("audit-log append failed wsvcId={} refNo={} refNo1={} refNo2={}",
                    safe(ctx, AuditLogContext::wsvcId),
                    safe(ctx, AuditLogContext::refNo),
                    safe(ctx, AuditLogContext::refNo1),
                    safe(ctx, AuditLogContext::refNo2),
                    ex);
        }
    }

    /* -----------------------------------------------------------------
     *  Internal — actual write path (extracted for clean catch/log)
     * ----------------------------------------------------------------- */

    private void doAppend(AuditLogContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("AuditLogContext is required");
        }
        requireField("wsvcId",    ctx.wsvcId());
        requireField("refNo",     ctx.refNo());
        requireField("refNo1",    ctx.refNo1());
        requireField("detailKey", ctx.detailKey());

        String bizKey = TenantContext.currentBizKey();
        String regId  = TenantContext.currentUserId();
        String regNm  = TenantContext.currentUserNm();
        if (bizKey == null || regId == null) {
            throw new IllegalStateException(
                    "TenantContext is not populated — audit-log append "
                  + "is only valid for an authenticated request");
        }
        // userNm should never be null after the JWT-filter change —
        // but defend anyway. The audit row's REG_NM column is NOT
        // NULL per V63; falling back to userId keeps the constraint
        // satisfied without a noisy NPE.
        if (regNm == null) regNm = regId;

        // Allocate via the single LegacyFunctionRepository funnel (V140 #17) —
        // no scattered native @Query. Positional args map to $3/$4/$5 exactly as
        // the previous AuditLogSeqNoRepository call did, preserving behaviour.
        String seqNoRaw = legacyFunctions.nextAuditLogsSeqNo(
                bizKey, ctx.refNo(), ctx.refNo1());
        if (seqNoRaw == null) {
            // Should never happen — the V140 #17 branch always returns a
            // value when its presence-checks pass. Defensive guard.
            throw new IllegalStateException(
                    "wb_get_next_val returned null for audit_logs_infm seq_no");
        }
        BigDecimal seqNo = new BigDecimal(seqNoRaw);
        String regDtm = OffsetDateTime.now(ZoneOffset.UTC).format(REG_DTM_FORMAT);
        Object[] args = ctx.msgArgs() == null ? EMPTY_ARGS : ctx.msgArgs();

        for (LangCd lang : SUPPORTED_LOCALES) {
            Locale locale = lang.toLocale();
            String activity = ctx.activityKey() == null || ctx.activityKey().isBlank()
                    ? null
                    : messages.resolve(ctx.activityKey(), locale, args);
            String detail = messages.resolve(ctx.detailKey(), locale, args);

            AuditLogsInfm entry = new AuditLogsInfm();
            entry.setBizKey(bizKey);
            entry.setAuditLogsSeqNo(seqNo);
            entry.setWsvcId(ctx.wsvcId());
            entry.setLangCd(lang.name());
            entry.setRefNo(ctx.refNo());
            entry.setRefNo1(ctx.refNo1());
            entry.setRefNo2(ctx.refNo2());
            entry.setAuditLogsActv(activity);
            entry.setAuditLogsActvDtl(detail);
            entry.setRegId(regId);
            entry.setRegNm(regNm);
            entry.setRegDtm(regDtm);
            entryRepository.save(entry);
        }
        log.debug("audit-log append wsvcId={} refNo={} refNo1={} refNo2={} seqNo={} locales={}",
                ctx.wsvcId(), ctx.refNo(), ctx.refNo1(), ctx.refNo2(),
                seqNo, SUPPORTED_LOCALES.size());
    }

    private static final Object[] EMPTY_ARGS = new Object[0];

    private static void requireField(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "AuditLogContext." + name + " is required");
        }
    }

    /** Null-safe context-field reader for the catch-block log line.
     *  Avoids cascading NPEs when the failure was triggered by a
     *  null context field in the first place. */
    private static String safe(AuditLogContext ctx,
                                java.util.function.Function<AuditLogContext, String> getter) {
        if (ctx == null) return null;
        try {
            return getter.apply(ctx);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
