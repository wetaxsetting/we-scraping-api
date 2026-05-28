package com.kosign.wecms.config;

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Distributed lock for {@code @Scheduled} methods — prevents duplicate
 * batch execution when the app runs on more than one JVM (SIT / UAT /
 * PROD horizontal scaling).
 *
 * <p>Backing store: the existing PostgreSQL via the {@code shedlock}
 * table created by Flyway V210. The provider uses the DB's clock
 * ({@code usingDbTime()}) so out-of-sync host clocks don't cause
 * either side to release the lock early.
 *
 * <p>{@link EnableSchedulerLock#defaultLockAtMostFor} pins a 10-minute
 * safety bound — if a node holding the lock crashes mid-batch, the
 * lock auto-releases after 10 minutes so the next scheduled fire can
 * recover. Individual {@code @SchedulerLock} annotations may override
 * this with a tighter / wider bound per job.
 *
 * <p>{@code @EnableScheduling} continues to live on
 * {@link com.kosign.wecms.domain.banking.nxisas.NxisasConfig} — this
 * class only adds the lock provider; it does not re-declare scheduling.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
