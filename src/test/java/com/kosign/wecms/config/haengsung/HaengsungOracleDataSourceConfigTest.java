package com.kosign.wecms.config.haengsung;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
