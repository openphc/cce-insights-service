package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

public abstract class AbstractClickHouseRepository<T, ID> implements ReadOnlyRepository<T, ID> {

    protected final DSLContext dsl;

    @Value("${cce.clickhouse.use-final:true}")
    private boolean useFinal;

    protected AbstractClickHouseRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    protected abstract String getTableName();

    protected abstract T fromRecord(Record r);

    /** Returns " FINAL" when use-final is enabled, otherwise empty string. */
    protected String finalClause() {
        return useFinal ? " FINAL" : "";
    }

    /**
     * Returns a table expression with an alias and the ClickHouse FINAL modifier when enabled.
     * ClickHouse requires the alias BEFORE FINAL: `TABLE alias FINAL`.
     */
    protected Table<?> finalAs(Table<?> table, String alias) {
        return finalClause().isEmpty()
                ? table.as(alias)
                : DSL.table(DSL.sql(table.getName() + " " + alias + " FINAL"));
    }

    private Table<?> baseTable() {
        return DSL.table(DSL.sql(getTableName() + finalClause()));
    }

    // -------------------------------------------------------------------------
    // ReadOnlyRepository — all implemented via jOOQ DSL
    // -------------------------------------------------------------------------

    @Override
    public Optional<T> findById(ID id) {
        return dsl.select(DSL.asterisk())
                  .from(baseTable())
                  .where(DSL.field("id").eq(id.toString()))
                  .limit(1)
                  .fetch()
                  .map(this::fromRecord)
                  .stream().findFirst();
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        List<String> idList = new ArrayList<>();
        StreamSupport.stream(ids.spliterator(), false).forEach(id -> idList.add(id.toString()));
        if (idList.isEmpty()) return List.of();
        return dsl.select(DSL.asterisk())
                  .from(baseTable())
                  .where(DSL.field("id").in(idList))
                  .fetch()
                  .map(this::fromRecord);
    }

    @Override
    public List<T> findAll() {
        return dsl.select(DSL.asterisk())
                  .from(baseTable())
                  .fetch()
                  .map(this::fromRecord);
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        var table = baseTable();
        List<T> content = dsl.select(DSL.asterisk())
                .from(table)
                .limit(pageable.getPageSize())
                .offset(pageable.getOffset())
                .fetch()
                .map(this::fromRecord);
        Long total = dsl.select(DSL.field("count()", Long.class))
                .from(table)
                .fetchOne(0, Long.class);
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public long count() {
        Long result = dsl.select(DSL.field("count()", Long.class))
                .from(baseTable())
                .fetchOne(0, Long.class);
        return result != null ? result : 0L;
    }

    @Override
    public boolean existsById(ID id) {
        Long result = dsl.select(DSL.field("count()", Long.class))
                .from(baseTable())
                .where(DSL.field("id").eq(id.toString()))
                .fetchOne(0, Long.class);
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

    /** Reads a Nullable DateTime column from a jOOQ Record. */
    protected static OffsetDateTime recordDateTime(Record r, String col) {
        Object val = r.get(col);
        if (val == null) return null;
        if (val instanceof Timestamp ts)       return ts.toInstant().atOffset(ZoneOffset.UTC);
        if (val instanceof LocalDateTime ldt)  return ldt.atOffset(ZoneOffset.UTC);
        if (val instanceof OffsetDateTime odt) return odt;
        if (val instanceof String s) {
            if (s.isEmpty()) return null;
            try { return OffsetDateTime.parse(s); } catch (Exception ignored) {}
            try { return LocalDateTime.parse(s).atOffset(ZoneOffset.UTC); } catch (Exception ignored) {}
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
