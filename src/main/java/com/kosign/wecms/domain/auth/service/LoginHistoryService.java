package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.repository.UserLoginHisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private final UserLoginHisRepository repository;

    /**
     * Write a login history row in a dedicated transaction so that a failed
     * login attempt still persists its FAIL audit row even when the outer
     * login transaction rolls back (matches the legacy behaviour where the
     * history row is inserted before the WbException is thrown).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String userId, String ipAddr, String rsltCd, String rsltCont) {
        UserLoginHis his = new UserLoginHis();
        his.setUserId(userId);
        his.setRegDtm(OffsetDateTime.now());
        his.setIpAddr(ipAddr);
        his.setLgnChnl(UserLoginHis.CHNL_PC);
        his.setRsltCd(rsltCd);
        his.setRsltCont(rsltCont);
        repository.save(his);
    }
}
