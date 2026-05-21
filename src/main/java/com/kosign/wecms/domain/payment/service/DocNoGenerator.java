package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfmId;
import com.kosign.wecms.domain.setting.environmentsettings.entity.SysEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.repository.BusiEnvSetInfmRepository;
import com.kosign.wecms.domain.setting.environmentsettings.repository.SysEnvSetInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates the next {@code DOC_NO} for {@code EXPT_RESOL_MSTR} following the
 * legacy {@code VO_YYMMDD_NNN} pattern.
 *
 * <p>Mirrors legacy {@code ReslHelper.getDocNo} →
 * {@code SeqHelper.getNextVal("EXPT_RESOL_MSTR", "doc_no", bizKey)} →
 * {@code wb_get_next_val('expt_resol_mstr', 'doc_no', bizKey)} as defined
 * in {@code V25__wb_get_next_val_full_port.sql:184-240}.
 *
 * <h2>Pattern breakdown</h2>
 * <pre>
 *   {prefix}_{date}_{seq}    e.g.  VO_260512_001
 *     prefix  — from SYS_ENV_SET_INFM env_set_inf='00071', default "VO"
 *     date    — current date in Vietnam timezone, format from env_set_inf='00072'
 *                 (legacy PostgreSQL YYMMDD → Java yyMMdd), default "YYMMDD"
 *     seq     — per-tenant daily MAX+1 of trailing numeric part of existing
 *                 DOC_NO rows, zero-padded to length(env_set_inf='00073'), default 3
 * </pre>
 *
 * <p>Per-tenant overrides are applied only when {@code BUSI_ENV_SET_INFM}
 * env_set_inf='00070' = 'Y' for the tenant.
 *
 * <p><strong>Concurrency note:</strong> {@code DOC_NO} carries no unique
 * constraint on {@code EXPT_RESOL_MSTR} (the PK is {@code (BIZ_KEY,
 * APPR_SEQ_NO)}). Two concurrent requests on the same date may therefore
 * receive the same {@code DOC_NO} in an extreme race — this is accepted
 * legacy parity (the original DB function has the same race window). The
 * caller does NOT need to retry on {@code DataIntegrityViolationException}
 * for this value.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocNoGenerator {

    /** ENV_SET_INF code: per-tenant override toggle ("Y" = use tenant settings). */
    private static final String ENV_TOGGLE     = "00070";
    /** ENV_SET_INF code: doc_no prefix (default "VO"). */
    private static final String ENV_PREFIX     = "00071";
    /** ENV_SET_INF code: date format string (default "YYMMDD"). */
    private static final String ENV_DATE_FMT   = "00072";
    /** ENV_SET_INF code: initial seq value — its length is the zero-pad width (default "001" → 3). */
    private static final String ENV_SEQ_INIT   = "00073";

    private static final String  DEFAULT_PREFIX   = "VO";
    private static final String  DEFAULT_DATE_FMT = "YYMMDD";
    private static final int     DEFAULT_SEQ_LEN  = 3;
    private static final ZoneId  VIETNAM_ZONE     = ZoneId.of("Asia/Ho_Chi_Minh");

    private final SysEnvSetInfmRepository  sysEnvRepo;
    private final BusiEnvSetInfmRepository busiEnvRepo;
    private final ExptResolMstrRepository  exptResolMstrRepository;

    /**
     * Returns the next {@code DOC_NO} string for the given tenant.
     *
     * <p>Reads env settings (with tenant-override logic), formats the
     * current date in Vietnam timezone, and queries the MAX existing
     * trailing-sequence value for today's date prefix before incrementing.
     */
    @Transactional(readOnly = true)
    public String nextDocNo(String bizKey) {
        boolean tenantOverride = isTenantOverrideEnabled(bizKey);

        String prefix      = resolve(bizKey, ENV_PREFIX,   DEFAULT_PREFIX,   tenantOverride);
        String dateFmtRaw  = resolve(bizKey, ENV_DATE_FMT, DEFAULT_DATE_FMT, tenantOverride);
        String seqInit     = resolve(bizKey, ENV_SEQ_INIT, "001",            tenantOverride);
        int    seqLen      = (seqInit != null && !seqInit.isBlank()) ? seqInit.length() : DEFAULT_SEQ_LEN;

        String formattedDate = LocalDate.now(VIETNAM_ZONE)
                .format(DateTimeFormatter.ofPattern(pgToJavaDateFormat(dateFmtRaw)));

        // "VO_260512_" — everything before the sequential part
        String datePrefix = prefix + "_" + formattedDate + "_";

        Long maxSeq = exptResolMstrRepository.findMaxDocNoSeqForDate(bizKey, datePrefix);
        long nextSeq = (maxSeq == null ? 0L : maxSeq) + 1L;

        String result = datePrefix + String.format("%0" + seqLen + "d", nextSeq);
        log.debug("feature=doc-no-generator bizKey={} result={}", bizKey, result);
        return result;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private boolean isTenantOverrideEnabled(String bizKey) {
        return busiEnvRepo.findById(new BusiEnvSetInfmId(bizKey, ENV_TOGGLE))
                .map(b -> "Y".equalsIgnoreCase(b.getEnvSetCont()))
                .orElse(false);
    }

    private String resolve(String bizKey, String code, String fallback, boolean tenantOverride) {
        if (tenantOverride) {
            var opt = busiEnvRepo.findById(new BusiEnvSetInfmId(bizKey, code));
            if (opt.isPresent()) {
                String v = opt.get().getEnvSetCont();
                if (v != null && !v.isBlank()) return v;
            }
        }
        return sysEnvRepo.findById(code)
                .map(SysEnvSetInfm::getEnvSetCont)
                .filter(v -> !v.isBlank())
                .orElse(fallback);
    }

    /**
     * Converts a PostgreSQL date format string (e.g. {@code YYMMDD}) to the
     * equivalent Java {@link DateTimeFormatter} pattern (e.g. {@code yyMMdd}).
     *
     * <p>Legacy DEV uses only {@code YYMMDD} (2-digit year + month + day).
     * {@code YYYY} → {@code yyyy} is supported defensively in case a tenant
     * switches to 4-digit year.
     */
    private static String pgToJavaDateFormat(String pgFmt) {
        if (pgFmt == null || pgFmt.isBlank()) return "yyMMdd";
        return pgFmt
                .replace("YYYY", "yyyy")
                .replace("YY", "yy")
                .replace("DD", "dd");
        // "MM" stays as-is — same in both PostgreSQL and Java
    }
}
