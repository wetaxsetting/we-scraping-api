package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H-4 · Behaviour and <b>transaction-boundary</b> contract of the pre-egress claim.
 *
 * <p>The behavioural half is trivial (1 row → win, 0 rows → lose). The part worth guarding is the
 * boundary: this claim only protects anything if it <b>commits before the SFTP upload</b>. Two ways
 * that silently breaks —
 * <ol>
 *   <li>the {@code @Transactional} annotation is removed or weakened, so the modifying query runs in
 *       whatever transaction happens to be active (or none), and</li>
 *   <li>the method is made non-public or the call is moved inside {@code BulkReceptionService}, where
 *       Spring's proxy does not apply and the annotation becomes decoration.</li>
 * </ol>
 * Both are asserted here, because neither would fail any behavioural test.
 */
@ExtendWith(MockitoExtension.class)
class BulkDispatchClaimTxServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String DT = "20260729";
    private static final String FILE_SEQ = "1";
    private static final String DTTM = "20260729101500";

    @Mock TbPayFileMstRepository payFileMstRepository;

    private BulkDispatchClaimTxService service() {
        return new BulkDispatchClaimTxService(payFileMstRepository);
    }

    // ── behaviour ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("firstClaim_succeeds — one row moved means this caller owns the dispatch")
    void firstClaim_succeeds() {
        when(payFileMstRepository.claimForReception(BIZ, DT, FILE_SEQ, DTTM)).thenReturn(1);

        assertThat(service().claimForReception(BIZ, DT, FILE_SEQ, DTTM)).isTrue();
    }

    @Test
    @DisplayName("secondClaim_forSameTenantAndLogicalFile_returnsLost — zero rows means stand down")
    void secondClaim_forSameTenantAndLogicalFile_returnsLost() {
        when(payFileMstRepository.claimForReception(BIZ, DT, FILE_SEQ, DTTM)).thenReturn(0);

        assertThat(service().claimForReception(BIZ, DT, FILE_SEQ, DTTM))
                .as("a lost claim must be reported as false so the caller returns before any egress")
                .isFalse();
    }

    @Test
    @DisplayName("the claim key is passed through verbatim — tenant, date and file sequence")
    void claimKeyIsPassedThroughVerbatim() {
        when(payFileMstRepository.claimForReception(BIZ, DT, FILE_SEQ, DTTM)).thenReturn(1);

        service().claimForReception(BIZ, DT, FILE_SEQ, DTTM);

        // If any key component were dropped or reordered the claim could move a different row —
        // FILE_SEQ_NO alone is not unique across tenants (global firm_file_seq_no sequence).
        verify(payFileMstRepository).claimForReception(eq(BIZ), eq(DT), eq(FILE_SEQ), eq(DTTM));
    }

    @Test
    @DisplayName("a lost claim performs no further repository work")
    void lostClaim_doesNoFurtherWork() {
        when(payFileMstRepository.claimForReception(BIZ, DT, FILE_SEQ, DTTM)).thenReturn(0);

        service().claimForReception(BIZ, DT, FILE_SEQ, DTTM);

        // In particular it must not "helpfully" save the row — that would resurrect the
        // unconditional write this fix removed.
        verify(payFileMstRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ── transaction boundary ─────────────────────────────────────────────────

    @Test
    @DisplayName("the claim runs in its own REQUIRES_NEW transaction, so it commits before egress")
    void claimIsRequiresNew() {
        Method m = ReflectionUtils.findMethod(BulkDispatchClaimTxService.class, "claimForReception",
                String.class, String.class, String.class, String.class);
        assertThat(m).isNotNull();

        Transactional tx = m.getAnnotation(Transactional.class);
        assertThat(tx)
                .as("without @Transactional the @Modifying claim query has no transaction of its own, "
                        + "so nothing guarantees the claim is durable before the SFTP upload")
                .isNotNull();
        assertThat(tx.propagation())
                .as("REQUIRES_NEW keeps the guarantee even if a future caller wraps receive() in a "
                        + "transaction — the claim must not commit at the end of an outer transaction "
                        + "that also spans the egress")
                .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(tx.readOnly()).as("a claim writes").isFalse();
    }

    @Test
    @DisplayName("the claim method is public on a distinct bean, so the Spring proxy actually applies")
    void claimIsProxyEffective() {
        Method m = ReflectionUtils.findMethod(BulkDispatchClaimTxService.class, "claimForReception",
                String.class, String.class, String.class, String.class);
        assertThat(m).isNotNull();

        // Spring's transaction proxy intercepts external calls to public methods only. A private or
        // package-private method, or a self-invocation from inside BulkReceptionService, would leave
        // @Transactional inert and the claim uncommitted at egress time.
        assertThat(Modifier.isPublic(m.getModifiers()))
                .as("claimForReception must be public for the transaction proxy to intercept it")
                .isTrue();
        assertThat(Modifier.isFinal(m.getModifiers()))
                .as("a final method cannot be proxied by CGLIB")
                .isFalse();
        assertThat(Modifier.isFinal(BulkDispatchClaimTxService.class.getModifiers()))
                .as("a final class cannot be proxied by CGLIB")
                .isFalse();
    }

    @Test
    @DisplayName("BulkReceptionService calls the claim across a bean boundary, not by self-invocation")
    void bulkReceptionServiceCallsTheClaimAcrossABeanBoundary() {
        // The whole point of extracting this class. If someone inlines the claim back into
        // BulkReceptionService as a @Transactional method, the proxy stops applying and the fix
        // silently reverts to an uncommitted claim.
        boolean injected = java.util.Arrays.stream(BulkReceptionService.class.getDeclaredFields())
                .anyMatch(f -> f.getType() == BulkDispatchClaimTxService.class);

        assertThat(injected)
                .as("BulkReceptionService must hold BulkDispatchClaimTxService as a collaborator so "
                        + "the claim crosses a proxy boundary")
                .isTrue();
        assertThat(java.util.Arrays.stream(BulkReceptionService.class.getDeclaredMethods())
                .anyMatch(f -> f.isAnnotationPresent(Transactional.class)))
                .as("BulkReceptionService must not carry @Transactional itself — it has no ambient "
                        + "transaction and self-invoked @Transactional methods are silently inert")
                .isFalse();
    }
}
