-- =============================================================================
-- V210 — ShedLock table for distributed @Scheduled lock
-- =============================================================================
-- Backing store for net.javacrumbs.shedlock:shedlock-provider-jdbc-template.
-- One row per lock name; ShedLock UPSERTs into it to acquire/release the lock.
--
-- Consumers (initial):
--   - exch-shinhan-first-time   (ExchangeRateBatchService.runShinhanFirstTime)
--   - exch-shinhan-last-time    (ExchangeRateBatchService.runShinhanLastTime)
--   - exch-woori                (ExchangeRateBatchService.runWoori)
--
-- Future scheduled jobs that need cross-instance protection add their own
-- @SchedulerLock(name = "<unique-key>") without touching this schema.
--
-- Column shape comes from ShedLock 5.x JDBC provider docs:
--   https://github.com/lukas-krecan/ShedLock#jdbctemplate
-- =============================================================================

CREATE TABLE IF NOT EXISTS shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP    NOT NULL,
    locked_at   TIMESTAMP    NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
