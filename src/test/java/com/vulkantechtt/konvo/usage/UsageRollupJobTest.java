package com.vulkantechtt.konvo.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

@ExtendWith(MockitoExtension.class)
class UsageRollupJobTest {

    @Mock JdbcTemplate jdbc;

    @Test
    void rollupMonthBindsPeriodInstantsWithExplicitTimestampType() {
        UsageRollupJob job = new UsageRollupJob(jdbc, new SimpleMeterRegistry());

        job.rollupMonth(YearMonth.of(2026, 5));

        ArgumentCaptor<Object> start = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> end = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(),
                eq("2026-05"),
                start.capture(), end.capture(),
                start.capture(), end.capture(),
                start.capture(), end.capture());

        assertThat(start.getAllValues()).allSatisfy(value ->
                assertTimestampParam(value, Instant.parse("2026-05-01T00:00:00Z")));
        assertThat(end.getAllValues()).allSatisfy(value ->
                assertTimestampParam(value, Instant.parse("2026-06-01T00:00:00Z")));
    }

    private static void assertTimestampParam(Object actual, Instant expected) {
        assertThat(actual).isNotInstanceOf(Instant.class);
        assertThat(actual)
                .asInstanceOf(type(SqlParameterValue.class))
                .satisfies(param -> {
                    assertThat(param.getSqlType()).isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
                    assertThat(param.getValue()).isEqualTo(OffsetDateTime.ofInstant(expected, ZoneOffset.UTC));
                });
    }
}
