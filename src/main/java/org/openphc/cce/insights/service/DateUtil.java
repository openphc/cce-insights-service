package org.openphc.cce.insights.service;

import java.sql.Timestamp;
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
        return obj.toString();
    }
}
