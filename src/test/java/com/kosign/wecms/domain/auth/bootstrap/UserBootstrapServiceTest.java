package com.kosign.wecms.domain.auth.bootstrap;

import com.kosign.wecms.common.security.PasswordHashService;
import com.kosign.wecms.domain.auth.entity.BusiAdmnInfm;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBootstrapServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String EMPL = "20260429_001";
    private static final String AUDIT = "SYSTEM_BOOTSTRAP";

    @Mock UserInfmRepository userInfmRepository;
    @Mock UserBusiConnInfmRepository userBusiConnInfmRepository;
    @Mock BusiAdmnInfmRepository busiAdmnInfmRepository;
    @Mock PasswordHashService passwordHashService;

    UserBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new UserBootstrapService(
                userInfmRepository,
                userBusiConnInfmRepository,
                busiAdmnInfmRepository,
                passwordHashService,
                Clock.fixed(Instant.parse("2026-04-29T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createFirstAdmin_writesAllThreeRows_andHashesPasswordViaFacade() {
        when(passwordHashService.encodeForNewUser("Password!1")).thenReturn("$2a$10$bcrypt");

        FirstAdminCommand admin = new FirstAdminCommand(
                "rndadmin", "R&D Admin", "Password!1", "rndadmin@example.com", null);

        String userId = service.createFirstAdmin(BIZ, EMPL, admin, AUDIT);

        assertThat(userId).isEqualTo("rndadmin");

        ArgumentCaptor<UserInfm> user = ArgumentCaptor.forClass(UserInfm.class);
        verify(userInfmRepository).save(user.capture());
        assertThat(user.getValue().getUserId()).isEqualTo("rndadmin");
        assertThat(user.getValue().getUserPw()).isEqualTo("$2a$10$bcrypt");
        assertThat(user.getValue().getActvYn()).isEqualTo("Y");
        assertThat(user.getValue().getLockYn()).isEqualTo("N");
        assertThat(user.getValue().getDelYn()).isEqualTo("N");
        assertThat(user.getValue().getBizKey()).isEqualTo(BIZ);
        assertThat(user.getValue().getEmplNo()).isEqualTo(EMPL);
        assertThat(user.getValue().getRegId()).isEqualTo(AUDIT);

        ArgumentCaptor<UserBusiConnInfm> conn = ArgumentCaptor.forClass(UserBusiConnInfm.class);
        verify(userBusiConnInfmRepository).save(conn.capture());
        assertThat(conn.getValue().getUserId()).isEqualTo("rndadmin");
        assertThat(conn.getValue().getBizKey()).isEqualTo(BIZ);
        assertThat(conn.getValue().getEmplNo()).isEqualTo(EMPL);
        assertThat(conn.getValue().getDelYn()).isEqualTo("N");

        ArgumentCaptor<BusiAdmnInfm> adm = ArgumentCaptor.forClass(BusiAdmnInfm.class);
        verify(busiAdmnInfmRepository).save(adm.capture());
        assertThat(adm.getValue().getBizKey()).isEqualTo(BIZ);
        assertThat(adm.getValue().getUserId()).isEqualTo("rndadmin");
        assertThat(adm.getValue().getUserNm()).isEqualTo("R&D Admin");
    }

    @Test
    void createFirstAdmin_writesInManualSqlOrder_userThenAdminThenConn() {
        when(passwordHashService.encodeForNewUser("Password!1")).thenReturn("$2a$10$bcrypt");

        FirstAdminCommand admin = new FirstAdminCommand(
                "rndadmin", "R&D Admin", "Password!1", null, null);

        service.createFirstAdmin(BIZ, EMPL, admin, AUDIT);

        InOrder order = inOrder(userInfmRepository, busiAdmnInfmRepository, userBusiConnInfmRepository);
        order.verify(userInfmRepository).save(any(UserInfm.class));
        order.verify(busiAdmnInfmRepository).save(any(BusiAdmnInfm.class));
        order.verify(userBusiConnInfmRepository).save(any(UserBusiConnInfm.class));
    }
}
