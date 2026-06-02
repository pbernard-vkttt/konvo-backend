package com.vulkantechtt.konvo.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.insights.dto.InsightsSnapshot;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

    @Mock JdbcTemplate jdbc;

    @Test
    void snapshotBindsRangeInstantsWithExplicitTimestampType() {
        InsightsService insights = new InsightsService(jdbc);
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> aiRow = Map.of(
                "runs", 0L,
                "ok_runs", 0L,
                "cost", 0.0,
                "avg_latency", 0L);
        List<Map<String, Object>> emptyRows = List.of();
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
                .thenReturn(0L);
        when(jdbc.queryForMap(anyString(), any(), any(), any()))
                .thenReturn(aiRow);
        when(jdbc.queryForList(anyString(), (Object) any(), (Object) any(), (Object) any()))
                .thenReturn(emptyRows);

        InsightsSnapshot snapshot = insights.snapshot(tenantId, 7);

        ArgumentCaptor<Object> kpiStart = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> kpiEnd = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).queryForObject(anyString(), eq(Long.class),
                eq(tenantId), kpiStart.capture(), kpiEnd.capture());
        assertTimestampParam(kpiStart.getValue(), snapshot.rangeStart());
        assertTimestampParam(kpiEnd.getValue(), snapshot.rangeEnd());

        ArgumentCaptor<Object> aiStart = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> aiEnd = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).queryForMap(anyString(), eq(tenantId), aiStart.capture(), aiEnd.capture());
        assertTimestampParam(aiStart.getValue(), snapshot.rangeStart());
        assertTimestampParam(aiEnd.getValue(), snapshot.rangeEnd());

        ArgumentCaptor<Object> firstParam = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> secondParam = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> thirdParam = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, times(5)).queryForList(anyString(),
                firstParam.capture(), secondParam.capture(), thirdParam.capture());

        List<Object> firstParams = firstParam.getAllValues();
        List<Object> secondParams = secondParam.getAllValues();
        List<Object> thirdParams = thirdParam.getAllValues();
        for (int i = 0; i < 4; i++) {
            assertThat(firstParams.get(i)).isEqualTo(tenantId);
            assertTimestampParam(secondParams.get(i), snapshot.rangeStart());
            assertTimestampParam(thirdParams.get(i), snapshot.rangeEnd());
        }
        assertTimestampParam(firstParams.get(4), snapshot.rangeStart());
        assertTimestampParam(secondParams.get(4), snapshot.rangeEnd());
        assertThat(thirdParams.get(4)).isEqualTo(tenantId);
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
