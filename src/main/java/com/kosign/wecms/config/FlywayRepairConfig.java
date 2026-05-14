package com.kosign.wecms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Local-profile-only Flyway repair strategy.
 *
 * <p>Calls {@code flyway.repair()} before {@code flyway.migrate()} to reconcile
 * stored checksums and clear any failed-migration rows from prior boots. This
 * is the safe, generic recovery path for the shared dev DB where files may have
 * been edited after their first apply (validate-on-migrate is already off in
 * application.yml so checksum drift doesn't block startup; repair() also fills
 * in missing checksums for rows inserted manually with checksum=NULL).
 */
@Slf4j
@Configuration
@Profile("local")
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayRepairStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}