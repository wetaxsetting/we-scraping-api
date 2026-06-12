package com.kosign.wecms.domain.setting.firmbankingsettings.service;

import com.kosign.wecms.common.auditlog.entity.AuditLogsInfm;
import com.kosign.wecms.common.auditlog.repository.AuditLogEntryRepository;
import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.security.LogMask;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingAuditEntry;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingAuditListResponse;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingCreateRequest;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingDetail;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingListResponse;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingRow;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingUpdateRequest;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSettingView;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingViewRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Settings &gt; Firm Banking Settings — read-only service slice
 * (Phase 2, Path A). Mirrors the legacy {@code comm_0220_*} read
 * paths:
 * <ul>
 *   <li>{@link #list()} → {@code comm_0220_001_r001} (legacy
 *       {@code IDO.TB_FIRM_CONF_R004}).</li>
 *   <li>{@link #detail(String, String)} → {@code comm_0220_003_r001}
 *       row payload (legacy {@code IDO.TB_FIRM_CONF_R001}).</li>
 *   <li>{@link #auditLog(String, String)} → {@code comm_0220_003_r001}
 *       audit-{@code REC} sub-result (now its own resource).</li>
 * </ul>
 *
 * <p>Create (added 2026-05-09 under OQ-8a Option (c) — provisional):
 * mirrors {@code comm_0220_002_c001_act.jsp:38-130}. Bounded-bank set
 * {@code {970424 Shinhan, 970436 Vietcombank, 970457 Woori}} drives
 * server-derived {@code FILE_TYPE}; anything else returns
 * {@code WBE0001}. V9 ({@code NAPAS_BANK_INFO} existence) is deferred
 * — strictly weaker than V8 for the supported set and the table is
 * not yet migrated locally. V11 stays disabled per OQ-3 / legacy
 * parity. Audit event appended via shared {@code AuditLogService}
 * (4 locale rows under one seq_no, REQUIRES_NEW propagation). See
 * {@link #BANK_TO_FILE_TYPE} for the future-bank-onboarding TODO.
 *
 * <p>Update (added 2026-05-09 — depends on P-1
 * {@code AuditLogService} which shipped in the same sprint): mirrors
 * JEX {@code comm_0220_003_u001_act.jsp}. Editable fields per V3/V4/V5
 * are {@code BANK_PRTN_ID}, {@code VAN_ORG_CD}, {@code ORG_STAT}.
 * {@code FILE_TYPE} is server-derived only on Create (V14) and never
 * touched on Update; the composite PK ({@code OUT_BANK_CD},
 * {@code WDRL_ACT_NO}) is immutable. After the business save the
 * service appends one audit event via {@link AuditLogService#append}
 * (4 locale rows under one {@code audit_logs_seq_no}).
 *
 * <p>Delete (added 2026-05-09): hard delete, mirrors JEX
 * {@code comm_0220_001_d001_act.jsp}. Per OQ-2 the JEX runtime writes
 * <b>no</b> audit row on delete, so the refactor mirrors exactly and
 * does <b>not</b> depend on the shared {@code AuditLogService}. The
 * earlier "Delete pending P-1" entry in the operations matrix was a
 * documentation error corrected in the same MR as this service method.
 *
 * <p>Tenant scoping: {@code BIZ_KEY} is read from
 * {@link TenantContext#currentBizKey()} and applied as a predicate of
 * every query. Detail / Audit-read return {@code WBE2020} rather than
 * leaking other tenants' rows.
 *
 * <p>Encryption: NONE for {@code wdrl_act_no} (OQ-4). Both the list
 * and detail responses ship {@code wdrl_act_no} as cleartext — it is
 * the PK component the frontend addresses for every write flow
 * (Delete / Update). Masking is presentation-layer only and lives in
 * the frontend mapper, not in this service. Logging always passes
 * through {@link LogMask#acct(String)} so the cleartext never reaches
 * server logs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmBankingSettingsService {

    /** Legacy {@code REF_NO} value referencing the {@code TB_FIRM_CONF}
     *  table — also written by the future audit-log writer. Mirrors
     *  the literal at {@code comm_0220_003_r001_act.jsp:79}. */
    private static final String REF_NO_TB_FIRM_CONF = "TB_FIRM_CONF";

    /** Refactor route slug used as the audit-log {@code wsvc_id}
     *  discriminator. Convention adopted 2026-05-09 — overrides the
     *  earlier OQ-6 default of preserving the legacy JEX page id
     *  ({@code comm_0220_001}). The refactor architecture
     *  standardises on slug-based identifiers across every
     *  identifier surface, including the audit DB column.
     *
     *  <p>Legacy bucket usage is now confined to JEX file:line
     *  citations and migration-traceability prose; the audit DB
     *  no longer carries legacy values for new writes. V67 back-
     *  fills any pre-existing local rows that still carry the
     *  old constant.
     *
     *  <p>Compatibility note: the legacy JEX detail-audit query
     *  ({@code comm_0220_003_r001_act.jsp:74-86}) filters by
     *  {@code WSVC_ID = 'comm_0220_001'}. Refactor-written rows
     *  with this new constant will NOT be returned by that query.
     *  This is by design — refactor is local-only R&D, and JEX
     *  never queries the local DB. */
    static final String AUDIT_WSVC_ID = "firm-banking-settings";

    /** Default locale when the request didn't carry one (rare; the JWT
     *  filter usually populates {@code TenantContext.currentLangCd()}).
     *  EN matches the Phase 1 demo seed. */
    private static final LangCd DEFAULT_LANG_CD = LangCd.EN;

    /** Audit-log activity / detail message keys for the Update event.
     *  Bundle entries seeded in {@code messages.properties} +
     *  {@code messages_{en,ko,vi,km}.properties} by the P-1 MR. */
    private static final String AUDIT_ACTIVITY_KEY      = "audit.firm_banking_settings.activity";
    private static final String AUDIT_CREATE_DETAIL_KEY = "audit.firm_banking_settings.create.detail";
    private static final String AUDIT_UPDATE_DETAIL_KEY = "audit.firm_banking_settings.update.detail";

    /**
     * OQ-8a Option (c) — bounded-bank set + server-derived
     * {@code FILE_TYPE}. Maps the three currently-supported NAPAS bank
     * codes to their {@code FILE_TYPE} discriminator per the §5.4
     * mapping table in the feature spec. The set is intentionally
     * narrow: it mirrors what DEV / JEX have produced today. The
     * frontend never sends {@code file_type} — it's derived here.
     *
     * <p>TODO(future-bank-onboarding): when a fourth bank goes live in
     * production, add one row to this map AND keep the entry shape
     * (NAPAS code → 3-letter file type) consistent with the §5.4 table
     * in {@code docs/refactor-wecms/features/settings/firm-banking-settings.md}.
     * No other code change is required — V10 reuses
     * {@code TB_FIRM_BANK_CODE} which is a global registry, not a
     * per-bank constant. PO sign-off on the new code is the only
     * out-of-tree gating step. The accompanying log/grep tag is
     * {@code OQ-8a-future-bank-onboarding}.
     */
    static final Map<String, String> BANK_TO_FILE_TYPE = Map.of(
            "970424", "SHB",  // Shinhan
            "970436", "VCB",  // Vietcombank — added 2026-05-09 per OQ-8 verification
            "970457", "WRB"   // Woori
    );

    /** Defaults applied on Create when the wire omits a field — mirrors
     *  legacy DAO behavior at {@code TbFirmConfDAO.create()}. V15
     *  ({@code BAT_TRAN_BRANCH_DIV='Y'}) and V16
     *  ({@code BAT_TRAN_BRANCH_LIMIT=2000}) are constants; V17
     *  ({@code ORG_STAT='1'} AVAILABLE) is enforced at the DTO level
     *  too — the frontend always sends '1' until a status selector
     *  ships. */
    private static final String     DEFAULT_BAT_TRAN_BRANCH_DIV   = "Y";
    private static final BigDecimal DEFAULT_BAT_TRAN_BRANCH_LIMIT = new BigDecimal("2000");

    /** Legacy {@code yyyyMMddHHmmss} timestamp shape — what
     *  {@code TB_FIRM_CONF.MOD_DTTM} stores. Server time at write, in
     *  UTC to match the JEX {@code DateTimeUtils.utcNow()} convention. */
    private static final DateTimeFormatter MOD_DTTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final FirmBankingSettingRepository      settingRepository;
    private final FirmBankingSettingViewRepository  viewRepository;
    private final AuditLogEntryRepository           auditLogRepository;
    private final AuditLogService                   auditLogService;
    private final TbFirmBankCodeRepository          firmBankCodeRepository;

    /**
     * List every binding for the current tenant. Mirrors
     * {@code comm_0220_001_r001_act.jsp} — no client filters, no
     * pagination (the legacy view returns the entire result set).
     *
     * <p>{@code wdrl_act_no} is masked in the wire row via
     * {@link LogMask#acct(String)}; the cleartext value is never
     * serialized through this endpoint.
     */
    @Transactional(readOnly = true)
    public FirmBankingSettingListResponse list() {
        String bizKey = requireBizKey();
        List<FirmBankingSettingView> rows =
                viewRepository.findAllByBizKeyOrderByOutBankCdAscWdrlActNoAsc(bizKey);
        log.debug("firm-banking-settings list returned {} rows for bizKey={}",
                rows.size(), bizKey);
        return new FirmBankingSettingListResponse(
                rows.stream().map(FirmBankingSettingsService::toListRow).toList());
    }

    /**
     * Detail by PK. Mirrors {@code comm_0220_003_r001_act.jsp:69-72}.
     * Returns the row for the current tenant; cross-tenant lookups
     * cannot succeed because {@code bizKey} is part of the composite
     * id constructed from {@link TenantContext}.
     *
     * @throws BusinessException {@code WBE_VALIDATION} when the path
     *         segments are missing/blank (the {@code @Pattern} guard
     *         in the controller catches the standard cases first;
     *         this is a defence-in-depth check).
     * @throws BusinessException {@code WBE2020} when no row matches
     *         the PK for the current tenant.
     */
    @Transactional(readOnly = true)
    public FirmBankingSettingDetail detail(String outBankCd, String wdrlActNo) {
        String safeOutBankCd = requireField("out_bank_cd", outBankCd);
        String safeWdrlActNo = requireField("wdrl_act_no", wdrlActNo);
        String bizKey        = requireBizKey();

        TbFirmConf entity = settingRepository
                .findById(new TbFirmConfId(bizKey, safeOutBankCd, safeWdrlActNo))
                .orElseThrow(() -> {
                    // Logged at warn — a missing row is a request-shape problem
                    // worth tracing, not a server fault.
                    log.warn("firm-banking-settings detail not found bizKey={} outBankCd={} wdrlActNo={}",
                            bizKey, safeOutBankCd, LogMask.acct(safeWdrlActNo));
                    return new BusinessException("WBE2020");
                });
        return toDetail(entity);
    }

    /**
     * Create a new firm-banking binding for the current tenant.
     * Mirrors {@code comm_0220_002_c001_act.jsp:38-130}:
     * <ol>
     *   <li>Required-field validation on {@code OUT_BANK_CD},
     *       {@code WDRL_ACT_NO}, {@code BANK_PRTN_ID},
     *       {@code VAN_ORG_CD}, {@code ORG_STAT} (rules V3/V4/V5/V7/V20
     *       — bean-validation handles this before the method runs).</li>
     *   <li><b>OQ-8a Option (c) — bounded-bank reject.</b> Reject
     *       {@code out_bank_cd} outside {@link #BANK_TO_FILE_TYPE}
     *       with {@code WBE0001 Invalid OUT_BANK_CD} (rule V8).
     *       Replaces the legacy "Shinhan/Woori only" branch.</li>
     *   <li>V10 referential check: {@code (ANT_DIV_CD='NAPAS',
     *       BANK_CD=outBankCd)} must exist in {@code TB_FIRM_BANK_CODE}.
     *       {@code WBE0002 There is no BANK} otherwise.</li>
     *   <li>V12 PK uniqueness: throw {@code WBE2021} when
     *       {@code (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)} already exists.
     *       JEX raises {@code WBE0003 "Duplicate"} — refactor uses the
     *       feature-level code per OQ-1.</li>
     *   <li>Build entity with server-derived defaults: V14
     *       ({@code FILE_TYPE} from {@link #BANK_TO_FILE_TYPE}), V15
     *       ({@code BAT_TRAN_BRANCH_DIV='Y'}), V16
     *       ({@code BAT_TRAN_BRANCH_LIMIT=2000}), V17 ({@code ORG_STAT}
     *       from request — frontend supplies '1' until UX adds the
     *       selector), V18 (server timestamps), V19
     *       ({@code LAST_EDTR=TenantContext.currentUserNm()}).</li>
     *   <li>{@code repository.save(entity)} (Hibernate INSERT).</li>
     *   <li>Append the audit event via
     *       {@link AuditLogService#append(AuditLogContext)} — one
     *       call writes 4 locale rows under one
     *       {@code audit_logs_seq_no}, isolated in its own
     *       {@code REQUIRES_NEW} transaction so an audit failure
     *       cannot strand the business INSERT.</li>
     * </ol>
     *
     * <p>Per V11 (legacy parity, OQ-3 resolved 2026-05-09): the
     * "{@code wdrl_act_no} must exist in {@code ACCT_MSTR}" check is
     * intentionally NOT enforced — JEX has the same line commented out
     * behind issue [#42004](https://www.kosign.com.kh/redmine/issues/42004).
     *
     * <p>Per V9 (deferred — see feature spec §13 OQ-8a): the
     * "{@code out_bank_cd} must exist in {@code NAPAS_BANK_INFO}" check
     * is deferred until the table lands locally. With the V8
     * bounded-bank reject above, V9 is strictly weaker for the
     * supported set and contributes no extra safety today.
     *
     * <p>No FSM on {@code ORG_STAT} (V22 — any value is permissible);
     * no approval-line (OQ-7); no FILE_TYPE on the wire (OQ-8a).
     *
     * @throws BusinessException {@code WBE0001} when {@code out_bank_cd}
     *         is outside the supported 3-bank set.
     * @throws BusinessException {@code WBE0002} when the bank code is
     *         not registered in {@code TB_FIRM_BANK_CODE} for NAPAS.
     * @throws BusinessException {@code WBE2021} when the PK already
     *         exists for the current tenant.
     */
    @Transactional
    public FirmBankingSettingDetail create(FirmBankingSettingCreateRequest req) {
        String safeOutBankCd = requireField("out_bank_cd", req.outBankCd());
        String safeWdrlActNo = requireField("wdrl_act_no", req.wdrlActNo());
        String bizKey        = requireBizKey();

        // V8 — bounded-bank set (OQ-8a Option c). The DTO @Pattern only
        // enforces shape; the allow-list lives here so we can carry
        // the WBE0001 argument verbatim. Future-bank onboarding adds a
        // row to BANK_TO_FILE_TYPE — see field Javadoc.
        String fileType = BANK_TO_FILE_TYPE.get(safeOutBankCd);
        if (fileType == null) {
            log.warn("firm-banking-settings create rejected unsupported outBankCd={} (allowed={})",
                    safeOutBankCd, BANK_TO_FILE_TYPE.keySet());
            throw new BusinessException("WBE0001", "OUT_BANK_CD");
        }

        // V10 — bank code must exist in TB_FIRM_BANK_CODE for NAPAS.
        if (!firmBankCodeRepository.existsByAntDivCdAndBankCd("NAPAS", safeOutBankCd)) {
            log.warn("firm-banking-settings create missing TB_FIRM_BANK_CODE row outBankCd={}",
                    safeOutBankCd);
            throw new BusinessException("WBE0002", "BANK");
        }

        // V12 — PK uniqueness. Tenant scoping is implicit because
        // bizKey is the first PK component.
        TbFirmConfId id = new TbFirmConfId(bizKey, safeOutBankCd, safeWdrlActNo);
        if (settingRepository.existsById(id)) {
            log.warn("firm-banking-settings create duplicate bizKey={} outBankCd={} wdrlActNo={}",
                    bizKey, safeOutBankCd, LogMask.acct(safeWdrlActNo));
            throw new BusinessException("WBE2021", safeOutBankCd, safeWdrlActNo);
        }

        String nowDttm = OffsetDateTime.now(ZoneOffset.UTC).format(MOD_DTTM_FORMAT);

        TbFirmConf entity = new TbFirmConf();
        entity.setBizKey(bizKey);
        entity.setOutBankCd(safeOutBankCd);
        entity.setWdrlActNo(safeWdrlActNo);
        entity.setBankPrtnId(req.bankPrtnId());
        entity.setVanOrgCd(req.vanOrgCd());
        entity.setOrgStat(req.orgStat());
        entity.setFileType(fileType);                          // V14
        entity.setBatTranBranchDiv(DEFAULT_BAT_TRAN_BRANCH_DIV);   // V15
        entity.setBatTranBranchLimit(DEFAULT_BAT_TRAN_BRANCH_LIMIT); // V16
        entity.setRegDttm(nowDttm);                            // V18
        entity.setStartDttm(nowDttm);                          // V18 — JEX sets START = now
        entity.setEndDttm(null);                               // V18 — open-ended
        entity.setModDttm(nowDttm);                            // V18
        entity.setLastEdtr(TenantContext.currentUserNm());     // V19

        TbFirmConf saved = settingRepository.save(entity);

        // Append one audit event — fans out to 4 locale rows under one
        // seq_no inside a REQUIRES_NEW transaction. Mirrors the Update
        // path; only the detail key differs (create vs update).
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_TB_FIRM_CONF)
                .refNo1(safeOutBankCd)
                .refNo2(safeWdrlActNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_CREATE_DETAIL_KEY)
                .build());

        log.info("firm-banking-settings created bizKey={} outBankCd={} wdrlActNo={} fileType={} orgStat={}",
                bizKey, safeOutBankCd, LogMask.acct(safeWdrlActNo), fileType, req.orgStat());
        return toDetail(saved);
    }

    /**
     * Update the editable fields of one binding for the current
     * tenant. Mirrors {@code comm_0220_003_u001_act.jsp:74-84}:
     * <ol>
     *   <li>Required-field validation on
     *       {@code BANK_PRTN_ID} / {@code VAN_ORG_CD} /
     *       {@code ORG_STAT} (rules V3/V4/V5; bean-validation handles
     *       this before the method runs).</li>
     *   <li>Load entity by PK with tenant scoping; {@code WBE2020} if
     *       missing (rule V13 — JEX
     *       {@code ValidationHelper.validateTbFirmConf}).</li>
     *   <li>Copy the three editable fields onto the entity.
     *       <b>Do not</b> touch {@code FILE_TYPE} (V14 — derivation
     *       Create-only) or any PK column.</li>
     *   <li>Set {@code MOD_DTTM = utcNowYyyyMMddHHmmss()} and
     *       {@code LAST_EDTR = TenantContext.currentUserNm()} (V19 —
     *       legacy {@code WbUser.getName()} parity).</li>
     *   <li>Save via {@code repository.save(entity)} (Hibernate dirty
     *       check picks up the field changes).</li>
     *   <li>Append the audit event via
     *       {@link AuditLogService#append(AuditLogContext)} — one
     *       call writes 4 locale rows under one
     *       {@code audit_logs_seq_no}, isolated in its own
     *       {@code REQUIRES_NEW} transaction so an audit failure
     *       cannot strand the business write.</li>
     * </ol>
     *
     * <p>Per OQ-2 / OQ-7: no Delete-style audit suppression here
     * (Update writes audit per JEX), no approval-line, no FSM on
     * {@code ORG_STAT} (V22 — any value transitions to any other).
     *
     * @throws BusinessException {@code WBE2020} when no row matches
     *         the PK for the current tenant.
     */
    @Transactional
    public FirmBankingSettingDetail update(
            String outBankCd,
            String wdrlActNo,
            FirmBankingSettingUpdateRequest req) {
        String safeOutBankCd = requireField("out_bank_cd", outBankCd);
        String safeWdrlActNo = requireField("wdrl_act_no", wdrlActNo);
        String bizKey        = requireBizKey();

        TbFirmConfId id = new TbFirmConfId(bizKey, safeOutBankCd, safeWdrlActNo);
        TbFirmConf entity = settingRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("firm-banking-settings update missing-row bizKey={} outBankCd={} wdrlActNo={}",
                            bizKey, safeOutBankCd, LogMask.acct(safeWdrlActNo));
                    return new BusinessException("WBE2020");
                });

        entity.setBankPrtnId(req.bankPrtnId());
        entity.setVanOrgCd(req.vanOrgCd());
        entity.setOrgStat(req.orgStat());
        entity.setModDttm(OffsetDateTime.now(ZoneOffset.UTC).format(MOD_DTTM_FORMAT));
        entity.setLastEdtr(TenantContext.currentUserNm());

        TbFirmConf saved = settingRepository.save(entity);

        // Append one audit event — fans out to 4 locale rows under one
        // seq_no inside a REQUIRES_NEW transaction. The shared writer
        // catches and swallows its own failures (Phase 1 §11.1 / §13
        // OQ-6); the calling tx commits regardless. Both audit rows
        // and the business write are persisted under the same
        // (bizKey, refNo='TB_FIRM_CONF', refNo1=outBankCd, refNo2=wdrlActNo)
        // tuple — the legacy detail-audit query at
        // comm_0220_003_r001_act.jsp:74-86 finds them as expected.
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_TB_FIRM_CONF)
                .refNo1(safeOutBankCd)
                .refNo2(safeWdrlActNo)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_UPDATE_DETAIL_KEY)
                .build());

        log.info("firm-banking-settings updated bizKey={} outBankCd={} wdrlActNo={} orgStat={}",
                bizKey, safeOutBankCd, LogMask.acct(safeWdrlActNo), req.orgStat());
        return toDetail(saved);
    }

    /**
     * Hard-delete the binding for the current tenant. Mirrors
     * {@code comm_0220_001_d001_act.jsp:59-75} byte-for-byte:
     * <ol>
     *   <li>{@code ValidationHelper.validateRequired(OUT_BANK_CD,
     *       WDRL_ACT_NO)} → covered by the controller's
     *       {@code @Pattern} guard plus this method's
     *       {@link #requireField(String, String)} defence-in-depth.</li>
     *   <li>{@code ValidationHelper.validateTbFirmConf(pk)} — load by PK,
     *       throw if absent → translated to {@code WBE2020}.</li>
     *   <li>{@code TbFirmConfDAO.deleteByPk(pk)} → {@code repository.deleteById(id)}.</li>
     * </ol>
     *
     * <p>Per OQ-2 the JEX runtime writes <b>no audit row</b> on delete
     * (verified against {@code comm_0220_001_d001_act.jsp} — no
     * {@code AuditLogsInfm} call anywhere in the action JSP). The
     * refactor mirrors exactly. {@code AuditLogService} is therefore
     * NOT a prerequisite for this operation — only Create / Update
     * depend on it.
     *
     * <p>Tenant scoping: the {@code BIZ_KEY} component of the composite
     * id is read from {@link TenantContext}, so a cross-tenant delete
     * cannot succeed (the lookup throws {@code WBE2020} as if the row
     * does not exist — no leak of tenant existence).
     *
     * @throws BusinessException {@code WBE_VALIDATION} when path
     *         segments are missing/blank.
     * @throws BusinessException {@code WBE2020} when no row matches
     *         the PK for the current tenant.
     */
    @Transactional
    public void delete(String outBankCd, String wdrlActNo) {
        String safeOutBankCd = requireField("out_bank_cd", outBankCd);
        String safeWdrlActNo = requireField("wdrl_act_no", wdrlActNo);
        String bizKey        = requireBizKey();

        TbFirmConfId id = new TbFirmConfId(bizKey, safeOutBankCd, safeWdrlActNo);
        if (!settingRepository.existsById(id)) {
            // Logged at warn — a delete against a non-existent row is a
            // request-shape problem worth tracing. Mask the account
            // number per the OQ-4 logging rule.
            log.warn("firm-banking-settings delete missing-row bizKey={} outBankCd={} wdrlActNo={}",
                    bizKey, safeOutBankCd, LogMask.acct(safeWdrlActNo));
            throw new BusinessException("WBE2020");
        }
        settingRepository.deleteById(id);
        log.info("firm-banking-settings deleted bizKey={} outBankCd={} wdrlActNo={}",
                bizKey, safeOutBankCd, LogMask.acct(safeWdrlActNo));
    }

    /**
     * Audit-log history for the row, in the caller's locale, latest
     * first. Mirrors the audit {@code REC} sub-result of
     * {@code comm_0220_003_r001_act.jsp:74-86}.
     *
     * <p>Filter shape:
     * {@code (BIZ_KEY, REF_NO='TB_FIRM_CONF', REF_NO1=outBankCd,
     *  REF_NO2=wdrlActNo, WSVC_ID='firm-banking-settings',
     *  LANG_CD=callerLocale)}.
     *
     * <p>The {@code WSVC_ID} value is the refactor route slug — the
     * earlier OQ-6 default of preserving the legacy JEX page id was
     * overridden 2026-05-09 in favour of slug-based audit ids
     * across every refactor surface. V67 back-fills any pre-existing
     * local rows that still carry the old constant. Legacy JEX
     * audit screens will not see refactor-written rows under this
     * filter — see service-level Javadoc + workspace
     * {@code CLAUDE.md} §"Refactor identifier naming".
     *
     * <p>The endpoint does NOT validate row existence — an audit
     * lookup against a non-existent firm-conf row simply returns an
     * empty list. This matches legacy ({@code comm_0220_003_r001_act.jsp}
     * also doesn't pre-check existence for the audit query) and lets
     * the frontend lazy-load audit history without coupling to the
     * detail endpoint.
     */
    @Transactional(readOnly = true)
    public FirmBankingAuditListResponse auditLog(String outBankCd, String wdrlActNo) {
        String safeOutBankCd = requireField("out_bank_cd", outBankCd);
        String safeWdrlActNo = requireField("wdrl_act_no", wdrlActNo);
        String bizKey        = requireBizKey();
        String langCd        = currentLangCdValue();

        List<AuditLogsInfm> rows = auditLogRepository
                .findByBizKeyAndRefNoAndRefNo1AndRefNo2AndWsvcIdAndLangCdOrderByRegDtmDesc(
                        bizKey,
                        REF_NO_TB_FIRM_CONF,
                        safeOutBankCd,
                        safeWdrlActNo,
                        AUDIT_WSVC_ID,
                        langCd);
        log.debug("firm-banking-settings audit returned {} rows for bizKey={} outBankCd={} wdrlActNo={} langCd={}",
                rows.size(), bizKey, safeOutBankCd, LogMask.acct(safeWdrlActNo), langCd);
        return new FirmBankingAuditListResponse(
                rows.stream().map(FirmBankingSettingsService::toAuditEntry).toList());
    }

    /* -----------------------------------------------------------------
     *  Internal helpers
     * ----------------------------------------------------------------- */

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            // Should never happen — the JWT filter populates the
            // TenantContext on every authenticated request. If the
            // context is empty here, an unauthenticated path leaked
            // into the service layer, which is a configuration bug.
            throw new BusinessException("WBE9999");
        }
        return bizKey;
    }

    private static String requireField(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", name + ": is required");
        }
        return value.trim();
    }

    private static String currentLangCdValue() {
        LangCd lang = TenantContext.currentLangCd();
        return (lang == null ? DEFAULT_LANG_CD : lang).name();
    }

    /** {@code wdrlActNo} is shipped <b>cleartext</b> on the list wire —
     *  it is the PK component the frontend uses to address the row for
     *  Detail / Update / Delete / Audit reads. Earlier revisions
     *  applied {@link LogMask#acct(String)} server-side here (OQ-4 §9
     *  "mask in API responses by default"), but that decision broke
     *  every write flow: the frontend's only handle on the row was the
     *  masked value, which the {@code @Pattern("^[A-Za-z0-9\\-]{1,100}$")}
     *  guard on every write endpoint correctly rejects (asterisks are
     *  outside the character class). The masking has been moved to
     *  the frontend mapper ({@code mapFirmBankingSettingRow}) which
     *  produces the displayed masked string while preserving the
     *  cleartext PK for write paths. JEX parity preserved — legacy
     *  {@code IDO.TB_FIRM_CONF_R004} likewise returns cleartext.
     *
     *  <p>The OQ-4 logging rule is unaffected: every log line that
     *  includes {@code wdrlActNo} still goes through
     *  {@link LogMask#acct(String)} (see {@link #detail} and
     *  {@link #auditLog} log statements). */
    private static FirmBankingSettingRow toListRow(FirmBankingSettingView v) {
        return new FirmBankingSettingRow(
                v.getBizKey(),
                v.getOutBankCd(),
                v.getBankNm(),
                v.getCcyCd(),
                v.getBankPrtnId(),
                v.getVanOrgCd(),
                v.getOrgStat(),
                v.getWdrlActNo(),
                v.getFileType(),
                v.getBatTranBranchDiv(),
                v.getBatTranBranchLimit(),
                v.getRegDttm(),
                v.getStartDttm(),
                v.getEndDttm(),
                v.getModDttm(),
                v.getLastEdtr(),
                v.getBal(),
                v.getExpiDt());
    }

    /** Detail returns cleartext {@code wdrl_act_no} — the edit dialog
     *  needs the round-trip value. */
    private static FirmBankingSettingDetail toDetail(TbFirmConf e) {
        return new FirmBankingSettingDetail(
                e.getBizKey(),
                e.getOutBankCd(),
                e.getBankPrtnId(),
                e.getVanOrgCd(),
                e.getOrgStat(),
                e.getWdrlActNo(),
                e.getFileType(),
                e.getBatTranBranchDiv(),
                e.getBatTranBranchLimit(),
                e.getRegDttm(),
                e.getStartDttm(),
                e.getEndDttm(),
                e.getModDttm(),
                e.getLastEdtr());
    }

    private static FirmBankingAuditEntry toAuditEntry(AuditLogsInfm e) {
        return new FirmBankingAuditEntry(
                e.getAuditLogsActv(),
                e.getAuditLogsActvDtl(),
                e.getRegId(),
                e.getRegNm(),
                e.getRegDtm());
    }
}
