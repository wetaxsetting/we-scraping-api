package com.kosign.wecms.domain.auth.bootstrap;

import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the {@code __BCRYPT_PENDING__} sentinel in V2__auth_seed_dev.sql
 * with a real BCrypt hash of the well-known R&D password. Idempotent —
 * after the first startup the sentinel is gone and this does nothing.
 *
 * Active only under the {@code local} profile.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
class DataSeeder implements ApplicationRunner {

    static final String PENDING = "__BCRYPT_PENDING__";
    static final String RND_PASSWORD = "Password!1";

    private final UserInfmRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        String hash = passwordEncoder.encode(RND_PASSWORD);
        int patched = 0;
        for (UserInfm u : userRepo.findAll()) {
            if (PENDING.equals(u.getUserPw())) {
                u.setUserPw(hash);
                userRepo.save(u);
                patched++;
            }
        }
        if (patched > 0) {
            log.info("DataSeeder: patched BCrypt password on {} seeded user(s).", patched);
        }
    }
}
