package org.openphc.cce.insights.service;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DateUtil} — interval normalization and the multi-type date coercion helpers
 * used when mapping ClickHouse result columns.
 */
class DateUtilTest {

    @Test
    void mapInterval_normalizesKnownValues() {
        assertThat(DateUtil.mapInterval("daily")).isEqualTo("day");
        assertThat(DateUtil.mapInterval("WEEKLY")).isEqualTo("week");
        assertThat(DateUtil.mapInterval("monthly")).isEqualTo("month");
    }

    @Test
    void mapInterval_defaultsToWeekForNullOrUnknown() {
        assertThat(DateUtil.mapInterval(null)).isEqualTo("week");
        assertThat(DateUtil.mapInterval("hourly")).isEqualTo("week");
    }

    @Test
    void extractDate_handlesTimestampInstantOffsetAndFallback() {
        Instant instant = Instant.parse("2026-06-30T15:30:00Z");
        assertThat(DateUtil.extractDate(Timestamp.from(instant))).isEqualTo("2026-06-30");
        assertThat(DateUtil.extractDate(instant)).isEqualTo("2026-06-30");
        assertThat(DateUtil.extractDate(instant.atOffset(ZoneOffset.UTC))).isEqualTo("2026-06-30");
        assertThat(DateUtil.extractDate("raw-value")).isEqualTo("raw-value");
    }

    @Test
    void toOffsetDateTime_coercesSupportedTypes() {
        Instant instant = Instant.parse("2026-06-30T15:30:00Z");
        OffsetDateTime expected = instant.atOffset(ZoneOffset.UTC);

        assertThat(DateUtil.toOffsetDateTime(null)).isNull();
        assertThat(DateUtil.toOffsetDateTime(expected)).isEqualTo(expected);
        assertThat(DateUtil.toOffsetDateTime(instant)).isEqualTo(expected);
        assertThat(DateUtil.toOffsetDateTime(Timestamp.from(instant))).isEqualTo(expected);
        assertThat(DateUtil.toOffsetDateTime("2026-06-30T15:30:00Z")).isEqualTo(expected);
    }
}
