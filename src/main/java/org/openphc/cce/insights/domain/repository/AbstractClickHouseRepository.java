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

import static org.openphc.cce.insights.jooq.Tables.DEVIATIONS;
import static org.openphc.cce.insights.jooq.Tables.PROTOCOL_DEFINITIONS;
import static org.openphc.cce.insights.jooq.Tables.PROTOCOL_INSTANCES;
import static org.openphc.cce.insights.jooq.Tables.STEP_INSTANCES;
import static org.openphc.cce.insights.jooq.Tables.STEP_SLA_STATE_TRANSITIONS;

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

    /**
     * Same as {@link #finalAs} for the {@code facility} reference table — ReplacingMergeTree, so
     * FINAL is needed to see the deduplicated, current row per facility_id.
     */
    protected Table<?> facilityFinal(String alias) {
        return DSL.table(DSL.sql("facility " + alias + finalClause()));
    }

    /**
     * Table read by the generic find* methods below. Subclasses override it to force FINAL on
     * reference tables where dedup correctness matters more than the FINAL cost, or when their
     * mapper needs derived columns (e.g. protocol_canonical, which 2.0.0 dropped from
     * protocol_instances).
     */
    protected Table<?> baseTable() {
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

    /**
     * protocol_instances plus its {@code protocol_canonical} (url|version), as a derived table
     * aliased {@code alias}. 2.0.0 dropped the denormalised protocol_instances.protocol_canonical;
     * it is rebuilt from protocol_definitions — '' while the definition has not reached ClickHouse.
     * A join rather than dictGet('dict_protocol_definitions', 'canonical', …): the dictionary is an
     * extra moving part (its CLICKHOUSE source authenticates on its own), and protocol_definitions is
     * tiny. url and version never change for an id, so ANY is exact without FINAL on that side.
     * FINAL (when enabled) applies to protocol_instances.
     */
    protected Table<?> protocolInstancesWithCanonical(String alias) {
        return DSL.table(DSL.sql(
                "(SELECT p.*, if(empty(pd." + PROTOCOL_DEFINITIONS.URL.getName() + "), '',"
                + " concat(pd." + PROTOCOL_DEFINITIONS.URL.getName() + ", '|', pd."
                + PROTOCOL_DEFINITIONS.VERSION.getName() + ")) AS protocol_canonical"
                + " FROM " + PROTOCOL_INSTANCES.getName() + " p" + finalClause()
                + " ANY LEFT JOIN " + PROTOCOL_DEFINITIONS.getName() + " pd"
                + " ON pd.id = p." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + ") " + alias));
    }

    /**
     * deviations joined to its enrollment, as a derived table aliased {@code alias}: every
     * deviations column plus {@code protocol_instance_id}. 2.0.0 dropped
     * deviations.protocol_instance_id (reachable as step_instances.protocol_instance_id). A step's
     * protocol_instance_id never changes, so an ANY LEFT JOIN on the plain step table is exact
     * without FINAL — the pipeline's mv_deviation_by_protocol (schema/03) makes the same lookup. (Not ANY
     * INNER: ClickHouse uses each right row once there, which would drop a step's second deviation.)
     * A deviation whose step has not reached ClickHouse yet carries the nil UUID, so it drops out of
     * every read keyed by, or joined to, a real protocol instance. FINAL (when enabled) applies to
     * deviations.
     */
    protected Table<?> deviationsWithInstance(String alias) {
        return DSL.table(DSL.sql(
                "(SELECT dv.*, si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " AS "
                + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName()
                + " FROM " + DEVIATIONS.getName() + " dv" + finalClause()
                + " ANY LEFT JOIN (SELECT id, " + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName()
                + " FROM " + STEP_INSTANCES.getName() + ") AS si"
                + " ON si.id = dv." + DEVIATIONS.STEP_INSTANCE_ID.getName() + ") " + alias));
    }

    /**
     * Per-step SLA thresholds, aliased {@code sla}: one row per step_instance_id with
     * {@code due_threshold} (DUE_DATE_REACHED.process_by) and {@code missed_threshold}
     * (MISSED_DATE_REACHED.process_by, i.e. due date + tolerance-days). These are the clinical
     * dates an OVERDUE / MISSED deviation breached; 1.x kept them on step_instances as
     * overdue_date / missed_date. Mandatory steps only — others have no row. The thresholds are
     * Nullable, so a LEFT JOIN miss reads NULL. Same derivation as the pipeline's
     * mv_daily_deviation_kpis (schema/07).
     */
    protected Table<?> slaThresholds() {
        return DSL.table(DSL.sql(
                "(SELECT " + STEP_SLA_STATE_TRANSITIONS.STEP_INSTANCE_ID.getName() + ","
                + " minIfOrNull(process_by, transition_type = 'DUE_DATE_REACHED') AS due_threshold,"
                + " minIfOrNull(process_by, transition_type = 'MISSED_DATE_REACHED') AS missed_threshold"
                + " FROM " + STEP_SLA_STATE_TRANSITIONS.getName() + finalClause()
                + " WHERE _is_deleted = 0"
                + " GROUP BY " + STEP_SLA_STATE_TRANSITIONS.STEP_INSTANCE_ID.getName() + ") sla"));
    }

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

    /**
     * Max element count for a single jOOQ {@code .in(...)} clause built from this helper.
     * ClickHouse rejects queries whose bound-parameter text exceeds max_query_size
     * (default 262144 bytes); wide date-range filters can otherwise produce IN lists with
     * thousands of UUIDs. Callers should split large id lists with {@link #chunkIds} and
     * issue one query per chunk, merging the results.
     */
    protected static final int MAX_IN_CLAUSE_SIZE = 1000;

    /** Splits a list of ids into chunks no larger than {@link #MAX_IN_CLAUSE_SIZE}. */
    protected static <E> List<List<E>> chunkIds(List<E> ids) {
        List<List<E>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += MAX_IN_CLAUSE_SIZE) {
            chunks.add(ids.subList(i, Math.min(i + MAX_IN_CLAUSE_SIZE, ids.size())));
        }
        return chunks;
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
