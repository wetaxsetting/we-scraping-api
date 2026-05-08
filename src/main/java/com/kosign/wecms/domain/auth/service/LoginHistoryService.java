package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.repository.UserLoginHisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoginHistoryService {

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
        his.setRegDtm(OffsetDateTime.now());
        his.setRegUid(UUID.randomUUID().toString());
        his.setIpAddr(ipAddr);
        his.setLgnChnl(UserLoginHis.CHNL_PC);
        his.setRsltCd(rsltCd);
        his.setRsltCont(rsltCont);
        repository.save(his);
    }
}
