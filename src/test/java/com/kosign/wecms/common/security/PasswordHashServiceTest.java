package com.kosign.wecms.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHashServiceTest {

    private final PasswordHashService service = new PasswordHashService();

    @Test
    void encodeForNewUser_alwaysProducesBCryptHash() {
        String hash = service.encodeForNewUser("Password!1");
        assertThat(hash).startsWith("$2a$");
        assertThat(hash.length()).isGreaterThanOrEqualTo(60);
    }

    @Test
    void encodeForNewUser_rejectsBlankInput() {
        assertThatThrownBy(() -> service.encodeForNewUser(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.encodeForNewUser(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.encodeForNewUser("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
