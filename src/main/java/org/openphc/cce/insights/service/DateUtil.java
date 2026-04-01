package org.openphc.cce.insights.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class DateUtil {

    private DateUtil() {}

    public static String mapInterval(String interval) {
        if (interval == null) return "week";
        return switch (interval.toLowerCase()) {
            case "daily" -> "day";
            case "weekly" -> "week";
            case "monthly" -> "month";
            default -> "week";
        };
    }

    public static String extractDate(Object obj) {
        if (obj instanceof Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString();
        }
        if (obj instanceof Instant inst) {
            return inst.atOffset(ZoneOffset.UTC).toLocalDate().toString();
        }
        if (obj instanceof OffsetDateTime odt) {
            return odt.toLocalDate().toString();
        }
        return obj.toString();
    }

    public static OffsetDateTime toOffsetDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof OffsetDateTime odt) return odt;
        if (obj instanceof Instant inst) return inst.atOffset(ZoneOffset.UTC);
        if (obj instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(obj.toString());
    }
}
