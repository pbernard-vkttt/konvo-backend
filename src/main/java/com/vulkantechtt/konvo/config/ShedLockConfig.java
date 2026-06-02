package com.vulkantechtt.konvo.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ShedLock wiring so {@code @Scheduled} jobs claim a per-job lock in the
 * shared Postgres table and a multi-pod deploy doesn't double-run. The
 * default lock-max-duration is generous (10m) because the only @Scheduled
 * job today, {@code UsageRollupJob.rollupCurrentMonth}, is a single bulk
 * UPSERT that finishes in seconds.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
