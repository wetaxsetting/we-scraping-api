package com.kosign.wecms.domain.setting.zalooa.service;

import com.kosign.wecms.common.entity.LegacyDtmConverter;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloApiClient;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloApiException;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloTokenGrant;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloAuthorizeRequest;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloOaListResponse;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloOaRegisterRequest;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloOaRow;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloOaUpdateRequest;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloTemplateRow;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloTemplateUpsertRequest;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloOaInfm;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloOaInfmId;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloTmplInfm;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloTmplInfmId;
import com.kosign.wecms.domain.setting.zalooa.repository.ZaloOaInfmRepository;
import com.kosign.wecms.domain.setting.zalooa.repository.ZaloTmplInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Settings &gt; Zalo OA — connection and template administration for the
 * signed-in tenant.
 *
 * <p>Every method scopes on {@link TenantContext#currentBizKey()}. {@code BIZ_KEY}
 * is part of both tables' primary keys, so a request cannot reach another
 * tenant's connection even by guessing an OA id.
 *
 * <p><b>Sending is not here.</b> Outbound messages live in
 * {@code ZaloMessageService} and tokens in {@code ZaloTokenService}; this class
 * only manages the records.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZaloOaService {

    private static final String Y = "Y";
    private static final String N = "N";

    /**
     * Reused rather than re-deriving {@code yyyyMMddHHmmss} here. The converter is
     * stateless and is the single definition of the legacy audit-column format, so
     * a change to that format cannot leave the wire behind.
     */
    private static final LegacyDtmConverter DTM = new LegacyDtmConverter();

    private final ZaloOaInfmRepository oaRepo;
    private final ZaloTmplInfmRepository tmplRepo;
    private final ZaloApiClient zaloApiClient;
    private final ZaloTokenService tokenService;

    /* ------------------------------------------------------------------
     *  Read
     * ------------------------------------------------------------------ */

    /**
     * The tenant's connections, newest first, each with its templates.
     *
     * <p>Templates are fetched in one query for the whole page rather than per row
     * — the panel always renders them, so a lazy association would be an N+1 with
     * no upside.
     */
    @Transactional(readOnly = true)
    public ZaloOaListResponse list(Pageable pageable) {
        String bizKey = requireBizKey();
        Page<ZaloOaInfm> page = oaRepo.findByBizKeyAndDelYnOrderByRegDtmDesc(bizKey, N, pageable);

        List<String> oaIds = page.getContent().stream().map(ZaloOaInfm::getOaId).toList();
        Map<String, List<ZaloTmplInfm>> byOa = oaIds.isEmpty()
                ? Map.of()
                : tmplRepo.findByBizKeyAndOaIdIn(bizKey, oaIds).stream()
                        .collect(Collectors.groupingBy(ZaloTmplInfm::getOaId));

        List<ZaloOaRow> rows = page.getContent().stream()
                .map(oa -> toRow(oa, byOa.getOrDefault(oa.getOaId(), List.of())))
                .toList();
        return new ZaloOaListResponse(rows, page.getTotalElements());
    }

    /* ------------------------------------------------------------------
     *  Connection lifecycle
     * ------------------------------------------------------------------ */

    /**
     * Step one — record the OA id so the row exists before the tenant is sent to
     * Zalo's consent popup. See {@link ZaloOaRegisterRequest} for why the order is
     * not negotiable.
     *
     * <p>The new row is created <b>inactive</b>. It carries no tokens yet, so
     * activating it would make it the tenant's sending OA while being incapable of
     * sending — and would stand down a working connection to do it.
     */
    @Transactional
    public ZaloOaRow register(ZaloOaRegisterRequest req) {
        String bizKey = requireBizKey();
        String actor = requireUserId();

        if (oaRepo.existsByBizKeyAndOaIdAndDelYn(bizKey, req.oaId(), N)) {
            throw new BusinessException("WBE_ZALO_OA_ALREADY_EXISTS");
        }

        // A previously disconnected row for the same id is revived rather than
        // duplicated: (BIZ_KEY, OA_ID) is the primary key, so a second INSERT would
        // fail regardless — and reviving keeps whatever templates were recorded
        // against it, which the tenant would otherwise have to re-enter.
        ZaloOaInfm oa = oaRepo.findById(new ZaloOaInfmId(bizKey, req.oaId()))
                .orElseGet(ZaloOaInfm::new);
        oa.setBizKey(bizKey);
        oa.setOaId(req.oaId());
        oa.setOaNm(req.oaNm());
        oa.setUseYn(N);
        oa.setDelYn(N);
        if (oa.getRegId() == null) {
            oa.setRegId(actor);
        }
        oa.setModId(actor);
        oaRepo.save(oa);

        log.info("feature=zalo-oa registered biz_key={} oa_id={}", bizKey, req.oaId());
        return toRow(oa, tmplRepo.findByBizKeyAndOaId(bizKey, req.oaId()));
    }

    /**
     * Step two — exchange the consent code for the first token pair.
     *
     * <p>The authorization code is stored on the row for troubleshooting only; it
     * is single-use and worthless afterwards, which is why it is safe to keep and
     * pointless to protect.
     *
     * @throws BusinessException {@code WBE_ZALO_DISABLED} when Zalo is switched
     *         off in this environment, {@code WBE_ZALO_AUTHORIZE_FAILED} when Zalo
     *         refuses the code
     */
    @Transactional
    public ZaloOaRow authorize(String oaId, ZaloAuthorizeRequest req) {
        String bizKey = requireBizKey();
        String actor = requireUserId();
        ZaloOaInfm oa = requireConnection(bizKey, oaId);

        ZaloTokenGrant grant;
        try {
            grant = zaloApiClient.exchangeAuthorizationCode(req.code());
        } catch (UnsupportedOperationException ex) {
            // MockZaloApiClient — Zalo is disabled here. Translated so the caller
            // gets a documented business code instead of the catch-all WBE9999.
            throw new BusinessException("WBE_ZALO_DISABLED");
        } catch (ZaloApiException ex) {
            log.error("feature=zalo-oa authorize rejected biz_key={} oa_id={} zalo_error={}",
                    bizKey, oaId, ex.getZaloErrorCode(), ex);
            throw new BusinessException("WBE_ZALO_AUTHORIZE_FAILED");
        }

        oa.setAuthCd(req.code());
        tokenService.applyGrant(oa, grant, actor);
        oaRepo.save(oa);

        log.info("feature=zalo-oa authorized biz_key={} oa_id={}", bizKey, oaId);
        return toRow(oa, tmplRepo.findByBizKeyAndOaId(bizKey, oaId));
    }

    /**
     * Rename and/or make this the tenant's sending OA.
     *
     * <p><b>Activation order is load-bearing.</b> {@code zalo_oa_infm_uk01} is a
     * partial unique index on {@code (BIZ_KEY) WHERE USE_YN='Y' AND DEL_YN='N'},
     * and PostgreSQL enforces it per statement — not at commit. So the other
     * connections must be stood down <em>and flushed</em> before this one is
     * activated; letting Hibernate choose the order would intermittently violate
     * the index. The explicit {@code flush()} is that ordering, not a leftover.
     *
     * <p>Activating a connection that never completed OAuth is refused: it would
     * become the sending OA while unable to send, and would displace a working one.
     */
    @Transactional
    public ZaloOaRow update(String oaId, ZaloOaUpdateRequest req) {
        String bizKey = requireBizKey();
        String actor = requireUserId();
        ZaloOaInfm oa = requireConnection(bizKey, oaId);

        if (req.oaNm() != null) {
            oa.setOaNm(req.oaNm());
        }

        if (Boolean.TRUE.equals(req.active())) {
            if (oa.getRefreshToken() == null || oa.getRefreshToken().isBlank()) {
                throw new BusinessException("WBE_ZALO_NOT_AUTHORIZED");
            }
            List<ZaloOaInfm> others = oaRepo.findByBizKeyAndUseYnAndDelYnAndOaIdNot(bizKey, Y, N, oaId);
            if (!others.isEmpty()) {
                others.forEach(other -> {
                    other.setUseYn(N);
                    other.setModId(actor);
                });
                oaRepo.saveAll(others);
                oaRepo.flush();
            }
            oa.setUseYn(Y);
        } else if (Boolean.FALSE.equals(req.active())) {
            oa.setUseYn(N);
        }

        oa.setModId(actor);
        oaRepo.save(oa);
        log.info("feature=zalo-oa updated biz_key={} oa_id={} active={}", bizKey, oaId, oa.getUseYn());
        return toRow(oa, tmplRepo.findByBizKeyAndOaId(bizKey, oaId));
    }

    /**
     * Disconnect — soft delete, and stand the connection down in the same write so
     * a deleted row can never be the tenant's active OA.
     *
     * <p>Tokens are cleared. They are useless once the tenant has disconnected, and
     * leaving a live refresh token in a row nobody looks at again is a credential
     * sitting in the database for no reason.
     *
     * <p>Templates are <b>kept</b>. They record Zalo-issued ids that survive a
     * disconnect, and re-registering the same OA revives the row (see
     * {@link #register}), so discarding them would force the tenant to re-enter
     * data that is still valid.
     */
    @Transactional
    public void disconnect(String oaId) {
        String bizKey = requireBizKey();
        String actor = requireUserId();
        ZaloOaInfm oa = requireConnection(bizKey, oaId);

        oa.setDelYn(Y);
        oa.setUseYn(N);
        oa.setAccessToken(null);
        oa.setRefreshToken(null);
        oa.setTokenExpiDtm(null);
        oa.setModId(actor);
        oaRepo.save(oa);
        log.info("feature=zalo-oa disconnected biz_key={} oa_id={}", bizKey, oaId);
    }

    /* ------------------------------------------------------------------
     *  Templates
     * ------------------------------------------------------------------ */

    /**
     * Record or update one Zalo-approved template id. Upsert, because
     * {@code (BIZ_KEY, OA_ID, TMPL_TYPE, LANG_CD)} is the key — the caller always
     * names the exact row it means.
     */
    @Transactional
    public ZaloTemplateRow upsertTemplate(String oaId, ZaloTemplateUpsertRequest req) {
        String bizKey = requireBizKey();
        String actor = requireUserId();
        requireConnection(bizKey, oaId);
        String langCd = requireLangCd(req.langCd());

        ZaloTmplInfm tmpl = tmplRepo
                .findById(new ZaloTmplInfmId(bizKey, oaId, req.tmplType(), langCd))
                .orElseGet(ZaloTmplInfm::new);
        tmpl.setBizKey(bizKey);
        tmpl.setOaId(oaId);
        tmpl.setTmplType(req.tmplType());
        tmpl.setLangCd(langCd);
        tmpl.setTmplId(req.tmplId());
        tmpl.setUnitPrc(req.unitPrc());
        tmpl.setUseYn(Boolean.FALSE.equals(req.active()) ? N : Y);
        if (tmpl.getRegId() == null) {
            tmpl.setRegId(actor);
        }
        tmpl.setModId(actor);
        tmplRepo.save(tmpl);

        log.info("feature=zalo-oa template saved biz_key={} oa_id={} type={} lang={} tmpl_id={}",
                bizKey, oaId, req.tmplType(), langCd, req.tmplId());
        return toRow(tmpl);
    }

    /** Remove one template registration. Hard delete — the row holds no history. */
    @Transactional
    public void deleteTemplate(String oaId, String tmplType, String langCd) {
        String bizKey = requireBizKey();
        String lang = requireLangCd(langCd);
        String type = tmplType == null ? null : tmplType.trim().toUpperCase();
        ZaloTmplInfmId id = new ZaloTmplInfmId(bizKey, oaId, type, lang);
        if (!tmplRepo.existsById(id)) {
            throw new BusinessException("WBE_ZALO_TEMPLATE_NOT_FOUND");
        }
        tmplRepo.deleteById(id);
        log.info("feature=zalo-oa template deleted biz_key={} oa_id={} type={} lang={}",
                bizKey, oaId, type, lang);
    }

    /* ------------------------------------------------------------------
     *  Helpers
     * ------------------------------------------------------------------ */

    private ZaloOaInfm requireConnection(String bizKey, String oaId) {
        return oaRepo.findByBizKeyAndOaIdAndDelYn(bizKey, oaId, N)
                .orElseThrow(() -> new BusinessException("WBE_ZALO_OA_NOT_FOUND"));
    }

    /**
     * Reject an unknown language rather than silently substituting a default.
     * {@code LangCd.fromCodeOrDefault} would turn a typo into a valid-looking row
     * under the wrong locale — and because {@code LANG_CD} is part of the primary
     * key, that row would then be invisible to the send path looking for the
     * language the caller actually meant.
     */
    private static String requireLangCd(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("WBE_ZALO_INVALID_LANG_CD");
        }
        String code = raw.trim().toUpperCase();
        for (LangCd lang : LangCd.values()) {
            if (lang.name().equals(code)) {
                return code;
            }
        }
        throw new BusinessException("WBE_ZALO_INVALID_LANG_CD");
    }

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE1009");
        }
        return bizKey;
    }

    private static String requireUserId() {
        String userId = TenantContext.currentUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("WBE1009");
        }
        return userId;
    }

    private static ZaloOaRow toRow(ZaloOaInfm oa, List<ZaloTmplInfm> templates) {
        List<ZaloTemplateRow> rows = templates.stream()
                .sorted(Comparator.comparing(ZaloTmplInfm::getTmplType)
                        .thenComparing(ZaloTmplInfm::getLangCd))
                .map(ZaloOaService::toRow)
                .toList();
        return new ZaloOaRow(
                oa.getOaId(),
                oa.getOaNm(),
                Y.equals(oa.getUseYn()),
                // "Authorized" is the presence of a refresh token, not of an access
                // token: the access token expires routinely and its absence says
                // nothing about whether the tenant ever completed consent.
                oa.getRefreshToken() != null && !oa.getRefreshToken().isBlank(),
                dtm14(oa.getTokenExpiDtm()),
                dtm14(oa.getRegDtm()),
                rows);
    }

    private static ZaloTemplateRow toRow(ZaloTmplInfm tmpl) {
        return new ZaloTemplateRow(
                tmpl.getTmplType(),
                tmpl.getLangCd(),
                tmpl.getTmplId(),
                // toPlainString, never toString: BigDecimal.toString can emit
                // scientific notation, which a frontend parseFloat would read as a
                // wildly different price.
                tmpl.getUnitPrc() == null ? null : tmpl.getUnitPrc().toPlainString(),
                Y.equals(tmpl.getUseYn()));
    }

    static String dtm14(OffsetDateTime value) {
        return DTM.convertToDatabaseColumn(value);
    }
}
