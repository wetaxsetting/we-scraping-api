package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.repository.UserLoginHisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    /** Legacy {@code WB_FORMATTER_DTM14} — UTC {@code yyyyMMddHHmmss}. */
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final UserLoginHisRepository repository;

    /**
     * Write a login history row in a dedicated transaction so that a failed
     * login attempt still persists its FAIL audit row even when the outer
     * login transaction rolls back (matches the legacy behaviour where the
     * history row is inserted before the WbException is thrown).
     *
     * REG_UID is part of the legacy DEV PK (USER_ID, REG_DTM, REG_UID) — it
     * acts as a tiebreaker when multiple login attempts share the same
     * REG_DTM. We populate it with a UUID per row, matching DEV's
     * UUID-format values for *_uid columns.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String userId, String ipAddr, String rsltCd, String rsltCont) {
        UserLoginHis his = new UserLoginHis();
        his.setUserId(userId);
        // REG_DTM — UTC now formatted yyyyMMddHHmmss, exactly matching legacy
        // UserLoginHisDAO.create → DateTimeUtils.utcNow().toString(WB_FORMATTER_DTM14).
        his.setRegDtm(OffsetDateTime.now(ZoneOffset.UTC).format(DTM14));
        his.setRegUid(UUID.randomUUID().toString());
        his.setIpAddr(ipAddr);
        his.setLgnChnl(UserLoginHis.CHNL_PC);
        his.setRsltCd(rsltCd);
        his.setRsltCont(rsltCont);
        repository.save(his);
    }
}
