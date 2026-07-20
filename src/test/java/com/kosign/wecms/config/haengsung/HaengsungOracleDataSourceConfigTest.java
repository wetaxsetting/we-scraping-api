package com.kosign.wecms.config.haengsung;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Verifies the conditional wiring of {@link HaengsungOracleDataSourceConfig}:
 * no Oracle bean exists when the flag is off, both Oracle beans plus the
 * re-declared primary datasource exist when the flag is on.
 *
 * <p>No real connection is opened — the Oracle pool uses
 * {@code initializationFailTimeout=-1} (lazy init) and the test pins the
 * primary Hikari pool to lazy init too, so the context refreshes without any
 * database being reachable.
 */
class HaengsungOracleDataSourceConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(HaengsungOracleDataSourceConfig.class);

    @Test
    void disabled_createsNoOracleBeans() {
        runner.withPropertyValues("haengsung.oracle.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(HaengsungOracleDataSourceConfig.ORACLE_DATA_SOURCE);
                    assertThat(ctx).doesNotHaveBean(HaengsungOracleDataSourceConfig.ORACLE_JDBC_TEMPLATE);
                    assertThat(ctx).doesNotHaveBean(HaengsungOracleProperties.class);
                });
    }

    @Test
    void absentFlag_createsNoOracleBeans() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(HaengsungOracleDataSourceConfig.ORACLE_DATA_SOURCE);
        });
    }

    @Test
    void enabled_createsOracleBeansAndPreservesPrimary() {
        runner.withPropertyValues(
                        "haengsung.oracle.enabled=true",
                        "haengsung.oracle.url=jdbc:oracle:thin:@//localhost:1521/DEV",
                        "haengsung.oracle.username=erp_user",
                        "haengsung.oracle.password=erp_pw",
                        // Primary Postgres datasource — re-declared when Oracle is on.
                        "spring.datasource.url=jdbc:postgresql://localhost:5432/none",
                        "spring.datasource.username=pg_user",
                        "spring.datasource.password=pg_pw",
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        // Lazy init so no real connection is attempted at refresh.
                        "spring.datasource.hikari.initialization-fail-timeout=-1")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();

                    // Secondary Oracle beans present.
                    assertThat(ctx).hasBean(HaengsungOracleDataSourceConfig.ORACLE_DATA_SOURCE);
                    assertThat(ctx).hasBean(HaengsungOracleDataSourceConfig.ORACLE_JDBC_TEMPLATE);
                    assertThat(ctx.getBean(HaengsungOracleDataSourceConfig.ORACLE_JDBC_TEMPLATE))
                            .isInstanceOf(JdbcTemplate.class);

                    // Primary datasource preserved (named `dataSource`, @Primary).
                    assertThat(ctx).hasBean("dataSource");
                    assertThat(ctx.getBeanProvider(DataSourceProperties.class).getIfAvailable())
                            .isNotNull();
                    assertThat(ctx.getBean(DataSource.class)).isNotNull();
                });
    }

    /**
     * Regression guard for the JDBC-template hijack: with Spring Boot's
     * {@code JdbcTemplateAutoConfiguration} active AND the Oracle
     * {@code JdbcTemplate} bean present, the single primary
     * {@link NamedParameterJdbcTemplate} (and {@link JdbcTemplate}) that every
     * unqualified repository injects must be backed by the <b>Postgres</b>
     * primary datasource — never the Oracle ERP pool. If the explicit
     * {@code @Primary} template beans are removed, auto-config would build the
     * primary {@code NamedParameterJdbcTemplate} on top of the only remaining
     * {@code JdbcTemplate} candidate (Oracle), and this test fails.
     */
    @Test
    void enabled_primaryJdbcTemplatesStayOnPostgres() {
        runner.withConfiguration(AutoConfigurations.of(JdbcTemplateAutoConfiguration.class))
                .withPropertyValues(
                        "haengsung.oracle.enabled=true",
                        "haengsung.oracle.url=jdbc:oracle:thin:@//localhost:1521/DEV",
                        "haengsung.oracle.username=erp_user",
                        "haengsung.oracle.password=erp_pw",
                        "spring.datasource.url=jdbc:postgresql://localhost:5432/none",
                        "spring.datasource.username=pg_user",
                        "spring.datasource.password=pg_pw",
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "spring.datasource.hikari.initialization-fail-timeout=-1")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();

                    DataSource primary = ctx.getBean("dataSource", DataSource.class);
                    DataSource oracle = ctx.getBean(
                            HaengsungOracleDataSourceConfig.ORACLE_DATA_SOURCE, DataSource.class);

                    NamedParameterJdbcTemplate npjt = ctx.getBean(NamedParameterJdbcTemplate.class);
                    assertThat(npjt.getJdbcTemplate().getDataSource()).isSameAs(primary);
                    assertThat(npjt.getJdbcTemplate().getDataSource()).isNotSameAs(oracle);

                    JdbcTemplate primaryJt = ctx.getBean(JdbcTemplate.class);
                    assertThat(primaryJt.getDataSource()).isSameAs(primary);
                });
    }
}
