package org.openphc.cce.insights.domain.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

/**
 * Base JDBC repository for ClickHouse. Provides common CRUD-like read operations
 * against ReplacingMergeTree tables. FINAL de-duplication is controlled by
 * {@code cce.clickhouse.use-final} (default: true); set to false for testing
 * without the deduplication overhead.
 */
public abstract class AbstractClickHouseRepository<T, ID> implements ReadOnlyRepository<T, ID> {

    protected final NamedParameterJdbcTemplate jdbc;

    @Value("${cce.clickhouse.use-final:true}")
    private boolean useFinal;

    protected AbstractClickHouseRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    protected abstract String getTableName();

    protected abstract RowMapper<T> rowMapper();

    /** Returns " FINAL" when use-final is enabled, otherwise empty string. */
    protected String finalClause() {
        return useFinal ? " FINAL" : "";
    }

    @Override
    public Optional<T> findById(ID id) {
        String sql = "SELECT * FROM " + getTableName() + finalClause() + " WHERE id = :id LIMIT 1";
        List<T> results = jdbc.query(sql, Map.of("id", id.toString()), rowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        List<String> idList = new ArrayList<>();
        StreamSupport.stream(ids.spliterator(), false).forEach(id -> idList.add(id.toString()));
        if (idList.isEmpty()) return List.of();
        String sql = "SELECT * FROM " + getTableName() + finalClause() + " WHERE id IN (:ids)";
        return jdbc.query(sql, Map.of("ids", idList), rowMapper());
    }

    @Override
    public List<T> findAll() {
        return jdbc.query("SELECT * FROM " + getTableName() + finalClause(), rowMapper());
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        String sql = "SELECT * FROM " + getTableName() + finalClause() + " LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<T> content = jdbc.query(sql, params, rowMapper());
        long total = count();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public long count() {
        Long result = jdbc.queryForObject(
                "SELECT count() FROM " + getTableName() + finalClause(), Map.of(), Long.class);
        return result != null ? result : 0L;
    }

    @Override
    public boolean existsById(ID id) {
        Long result = jdbc.queryForObject(
                "SELECT count() FROM " + getTableName() + finalClause() + " WHERE id = :id",
                Map.of("id", id.toString()), Long.class);
        return result != null && result > 0;
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /** Convert null OffsetDateTime to empty string (sentinel for "no filter"). */
    protected static String dt(OffsetDateTime v) {
        return v == null ? "" : v.toString();
    }

    /** Convert null start date to epoch — parseDateTime64BestEffort-safe, effectively no lower bound. */
    protected static String dtStart(OffsetDateTime v) {
        return v == null ? "1970-01-01 00:00:00" : v.toString();
    }

    /** Convert null end date to far-future — parseDateTime64BestEffort-safe, effectively no upper bound. */
    protected static String dtEnd(OffsetDateTime v) {
        return v == null ? "2099-12-31 23:59:59" : v.toString();
    }

    /** Convert null UUID to empty string (sentinel for "no filter"). */
    protected static String uuid(UUID v) {
        return v == null ? "" : v.toString();
    }

    /** Convert null string to empty string (sentinel for "no filter"). */
    protected static String str(String v) {
        return v == null ? "" : v;
    }

    /** Safe UUID parse — returns null for null/empty input. */
    protected static UUID parseUUID(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    /** Safe column read — returns null if the column does not exist in the result set. */
    protected static String tryGetString(java.sql.ResultSet rs, String col) {
        try { return rs.getString(col); } catch (java.sql.SQLException e) { return null; }
    }

    /** Map a JDBC column to OffsetDateTime, handling Timestamp, LocalDateTime, and String. */
    protected static OffsetDateTime toOffsetDateTime(ResultSet rs, String col) throws SQLException {
        Object val = rs.getObject(col);
        if (val == null) return null;
        if (val instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        if (val instanceof LocalDateTime ldt) return ldt.atOffset(ZoneOffset.UTC);
        if (val instanceof String s) {
            if (s.isEmpty()) return null;
            try { return OffsetDateTime.parse(s); } catch (Exception e1) {
                try { return LocalDateTime.parse(s).atOffset(ZoneOffset.UTC); } catch (Exception e2) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Return a ClickHouse date-truncation expression for the given interval string.
     * Validates the input to prevent SQL injection.
     */
    protected static String dateTruncExpr(String interval, String column) {
        return switch (interval.toLowerCase()) {
            case "hour"    -> "toStartOfHour(" + column + ")";
            case "day"     -> "toStartOfDay(" + column + ")";
            case "week"    -> "toStartOfWeek(" + column + ")";
            case "month"   -> "toStartOfMonth(" + column + ")";
            case "quarter" -> "toStartOfQuarter(" + column + ")";
            case "year"    -> "toStartOfYear(" + column + ")";
            default -> throw new IllegalArgumentException("Invalid interval: " + interval);
        };
    }
}
