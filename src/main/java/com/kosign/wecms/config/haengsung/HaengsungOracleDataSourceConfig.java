package com.kosign.wecms.config.haengsung;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Wires the secondary, optional HAENGSUNG Oracle ERP datasource.
 *
 * <p>Step 1 (Oracle Foundation) of
 * {@code docs/features/haengsung-oracle-integration-roadmap.md}. The whole
 * class is gated on {@code haengsung.oracle.enabled=true}; when the flag is off
 * (the default), none of these beans exist and the application boots against
 * PostgreSQL exactly as before.
 *
 * <p><b>Why the primary PostgreSQL datasource is re-declared here.</b> Spring
 * Boot's {@code DataSourceAutoConfiguration} is {@code @ConditionalOnMissingBean}
 * for {@link DataSource} — declaring the Oracle datasource bean makes it back
 * off entirely. So, only when Oracle is enabled, we re-declare the primary
 * Postgres datasource explicitly, binding the same {@code spring.datasource.*}
 * and {@code spring.datasource.hikari.*} keys auto-config would have used. The
 * primary therefore stays byte-identical to the Oracle-disabled path and JPA /
 * Hibernate auto-config keeps wiring against it. Oracle access is JdbcTemplate
 * only — no JPA {@code EntityManagerFactory}, no entities, no Flyway.
 */
@Configuration
@EnableConfigurationProperties(HaengsungOracleProperties.class)
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class HaengsungOracleDataSourceConfig {

    /** Bean name of the secondary Oracle datasource. */
    public static final String ORACLE_DATA_SOURCE = "haengsungOracleDataSource";

    /** Bean name of the JdbcTemplate bound to the Oracle datasource. */
    public static final String ORACLE_JDBC_TEMPLATE = "haengsungOracleJdbcTemplate";

    private static final String ORACLE_DRIVER_CLASS = "oracle.jdbc.OracleDriver";
    private static final String VALIDATION_QUERY = "SELECT 1 FROM dual";

    // ---------------------------------------------------------------------
    // Primary PostgreSQL datasource (re-declared — see class Javadoc)
    // ---------------------------------------------------------------------

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().build();
    }

    /**
     * Primary PostgreSQL {@link JdbcTemplate} — re-declared for the same reason
     * as the datasource above, but with a sharper consequence.
     *
     * <p>Registering {@link #haengsungOracleJdbcTemplate} below makes a
     * {@code JdbcTemplate} (hence a {@code JdbcOperations}) bean exist. Spring
     * Boot's {@code JdbcTemplateAutoConfiguration} is
     * {@code @ConditionalOnMissingBean(JdbcOperations.class)}, so it then backs
     * off and does <b>not</b> create the primary {@code JdbcTemplate}. Its
     * companion {@code NamedParameterJdbcTemplate} auto-config is
     * {@code @ConditionalOnSingleCandidate(JdbcTemplate.class)} — with only the
     * Oracle template present it would build the primary
     * {@code NamedParameterJdbcTemplate} on top of the <b>Oracle</b> datasource.
     * The result: every unqualified {@code JdbcTemplate} /
     * {@code NamedParameterJdbcTemplate} injection (e.g. the bank-picker /
     * bank-form repositories) would silently run PostgreSQL SQL against the
     * Oracle ERP. Declaring these two {@code @Primary} beans, bound to the
     * primary Postgres datasource, keeps the JDBC template surface on Postgres.
     * Oracle stays reachable only through the {@code @Qualifier}-scoped
     * {@link #ORACLE_JDBC_TEMPLATE} bean.
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
            @Qualifier("jdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // ---------------------------------------------------------------------
    // Secondary, optional HAENGSUNG Oracle datasource
    // ---------------------------------------------------------------------

    @Bean(name = ORACLE_DATA_SOURCE)
    public DataSource haengsungOracleDataSource(HaengsungOracleProperties props) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("haengsung-oracle-pool");
        cfg.setJdbcUrl(props.url());
        cfg.setUsername(props.username());
        cfg.setPassword(props.password());
        cfg.setDriverClassName(ORACLE_DRIVER_CLASS);
        cfg.setMaximumPoolSize(props.maxPoolSize());
        cfg.setMinimumIdle(props.minIdle());
        cfg.setConnectionTimeout(props.connectionTimeoutMs());

        // ORA-17008 "closed connection" guard: validate borrowed connections and
        // keep idle ones alive. Oracle's driver does not implement JDBC4
        // Connection.isValid reliably across every config, so pin an explicit
        // test query (memory project_hs_prod_datasource_ora17008).
        cfg.setConnectionTestQuery(VALIDATION_QUERY);
        cfg.setKeepaliveTime(props.keepaliveMs());
        cfg.setValidationTimeout(props.validationTimeoutMs());

        // Cap connection lifetime at 29 minutes — below the common 30-minute
        // network idle cutoff, so a connection is retired by the pool before a
        // firewall / Oracle profile silently drops it.
        cfg.setMaxLifetime(1_740_000L);

        // Secondary / optional: never block or fail application startup when
        // Oracle is unreachable. The pool initialises lazily on first use;
        // connectivity is surfaced (not enforced) by HaengsungOracleHealthCheck.
        cfg.setInitializationFailTimeout(-1);

        return new HikariDataSource(cfg);
    }

    @Bean(name = ORACLE_JDBC_TEMPLATE)
    public JdbcTemplate haengsungOracleJdbcTemplate(
            @Qualifier(ORACLE_DATA_SOURCE) DataSource haengsungOracleDataSource) {
        return new JdbcTemplate(haengsungOracleDataSource);
    }
}
