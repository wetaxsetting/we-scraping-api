package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.domain.auth.entity.BusiAdmnLoginHis;
import com.kosign.wecms.domain.auth.repository.BusiAdmnLoginHisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Admin login-history audit — legacy {@code BUSI_ADMN_LOGIN_HIS} (gap #9,
 * confirmed 2026-07-24). <b>Write-only.</b>
 *
 * <p>Appends one row on a successful business-admin login, mirroring
 * {@code logn_2001_001_r001_act.jsp:270-284}
 * ({@code busiAdmnInfm != null && ChnlType.TYPE_C}). The refactor <i>is</i> the
 * WeCMS customer channel, so the channel test is implicit; the admin test is
 * {@code authGrCd == ADMIN} (a {@code BUSI_ADMN_INFM} row exists). Unlike
 * {@code LoginHistoryService} (USER_LOGIN_HIS, {@code REQUIRES_NEW} so FAIL rows
 * survive rollback), this row is written on the SUCCESS path <b>inside the main
 * login transaction</b> — no annotation here, so it joins
 * {@code AuthService.login}'s transaction and commits/rolls back with it,
 * exactly as legacy writes it in the same {@code idoCon} transaction.
 *
 * <p><b>No read side.</b> Live WeCMS only <i>stores</i> this audit — it does not
 * surface the legacy JEX {@code comm_0213_001} viewer screen (confirmed Sonny,
 * 2026-07-24). The refactor matches WeCMS: write the row, expose no endpoint /
 * screen. Do not add a read endpoint unless the PO brings the viewer into
 * WeCMS scope.
 */
@Service
@RequiredArgsConstructor
public class AdminLoginHistoryService {

    /** Legacy {@code WB_FORMATTER_DTM14} — UTC {@code yyyyMMddHHmmss}. */
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BusiAdmnLoginHisRepository repository;

    /**
     * Append the admin login-history row. Fields mirror legacy verbatim:
     * BIZ_KEY / REG_ID from the resolved (tenant, user); REG_NM from
     * {@code BUSI_ADMN_INFM.USER_NM}; IP_ADDR from the request; LGN_CHNL 'PC';
     * REG_DTM UTC-now (14-char). LOGIN_UUID is generated here (Java equivalent
     * of the live DDL's DEFAULT) so it is unique per attempt and Hibernate has
     * the full composite id at insert time.
     *
     * @param bizKey resolved tenant (legacy {@code busiAdmnInfm.getBizKey()})
     * @param regId  admin user id  (legacy {@code busiAdmnInfm.getUserId()})
     * @param regNm  admin display name (legacy {@code busiAdmnInfm.getUserNm()}); may be null
     * @param ipAddr client IP (legacy {@code request.getRemoteAddr()})
     */
    public void record(String bizKey, String regId, String regNm, String ipAddr) {
        BusiAdmnLoginHis his = new BusiAdmnLoginHis();
        his.setBizKey(bizKey);
        his.setRegId(regId);
        his.setRegDtm(OffsetDateTime.now(ZoneOffset.UTC).format(DTM14));
        his.setLoginUuid(UUID.randomUUID().toString());
        his.setIpAddr(ipAddr);
        his.setRegNm(regNm);
        his.setLgnChnl(BusiAdmnLoginHis.CHNL_PC);
        repository.save(his);
    }
}
