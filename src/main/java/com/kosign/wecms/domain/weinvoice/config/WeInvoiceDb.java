package com.kosign.wecms.domain.weinvoice.config;

import com.kosign.wecms.common.exception.BusinessException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;

/**
 * Handle on the WeInvoice database — the PostgreSQL database the we-scraping engine
 * writes, holding the engine tables and the WeInvoice {@code winv_*} tables.
 *
 * <p><b>Why this is not a {@code DataSource} bean.</b> Declaring a second
 * {@code DataSource}, {@code JdbcTemplate}, {@code NamedParameterJdbcTemplate} or
 * transaction-manager bean makes the matching Spring Boot auto-configuration back off
 * ({@code @ConditionalOnMissingBean}): the primary {@code NamedParameterJdbcTemplate}
 * that {@code BankPickerRepository} and friends inject would disappear or point here,
 * JPA would lose its transaction manager, and the actuator health check would start
 * probing this database. {@code HaengsungOracleDataSourceConfig} has to re-declare the
 * primary beans for exactly that reason. Keeping the pool private to this component
 * leaves every existing bean untouched.
 *
 * <p>Consequences for callers:
 * <ul>
 *   <li>Never put {@code @Transactional} on WeInvoice code — it would open a JPA
 *       transaction on the WeCMS database. Use {@link #tx()}.</li>
 *   <li>SQL names tables as {@code {eng}.invoices} / {@code {app}.winv_cmpn_infm};
 *       {@link #sql(String)} substitutes the configured schema names, which
 *       {@link WeInvoiceProperties} has validated as plain identifiers.</li>
 *   <li>With WeInvoice disabled, {@link #jdbc()} and {@link #tx()} throw
 *       {@code WBE3000}; nothing connects at startup.</li>
 * </ul>
 */
@Slf4j
@Component
public class WeInvoiceDb implements DisposableBean {

    private final boolean enabled;
    private final String engineSchema;
    private final String appSchema;
    private final HikariDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public WeInvoiceDb(WeInvoiceProperties props) {
        this.enabled = props.enabled();
        // Disabled: schema names may be dummies. Substitute safe defaults so SQL
        // built at construction time stays well-formed; it is never executed.
        this.engineSchema = enabled ? props.engineSchema().toLowerCase(Locale.ROOT) : "public";
        this.appSchema = enabled ? props.appSchema().toLowerCase(Locale.ROOT) : "weinvoice";
        if (!enabled) {
            this.dataSource = null;
            this.jdbc = null;
            this.tx = null;
            log.info("WeInvoice disabled (wecms.weinvoice.enabled=false) — no connection pool opened");
            return;
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("weinvoice-pool");
        cfg.setJdbcUrl(props.db().url());
        cfg.setUsername(props.db().username());
        cfg.setPassword(props.db().password());
        cfg.setMaximumPoolSize(props.db().maxPoolSize());
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(30_000L);
        // Below the common 30-minute network idle cutoff (same choice as the Oracle pool).
        cfg.setMaxLifetime(1_740_000L);
        // Never block or fail startup when the database is unreachable; the pool
        // connects on first use and the failing call surfaces as WBE_DB.
        cfg.setInitializationFailTimeout(-1);
        this.dataSource = new HikariDataSource(cfg);
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        log.info("WeInvoice enabled — engine schema '{}', app schema '{}'", engineSchema, appSchema);
    }

    public boolean enabled() {
        return enabled;
    }

    public NamedParameterJdbcTemplate jdbc() {
        requireEnabled();
        return jdbc;
    }

    /** Transaction on the WeInvoice database (the JPA transaction manager covers the WeCMS database only). */
    public TransactionTemplate tx() {
        requireEnabled();
        return tx;
    }

    /** Replaces {@code {eng}} and {@code {app}} with the configured schema names. */
    public String sql(String template) {
        return template.replace("{eng}", engineSchema).replace("{app}", appSchema);
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new BusinessException("WBE3000");
        }
    }

    @Override
    public void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
