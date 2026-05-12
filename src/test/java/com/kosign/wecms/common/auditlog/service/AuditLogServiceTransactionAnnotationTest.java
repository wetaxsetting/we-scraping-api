package com.kosign.wecms.common.auditlog.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@link Transactional @Transactional(propagation =
 * REQUIRES_NEW)} contract on
 * {@link AuditLogService#append(AuditLogContext)}.
 *
 * <p>Phase 1 §11.1 + §13 OQ-6 require that audit writes happen in
 * their own transaction so a partial audit failure cannot strand a
 * successful business write, and a caller's business rollback cannot
 * roll back the audit insert. Spring's tx aspect interprets
 * {@link Propagation#REQUIRES_NEW} at runtime, but the aspect only
 * fires under a full Spring context. This test verifies the
 * annotation is <b>present and configured correctly</b> at the
 * source level — accidental removal (or an inadvertent switch to
 * {@code REQUIRED}) would silently break the rollback isolation
 * contract without changing observable behavior in the unit-test
 * suite.
 *
 * <p>The actual runtime rollback isolation (caller's tx rolls back,
 * audit row persists) is verified manually until a future MR
 * introduces Testcontainers + a {@code @SpringBootTest} integration
 * suite. The verification recipe is documented in the MR summary
 * for this service. Both the unit suite
 * ({@link AuditLogServiceTest}) and this annotation pin are
 * non-context tests that run without a database.
 */
class AuditLogServiceTransactionAnnotationTest {

    @Test
    void append_isAnnotatedTransactionalRequiresNew() throws Exception {
        Method append = AuditLogService.class
                .getMethod("append", AuditLogContext.class);

        Transactional tx = append.getAnnotation(Transactional.class);
        assertThat(tx)
                .as("AuditLogService.append must carry @Transactional — "
                  + "Phase 1 §11.1 requires REQUIRES_NEW so audit writes "
                  + "are isolated from the caller's business transaction.")
                .isNotNull();
        assertThat(tx.propagation())
                .as("AuditLogService.append must use REQUIRES_NEW — "
                  + "REQUIRED would couple the audit insert to the "
                  + "caller's transaction, defeating the isolation rule "
                  + "documented in Phase 1 §13 OQ-6.")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
