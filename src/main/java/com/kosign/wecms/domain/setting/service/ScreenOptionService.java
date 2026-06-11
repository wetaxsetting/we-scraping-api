package com.kosign.wecms.domain.setting.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.dto.ScreenAreaListResponse;
import com.kosign.wecms.domain.setting.dto.ScreenOptionItem;
import com.kosign.wecms.domain.setting.dto.ScreenOptionListResponse;
import com.kosign.wecms.domain.setting.entity.SysScrOptionWecms;
import com.kosign.wecms.domain.setting.entity.UserScrOptionWecms;
import com.kosign.wecms.domain.setting.repository.SysScrOptionWecmsRepository;
import com.kosign.wecms.domain.setting.repository.UserScrOptionWecmsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * View Setting service — backs the GET / PUT / DELETE endpoints under
 * {@code /api/v1/screen-options/{webSvcId}/{dymAreaId}}, plus the
 * {@code /areas} aggregator. Documented end-to-end in
 * {@code docs/features/view-setting.md}.
 *
 * <p>Strategy: <b>lazy-INSERT (legacy parity)</b>. The first GET for a
 * given {@code (BIZ_KEY, USER_ID, WEB_SVC_ID, DYM_AREA_ID)} scope copies
 * every {@code SYS_SCR_OPTION_WECMS} row into {@code USER_SCR_OPTION_WECMS}
 * before returning. Reset wipes the user rows and immediately re-inits
 * from sys so the user table is always populated post-reset. This mirrors
 * legacy {@code comm_0108_001_r001} / {@code comm_0108_001_u001} —
 * reversed from the original merge-on-read design (Sonny, 2026-05-02) for
 * full legacy parity. {@code SYS_SCR_OPTION_WECMS} is never written by
 * user popup actions; only Flyway / admin migrations touch it.
 *
 * <p>Schema is column-for-column DEV-aligned post-V35 — {@code SIZE},
 * {@code SORT_YN}, {@code HTML_CONT}, {@code DISP_YN} are all surfaced
 * to the wire so the frontend renders {@code <col width>}, sortable
 * headers, and pre-rendered HTML when DEV configures any of them.
 */
@Service
@RequiredArgsConstructor
public class ScreenOptionService {

    private static final String SYS_NOT_DEFINED = "WBE2001";

    /**
     * FIELD_IDs that are present in the View Setting data (driving column
     * order / preservation through Save+Reset) but never editable by the
     * end user. The popover hides them; the backend locks USE_YN='Y' and
     * pins DISP_ORD to the system seed regardless of payload, so a
     * misbehaving frontend cannot relax the rule. CHECKBOX = row-selection
     * column at DISP_ORD=0; ETC = optional trailing action column at
     * DISP_ORD=99.
     */
    private static final Set<String> SPECIAL_FIELD_IDS = Set.of("CHECKBOX", "ETC");

    private final SysScrOptionWecmsRepository sysRepo;
    private final UserScrOptionWecmsRepository userRepo;

    @Transactional
    public ScreenOptionListResponse load(String webSvcId, String dymAreaId) {
        List<SysScrOptionWecms> sysRows =
                sysRepo.findByWebSvcIdAndDymAreaIdOrderByDispOrdAsc(webSvcId, dymAreaId);
        if (sysRows.isEmpty()) {
            throw new BusinessException(SYS_NOT_DEFINED, webSvcId + "/" + dymAreaId);
        }

        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();

        Map<String, UserScrOptionWecms> overrides = currentUserOverrides(webSvcId, dymAreaId);

        if (overrides.isEmpty() && bizKey != null && userId != null) {
            initUserRowsFromSys(sysRows, bizKey, userId, webSvcId, dymAreaId);
            overrides = currentUserOverrides(webSvcId, dymAreaId);
        }

        return buildMergedResponse(webSvcId, dymAreaId, sysRows, overrides);
    }

    @Transactional
    public ScreenOptionListResponse save(String webSvcId, String dymAreaId, List<ScreenOptionItem> items) {
        Map<String, SysScrOptionWecms> sysByFieldId = sysByFieldId(webSvcId, dymAreaId);

        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();

        userRepo.deleteAllForScope(bizKey, userId, webSvcId, dymAreaId);

        int autoOrder = 1;
        List<UserScrOptionWecms> rows = new ArrayList<>(items.size());
        for (ScreenOptionItem incoming : items) {
            SysScrOptionWecms sys = sysByFieldId.get(incoming.fieldId());
            if (sys == null) {
                continue;
            }
            boolean isSpecial = isSpecialFieldId(sys.getFieldId());
            UserScrOptionWecms row = new UserScrOptionWecms();
            row.setBizKey(bizKey);
            row.setUserId(userId);
            row.setWebSvcId(webSvcId);
            row.setFieldId(sys.getFieldId());
            row.setFieldNm(sys.getFieldNm());
            row.setDymAreaId(dymAreaId);
            row.setDispYn(sys.getDispYn());
            row.setSize(sys.getSize());
            row.setUseYn(isSpecial
                    ? "Y"
                    : normaliseYn(incoming.useYn()));
            if (isSpecial) {
                row.setDispOrd(sys.getDispOrd());
            } else {
                Integer incomingOrd = incoming.dispOrd();
                row.setDispOrd(incomingOrd != null ? incomingOrd : autoOrder++);
            }
            row.setRegId(userId);
            row.setModId(userId);
            rows.add(row);
        }
        userRepo.saveAll(rows);
        return load(webSvcId, dymAreaId);
    }

    @Transactional
    public ScreenOptionListResponse reset(String webSvcId, String dymAreaId) {
        List<SysScrOptionWecms> sysRows =
                sysRepo.findByWebSvcIdAndDymAreaIdOrderByDispOrdAsc(webSvcId, dymAreaId);
        if (sysRows.isEmpty()) {
            throw new BusinessException(SYS_NOT_DEFINED, webSvcId + "/" + dymAreaId);
        }
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();

        userRepo.deleteAllForScope(bizKey, userId, webSvcId, dymAreaId);
        Map<String, UserScrOptionWecms> overrides;
        if (bizKey != null && userId != null) {
            initUserRowsFromSys(sysRows, bizKey, userId, webSvcId, dymAreaId);
            overrides = currentUserOverrides(webSvcId, dymAreaId);
        } else {
            overrides = Map.of();
        }
        return buildMergedResponse(webSvcId, dymAreaId, sysRows, overrides);
    }

    @Transactional(readOnly = true)
    public ScreenAreaListResponse listAreas(String webSvcId) {
        List<Object[]> rows = sysRepo.countColumnsByDymAreaId(webSvcId);
        if (rows.isEmpty()) {
            throw new BusinessException(SYS_NOT_DEFINED, webSvcId);
        }
        List<ScreenAreaListResponse.Area> areas = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String area = (String) r[0];
            Long count = (Long) r[1];
            areas.add(new ScreenAreaListResponse.Area(area, count.intValue()));
        }
        return new ScreenAreaListResponse(webSvcId, areas);
    }

    private Map<String, UserScrOptionWecms> currentUserOverrides(String webSvcId, String dymAreaId) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        if (bizKey == null || userId == null) return Map.of();
        List<UserScrOptionWecms> rows = userRepo
                .findByBizKeyAndUserIdAndWebSvcIdAndDymAreaIdOrderByDispOrdAsc(
                        bizKey, userId, webSvcId, dymAreaId);
        Map<String, UserScrOptionWecms> by = new HashMap<>(rows.size());
        for (UserScrOptionWecms r : rows) by.put(r.getFieldId(), r);
        return by;
    }

    private Map<String, SysScrOptionWecms> sysByFieldId(String webSvcId, String dymAreaId) {
        List<SysScrOptionWecms> rows =
                sysRepo.findByWebSvcIdAndDymAreaIdOrderByDispOrdAsc(webSvcId, dymAreaId);
        if (rows.isEmpty()) {
            throw new BusinessException(SYS_NOT_DEFINED, webSvcId + "/" + dymAreaId);
        }
        Map<String, SysScrOptionWecms> by = new HashMap<>(rows.size());
        for (SysScrOptionWecms r : rows) by.put(r.getFieldId(), r);
        return by;
    }

    private static String normaliseYn(String value) {
        return "Y".equalsIgnoreCase(value) ? "Y" : "N";
    }

    /**
     * True when {@code fieldId} is a special admin-controlled column that
     * the popover must hide and the service must lock to the sys-defined
     * USE_YN/DISP_ORD. Refactor convention: exact match on the canonical
     * names ({@code CHECKBOX}, {@code ETC}). Multi-area screens disambiguate
     * via {@code DYM_AREA_ID}, not via suffixed FIELD_IDs.
     */
    public static boolean isSpecialFieldId(String fieldId) {
        return fieldId != null && SPECIAL_FIELD_IDS.contains(fieldId);
    }

    private void initUserRowsFromSys(
            List<SysScrOptionWecms> sysRows,
            String bizKey, String userId,
            String webSvcId, String dymAreaId) {
        List<UserScrOptionWecms> seeded = new ArrayList<>(sysRows.size());
        for (SysScrOptionWecms sys : sysRows) {
            UserScrOptionWecms row = new UserScrOptionWecms();
            row.setBizKey(bizKey);
            row.setUserId(userId);
            row.setWebSvcId(webSvcId);
            row.setFieldId(sys.getFieldId());
            row.setFieldNm(sys.getFieldNm());
            row.setDymAreaId(dymAreaId);
            row.setUseYn(sys.getUseYn());
            row.setDispYn(sys.getDispYn());
            row.setDispOrd(sys.getDispOrd());
            row.setSize(sys.getSize());
            row.setRegId(userId);
            row.setModId(userId);
            seeded.add(row);
        }
        userRepo.saveAll(seeded);
    }

    private ScreenOptionListResponse buildMergedResponse(
            String webSvcId, String dymAreaId,
            List<SysScrOptionWecms> sysRows,
            Map<String, UserScrOptionWecms> overrides) {
        List<ScreenOptionItem> merged = new ArrayList<>(sysRows.size());
        for (SysScrOptionWecms sys : sysRows) {
            UserScrOptionWecms user = overrides.get(sys.getFieldId());
            if (user == null) {
                merged.add(new ScreenOptionItem(
                        sys.getFieldId(),
                        sys.getFieldNm(),
                        sys.getDispOrd(),
                        sys.getDispYn(),
                        sys.getUseYn(),
                        sys.getSize(),
                        sys.getSortYn(),
                        sys.getHtmlCont()));
            } else {
                merged.add(new ScreenOptionItem(
                        sys.getFieldId(),
                        sys.getFieldNm(),
                        user.getDispOrd(),
                        sys.getDispYn(),
                        user.getUseYn(),
                        sys.getSize(),
                        sys.getSortYn(),
                        sys.getHtmlCont()));
            }
        }
        merged.sort((a, b) -> {
            int ao = a.dispOrd() == null ? Integer.MAX_VALUE : a.dispOrd();
            int bo = b.dispOrd() == null ? Integer.MAX_VALUE : b.dispOrd();
            return Integer.compare(ao, bo);
        });
        return new ScreenOptionListResponse(webSvcId, dymAreaId, merged);
    }
}
