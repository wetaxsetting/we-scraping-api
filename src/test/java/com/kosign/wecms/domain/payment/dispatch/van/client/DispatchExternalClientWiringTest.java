package com.kosign.wecms.domain.payment.dispatch.van.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.sftp.JschSftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.MockSftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Default-profile external-call safety contract for the two money-path egress clients:
 * the EFS/VAN gateway ({@link EfsApiClient}) and SFTP ({@link SftpClient}).
 *
 * <p><b>What this guards.</b> Both clients are a mutually-exclusive pair selected by
 * {@code @ConditionalOnProperty} — the offline stand-in carries
 * {@code havingValue="false", matchIfMissing=true} and the live adapter carries
 * {@code havingValue="true"}. That pairing is the <em>only</em> thing standing between a
 * default/local run and a real transfer instruction leaving for a bank. It was previously
 * asserted nowhere: flipping a {@code matchIfMissing}, renaming a prefix, or dropping one
 * annotation would silently activate the live adapter, and every existing dispatch test
 * injects a mock {@code EfsApiClient} directly so none of them would notice.
 *
 * <p><b>How.</b> A narrow {@link ApplicationContextRunner} — no database, no ShedLock, no
 * secrets, so it runs in CI on every commit. {@link EfsProperties} / {@link SftpProperties}
 * are supplied as plain beans with explicit values rather than bound
 * {@code @ConfigurationProperties}, because the conditionals read the {@code Environment}
 * directly; that keeps this test about <em>bean selection</em> and nothing else.
 *
 * <p>No live endpoint is contacted: the "enabled" cases only assert which bean was chosen,
 * and the offline cases assert that calling the stand-in throws.
 *
 * @see com.kosign.wecms.config.SchedulerConfigurationDefaultsTest the sibling contract for
 *      scheduler enable-flags and crons
 */
class DispatchExternalClientWiringTest {

    /**
     * Supplies the collaborators the two live adapters need, without pulling in
     * {@code @ConfigurationProperties} binding. Values are inert placeholders — nothing here
     * can reach a network because no adapter method is ever invoked in the enabled cases.
     */
    @Configuration(proxyBeanMethods = false)
    static class Collaborators {

        @Bean
        EfsProperties efsProperties() {
            EfsProperties props = new EfsProperties();
            // Non-blank so RestClient.builder().baseUrl(...) is well-formed. Loopback with an
            // unroutable port — never dialled, since send() is not called when enabled.
            props.setBaseUrl("http://127.0.0.1:1");
            return props;
        }

        @Bean
        SftpProperties sftpProperties() {
            return new SftpProperties();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Collaborators.class)
            .withUserConfiguration(
                    MockEfsApiClient.class, RestClientEfsApiClient.class, EfsClientConfig.class,
                    MockSftpClient.class, JschSftpClient.class);

    // ── EFS gateway ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("default profile: the offline EFS client is the only EfsApiClient bean")
    void efs_defaultProfile_selectsMockOnly() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(EfsApiClient.class);
            assertThat(ctx.getBean(EfsApiClient.class)).isInstanceOf(MockEfsApiClient.class);
            assertThat(ctx).doesNotHaveBean(RestClientEfsApiClient.class);
            // The RestClient itself must not even be built when EFS is off.
            assertThat(ctx).doesNotHaveBean("efsRestClient");
        });
    }

    @Test
    @DisplayName("explicitly disabled EFS behaves identically to an unset flag")
    void efs_explicitlyDisabled_selectsMockOnly() {
        runner.withPropertyValues("wecms.dispatch.efs.enabled=false").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(EfsApiClient.class);
            assertThat(ctx.getBean(EfsApiClient.class)).isInstanceOf(MockEfsApiClient.class);
        });
    }

    @Test
    @DisplayName("the offline EFS client cannot perform a call — every send throws")
    void efs_offlineClient_throwsOnSend() {
        runner.run(ctx -> {
            EfsApiClient client = ctx.getBean(EfsApiClient.class);
            // The safety property that makes "mock is active" meaningful: it is not a
            // silent no-op that would let a caller believe a transfer was accepted.
            assertThatThrownBy(() -> client.send(EfsApiClient.EFS_202, new EfsEnvelope(
                    null, null, null, null, null, null, null, null, null, null)))
                    .isInstanceOf(UnsupportedOperationException.class);
        });
    }

    @Test
    @DisplayName("wecms.dispatch.efs.enabled=true: the live EFS client is the only EfsApiClient bean")
    void efs_enabled_selectsRestClientOnly() {
        runner.withPropertyValues("wecms.dispatch.efs.enabled=true").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(EfsApiClient.class);
            assertThat(ctx.getBean(EfsApiClient.class)).isInstanceOf(RestClientEfsApiClient.class);
            assertThat(ctx).doesNotHaveBean(MockEfsApiClient.class);
            assertThat(ctx).hasBean("efsRestClient");
        });
    }

    // ── SFTP ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("default profile: the offline SFTP client is the only SftpClient bean")
    void sftp_defaultProfile_selectsMockOnly() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(SftpClient.class);
            assertThat(ctx.getBean(SftpClient.class)).isInstanceOf(MockSftpClient.class);
            assertThat(ctx).doesNotHaveBean(JschSftpClient.class);
        });
    }

    @Test
    @DisplayName("explicitly disabled SFTP behaves identically to an unset flag")
    void sftp_explicitlyDisabled_selectsMockOnly() {
        runner.withPropertyValues("wecms.dispatch.sftp.enabled=false").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(SftpClient.class);
            assertThat(ctx.getBean(SftpClient.class)).isInstanceOf(MockSftpClient.class);
        });
    }

    @Test
    @DisplayName("the offline SFTP client cannot move a file — upload/download/move all throw")
    void sftp_offlineClient_throwsOnEveryOperation() {
        runner.run(ctx -> {
            SftpClient client = ctx.getBean(SftpClient.class);
            // A bulk pay-file must never appear to have been delivered.
            assertThatThrownBy(() -> client.upload("local", "remote"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> client.download("remote", "local"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> client.move("from", "to"))
                    .isInstanceOf(UnsupportedOperationException.class);
        });
    }

    @Test
    @DisplayName("wecms.dispatch.sftp.enabled=true: the live SFTP client is the only SftpClient bean")
    void sftp_enabled_selectsJschOnly() {
        runner.withPropertyValues("wecms.dispatch.sftp.enabled=true").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(SftpClient.class);
            assertThat(ctx.getBean(SftpClient.class)).isInstanceOf(JschSftpClient.class);
            assertThat(ctx).doesNotHaveBean(MockSftpClient.class);
        });
    }

    // ── the pairing itself ───────────────────────────────────────────────────

    @Test
    @DisplayName("neither pair can ever produce two beans, in any flag combination")
    void bothPairs_areMutuallyExclusive_inEveryCombination() {
        for (String efs : new String[] {"false", "true"}) {
            for (String sftp : new String[] {"false", "true"}) {
                runner.withPropertyValues(
                                "wecms.dispatch.efs.enabled=" + efs,
                                "wecms.dispatch.sftp.enabled=" + sftp)
                        .run(ctx -> {
                            assertThat(ctx).hasNotFailed();
                            // Two beans would be an ambiguous-dependency failure at injection
                            // time in production; assert the count, not just "has a bean".
                            assertThat(ctx.getBeansOfType(EfsApiClient.class))
                                    .as("EfsApiClient beans for efs=%s sftp=%s", efs, sftp)
                                    .hasSize(1);
                            assertThat(ctx.getBeansOfType(SftpClient.class))
                                    .as("SftpClient beans for efs=%s sftp=%s", efs, sftp)
                                    .hasSize(1);
                        });
            }
        }
    }

    @Test
    @DisplayName("a malformed flag value activates nothing — no client bean at all, so startup fails")
    void malformedFlagValue_registersNeitherClient() {
        // Verified behaviour, recorded because it is not the obvious one. Spring matches
        // havingValue literally: a present-but-unrecognised value satisfies NEITHER
        // `havingValue="false", matchIfMissing=true` (the value is present and is not "false")
        // NOR `havingValue="true"`. So a typo like WECMS_EFS_ENABLED=yes leaves the context
        // with no EfsApiClient / SftpClient bean whatsoever, and the application refuses to
        // start once a dispatch service tries to inject one.
        //
        // That is FAIL-SAFE and is the property worth pinning: a malformed flag can neither
        // activate the live adapter nor silently degrade to the offline one — it stops the
        // deploy. If a future change added a defaulting fallback here, a typo would start
        // resolving to *some* client and this test would catch the regression.
        runner.withPropertyValues(
                        "wecms.dispatch.efs.enabled=TRUE_ISH",
                        "wecms.dispatch.sftp.enabled=yes")
                .run(ctx -> {
                    // The safety-critical half: the live adapters are definitely absent.
                    assertThat(ctx).doesNotHaveBean(RestClientEfsApiClient.class);
                    assertThat(ctx).doesNotHaveBean(JschSftpClient.class);
                    assertThat(ctx).doesNotHaveBean("efsRestClient");
                    // And no offline fallback was substituted either.
                    assertThat(ctx.getBeansOfType(EfsApiClient.class)).isEmpty();
                    assertThat(ctx.getBeansOfType(SftpClient.class)).isEmpty();
                });
    }
}
