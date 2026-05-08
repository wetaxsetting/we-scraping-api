package com.kosign.wecms.domain.auth.bootstrap;

import com.kosign.wecms.common.security.PasswordHashService;
import com.kosign.wecms.domain.auth.entity.BusiAdmnInfm;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Writes the user portion of the bootstrap chain:
 * USER_INFM → USER_BUSI_CONN_INFM → BUSI_ADMN_INFM.
 *
 * <p>Called only by {@link CompanyBootstrapService} so the whole chain runs
 * inside one outer {@code @Transactional} boundary — see seahorse plan §6.
 * Replaces the legacy {@code WB_INIT_USER_DATA} IDO; new password writes go
 * through {@link PasswordHashService} (BCrypt only) so legacy
 * {@code public.hash()} is never touched on a write path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBootstrapService {

    private final UserInfmRepository userInfmRepository;
    private final UserBusiConnInfmRepository userBusiConnInfmRepository;
    private final BusiAdmnInfmRepository busiAdmnInfmRepository;
    private final PasswordHashService passwordHashService;
    private final Clock clock;

    /**
     * Creates the first admin for the given tenant and links them.
     *
     * <p>Insert order matches the real manual SQL flow used by ops to
     * provision new tenants (see {@code Claude Code (AI)/sql/setup_uat_account_*.sql}):
     * USER_INFM → BUSI_ADMN_INFM → USER_BUSI_CONN_INFM. FK-wise either order
     * for the last two works; we follow the manual flow so the Spring
     * service is byte-compatible with the SQL that ops already trusts.
     *
     * @return the resolved USER_ID so the caller can include it in
     *         {@link BootstrapResult}.
     */
    public String createFirstAdmin(String bizKey,
                                   String emplNo,
                                   FirstAdminCommand cmd,
                                   String auditUser) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String hashedPw = passwordHashService.encodeForNewUser(cmd.rawPassword());

        UserInfm user = new UserInfm();
        user.setUserId(cmd.userId());
        user.setUserNm(cmd.userNm());
        user.setUserPw(hashedPw);
        user.setEml(cmd.eml());
        user.setClphNo(cmd.clphNo());
        user.setActvYn("Y");
        user.setLockYn("N");
        user.setDelYn("N");
        user.setBizKey(bizKey);
        user.setEmplNo(emplNo);
        user.setRegId(auditUser);
        user.setRegDtm(now);
        user.setModId(auditUser);
        user.setModDtm(now);
        userInfmRepository.save(user);

        BusiAdmnInfm admin = new BusiAdmnInfm();
        admin.setBizKey(bizKey);
        admin.setUserId(cmd.userId());
        admin.setUserNm(cmd.userNm());
        admin.setRegId(auditUser);
        admin.setRegDtm(now);
        admin.setModId(auditUser);
        admin.setModDtm(now);
        busiAdmnInfmRepository.save(admin);

        UserBusiConnInfm conn = new UserBusiConnInfm();
        conn.setUserId(cmd.userId());
        conn.setBizKey(bizKey);
        conn.setEmplNo(emplNo);
        conn.setDelYn("N");
        conn.setRegId(auditUser);
        conn.setRegDtm(now);
        conn.setModId(auditUser);
        conn.setModDtm(now);
        userBusiConnInfmRepository.save(conn);

        return cmd.userId();
    }
}
