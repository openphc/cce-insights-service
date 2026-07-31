package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.enums.DeviationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.openphc.cce.insights.jooq.Tables.DEVIATIONS;
import static org.openphc.cce.insights.jooq.Tables.INBOUND_EVENT_LOGS;
import static org.openphc.cce.insights.jooq.Tables.PROTOCOL_INSTANCES;
import static org.openphc.cce.insights.jooq.Tables.STEP_INSTANCES;

@Repository
public class DeviationRepositoryImpl
        extends AbstractClickHouseRepository<Deviation, UUID>
        implements DeviationRepository {

    public DeviationRepositoryImpl(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected String getTableName() {
        return DEVIATIONS.getName();
    }

    @Override
    protected Deviation fromRecord(Record r) {
        return toDeviation(r);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Result mappers
    // ══════════════════════════════════════════════════════════════════════════════

    private Deviation toDeviation(Record r) {
        DeviationType dt = null;
        try {
            String s = r.get(DEVIATIONS.DEVIATION_TYPE.getName(), String.class);
            if (s != null) dt = DeviationType.valueOf(s);
        } catch (Exception ignored) {}
        return Deviation.builder()
                .id(r.get(DEVIATIONS.ID.getName(), UUID.class))
                .protocolInstanceId(r.get(DEVIATIONS.PROTOCOL_INSTANCE_ID.getName(), UUID.class))
                .stepInstanceId(r.get(DEVIATIONS.STEP_INSTANCE_ID.getName(), UUID.class))
                .deviationType(dt)
                .detectedAt(recordDateTime(r, DEVIATIONS.DETECTED_AT.getName()))
                .metadata(r.get(DEVIATIONS.METADATA.getName(), String.class))
                .intelligenceEventId(parseUUID(r.get(DEVIATIONS.INTELLIGENCE_EVENT_ID.getName(), String.class)))
                .build();
    }

    /**
     * Clinical occurrence date of a deviation — when it actually HAPPENED — resolved from the linked
     * step's engine-computed clinical dates, keyed by deviation_type, with a fallback chain:
     *   OVERDUE → step.overdue_date, MISSED → step.missed_date, ORDER_VIOLATION → step.completed_at,
     *   then step.due_date, then detected_at (last resort). Distinct from detected_at, which is when
     *   OUR SYSTEM flagged it (processing time). Deviation metrics filter/bucket on THIS, not detected_at.
     * Requires the query to expose step_instances as alias "si" (join on d.step_instance_id = si.id).
     */
    private String occurredAt() {
        String type = "d." + DEVIATIONS.DEVIATION_TYPE.getName();
        return "coalesce(multiIf(" +
                type + " = 'OVERDUE', si." + STEP_INSTANCES.OVERDUE_DATE.getName() + ", " +
                type + " = 'MISSED', si." + STEP_INSTANCES.MISSED_DATE.getName() + ", " +
                type + " = 'ORDER_VIOLATION', si." + STEP_INSTANCES.COMPLETED_AT.getName() + ", " +
                "CAST(NULL AS Nullable(DateTime64(6)))), si." + STEP_INSTANCES.DUE_DATE.getName() +
                ", d." + DEVIATIONS.DETECTED_AT.getName() + ")";
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Repository methods — full jOOQ DSL
    // ══════════════════════════════════════════════════════════════════════════════

    @Override
    public List<Deviation> findByProtocolInstanceId(UUID protocolInstanceId) {
        var d = finalAs(DEVIATIONS, "d");
        return dsl.select(DSL.asterisk())
                  .from(d)
                  .where(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = toUUID(?)",
                          protocolInstanceId.toString()))
                  .fetch()
                  .map(this::toDeviation);
    }

    @Override
    public List<Deviation> findByProtocolInstanceIdIn(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> idStrings = ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.toList());
        var d = finalAs(DEVIATIONS, "d");
        List<Deviation> result = new java.util.ArrayList<>();
        for (List<String> chunk : chunkIds(idStrings)) {
            result.addAll(dsl.select(DSL.asterisk())
                    .from(d)
                    .where(DSL.field("d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName()).in(chunk))
                    .fetch()
                    .map(this::toDeviation));
        }
        return result;
    }

    @Override
    public List<Object[]> countDeviationsByProtocolInstanceIdIn(List<UUID> ids,
                                                                 OffsetDateTime startDate,
                                                                 OffsetDateTime endDate) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> idStrings = ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.toList());
        var d = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");   // for occurredAt() clinical date
        String detectedAt = occurredAt();  // clinical occurrence date, not system detection time
        // Each protocol_instance_id belongs to exactly one chunk, so groupBy results across
        // chunks are disjoint and can be concatenated without merging/double-counting.
        List<Object[]> result = new java.util.ArrayList<>();
        for (List<String> chunk : chunkIds(idStrings)) {
            org.jooq.Condition where = DSL.field("d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName()).in(chunk);
            if (startDate != null) {
                where = where.and(DSL.condition(
                        detectedAt + " >= parseDateTime64BestEffort(?)", startDate.toString()));
            }
            if (endDate != null) {
                where = where.and(DSL.condition(
                        detectedAt + " <= parseDateTime64BestEffort(?)", endDate.toString()));
            }
            result.addAll(dsl.select(
                        DSL.field("d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName()),
                        DSL.field("count()", Long.class).as("cnt")
                    )
                    .from(d)
                    .leftJoin(si).on(DSL.condition(
                            "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                    .where(where)
                    .groupBy(DSL.field("d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName()))
                    .fetch()
                    .map(r -> new Object[]{r.get(0, UUID.class), r.get(1, Long.class)}));
        }
        return result;
    }

    @Override
    public List<Object[]> findDeviationTypesByInstanceIdIn(List<UUID> ids,
                                                            OffsetDateTime startDate,
                                                            OffsetDateTime endDate) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> idStrings = ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.toList());
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");   // for occurredAt() clinical date
        String occurred = occurredAt();           // clinical occurrence date, not system detection time
        List<Object[]> result = new java.util.ArrayList<>();
        for (List<String> chunk : chunkIds(idStrings)) {
            org.jooq.Condition where = DSL.field("d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName()).in(chunk);
            if (startDate != null) {
                where = where.and(DSL.condition(occurred + " >= parseDateTime64BestEffort(?)", startDate.toString()));
            }
            if (endDate != null) {
                where = where.and(DSL.condition(occurred + " <= parseDateTime64BestEffort(?)", endDate.toString()));
            }
            result.addAll(dsl.select(
                        DSL.field("d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName()),
                        DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName())
                    )
                    .from(d)
                    .leftJoin(si).on(DSL.condition(
                            "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                    .where(where)
                    .fetch()
                    .map(r -> new Object[]{r.get(0, UUID.class), r.get(1, String.class)}));
        }
        return result;
    }

    @Override
    public List<Object[]> findDeviationCountsByFacilityGroupedByProtocol(String facilityId) {
        var d  = finalAs(DEVIATIONS, "d");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table("mv_patient_facility_latest").as("pf");
        String zeroUuid = "toUUID('00000000-0000-0000-0000-000000000000')";

        return dsl.select(
                    DSL.field("pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName()),
                    DSL.field("countIf(d.id != " + zeroUuid + ")", Long.class).as("deviation_count")
                )
                .from(pi)
                .join(pf).on(DSL.condition(
                        "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .leftJoin(d).on(DSL.condition(
                        "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .where(DSL.field("pf.facility_id").eq(facilityId))
                .groupBy(DSL.field("pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName()))
                .fetch()
                .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public Page<Deviation> findByDeviationType(DeviationType type, Pageable pageable) {
        var d = finalAs(DEVIATIONS, "d");
        var condition = DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()).eq(type.name());
        List<Deviation> content = dsl.select(DSL.asterisk())
                .from(d)
                .where(condition)
                .orderBy(DSL.field("d." + DEVIATIONS.DETECTED_AT.getName()).desc())
                .limit(pageable.getPageSize())
                .offset(pageable.getOffset())
                .fetch()
                .map(this::toDeviation);
        Long total = dsl.select(DSL.field("count()", Long.class))
                .from(d)
                .where(condition)
                .fetchOne(0, Long.class);
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /** "district blank OR facility column in the district's facilities" — resolved via facility ref. */
    private org.jooq.Condition districtScope(String facilityColumn, String district) {
        String d = str(district);
        return DSL.condition("? = '' OR " + facilityColumn
                + " IN (SELECT facility_id FROM facility" + finalClause()
                + " WHERE _is_deleted = 0 AND lower(district_name) = lower(?))", d, d);
    }

    @Override
    public List<Object[]> findFilteredDeviations(String deviationType, String facilityId, String district,
                                                  UUID protocolDefinitionId,
                                                  OffsetDateTime startDate, OffsetDateTime endDate,
                                                  int lim) {
        String dtype = str(deviationType);
        String fid   = str(facilityId);
        String pid   = uuid(protocolDefinitionId);
        var d  = finalAs(DEVIATIONS, "d");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var si = finalAs(STEP_INSTANCES, "si");
        var pf = DSL.table("mv_patient_facility_latest").as("pf");

        return dsl.select(
                    DSL.field("d." + DEVIATIONS.ID.getName()),
                    DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()),
                    DSL.field("d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName()),
                    DSL.field("pi." + PROTOCOL_INSTANCES.PROTOCOL_CANONICAL.getName()),
                    DSL.field("d." + DEVIATIONS.STEP_INSTANCE_ID.getName()),
                    DSL.field("si." + STEP_INSTANCES.ACTION_ID.getName()),
                    DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()),
                    DSL.field("d." + DEVIATIONS.DETECTED_AT.getName()).as(DEVIATIONS.DETECTED_AT.getName()),
                    DSL.field("pf.facility_id"),
                    DSL.field(DSL.sql(occurredAt())).as("occurred_at"))   // clinical occurrence date
                  .from(d)
                  .join(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .join(si).on(DSL.condition(
                          "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                  .leftJoin(pf).on(DSL.condition(
                          "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .where(DSL.condition(
                          "? = '' OR d." + DEVIATIONS.DEVIATION_TYPE.getName() + " = ?", dtype, dtype))
                  .and(DSL.condition("? = '' OR pf.facility_id = ?", fid, fid))
                  .and(districtScope("pf.facility_id", district))
                  .and(DSL.condition(
                          "toUUIDOrNull(?) IS NULL OR pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUIDOrNull(?)",
                          pid, pid))
                  .and(DSL.condition(
                          occurredAt() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          occurredAt() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .orderBy(DSL.field(DSL.sql(occurredAt())).desc())
                  .limit(lim)
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class), r.get(1, String.class), r.get(2, String.class),
                          r.get(3, String.class), r.get(4, String.class), r.get(5, String.class),
                          r.get(6, String.class),
                          recordDateTime(r, DEVIATIONS.DETECTED_AT.getName()),
                          r.get(8, String.class),
                          recordDateTime(r, "occurred_at")});
    }

    @Override
    public List<Object[]> findDeviationTrends(String interval, OffsetDateTime startDate,
                                               OffsetDateTime endDate, String facilityId, String district,
                                               UUID protocolDefinitionId) {
        // MV-first: read mv_daily_deviation_kpis. Buckets on the clinical occurrence day
        // (snapshot_date); deviation_count is additive so sum() rolls up per interval.
        String fid = str(facilityId);
        String pid = uuid(protocolDefinitionId);
        String periodExpr = dateTruncExpr(interval, "snapshot_date");
        return dsl.select(
                    DSL.field(DSL.sql(periodExpr)).as("period"),
                    DSL.field("deviation_type"),
                    DSL.field("sum(deviation_count)", Long.class).as("cnt"))
                  .from(DSL.table(DSL.sql("mv_daily_deviation_kpis" + finalClause())))
                  .where(DSL.condition("snapshot_date >= toDate(parseDateTime64BestEffort(?))", dtStart(startDate)))
                  .and(DSL.condition("snapshot_date <= toDate(parseDateTime64BestEffort(?))", dtEnd(endDate)))
                  .and(DSL.condition("? = '' OR facility_id = ?", fid, fid))
                  .and(districtScope("facility_id", district))
                  .and(DSL.condition("toUUIDOrNull(?) IS NULL OR protocol_definition_id = ?", pid, pid))
                  .groupBy(
                          DSL.field(DSL.sql("period")),
                          DSL.field("deviation_type"))
                  .orderBy(DSL.field(DSL.sql("period")))
                  .fetch()
                  .map(r -> new Object[]{r.value1(), r.get(1, String.class), r.value3()});
    }

    @Override
    public List<Object[]> findDeviationsByAction(UUID protocolDefId, String facilityId, String district,
                                                  OffsetDateTime startDate, OffsetDateTime endDate) {
        // MV-first: read mv_daily_deviation_kpis grouped by action. Counts are additive (sum/sumIf);
        // affected patients is a uniq STATE merged over the range (uniqMerge) so distinct patients are
        // correct across days. protocol_canonical is carried in the MV (any() — 1:1 with protocol).
        // The MV is facility-grained, so facilityId scopes directly (same as the trends read).
        String pid = uuid(protocolDefId);
        String fid = str(facilityId);
        return dsl.select(
                    DSL.field("action_id"),
                    DSL.field("protocol_definition_id"),
                    DSL.field("any(protocol_canonical)").as("protocol_canonical"),
                    DSL.field("sum(deviation_count)", Long.class).as("total_deviations"),
                    DSL.field("sumIf(deviation_count, deviation_type = 'OVERDUE')", Long.class).as("overdue_count"),
                    DSL.field("sumIf(deviation_count, deviation_type = 'MISSED')", Long.class).as("missed_count"),
                    DSL.field("sumIf(deviation_count, deviation_type = 'ORDER_VIOLATION')", Long.class).as("order_violation_count"),
                    DSL.field("uniqMerge(affected_patients_state)", Long.class).as("affected_patients"))
                  .from(DSL.table(DSL.sql("mv_daily_deviation_kpis" + finalClause())))
                  .where(DSL.condition("toUUIDOrNull(?) IS NULL OR protocol_definition_id = ?", pid, pid))
                  .and(DSL.condition("snapshot_date >= toDate(parseDateTime64BestEffort(?))", dtStart(startDate)))
                  .and(DSL.condition("snapshot_date <= toDate(parseDateTime64BestEffort(?))", dtEnd(endDate)))
                  .and(DSL.condition("? = '' OR facility_id = ?", fid, fid))
                  .and(districtScope("facility_id", district))
                  .groupBy(
                          DSL.field("action_id"),
                          DSL.field("protocol_definition_id"))
                  .orderBy(DSL.field("total_deviations").desc())
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class), r.get(1, String.class), r.get(2, String.class),
                          r.get(3, Long.class), r.get(4, Long.class), r.get(5, Long.class),
                          r.get(6, Long.class), r.get(7, Long.class)});
    }

    @Override
    public List<Object[]> findResolutionRate(UUID protocolDefId, OffsetDateTime startDate,
                                             OffsetDateTime endDate) {
        String pid = uuid(protocolDefId);
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");

        // Subquery: resolve IN clause against protocol_instances with FINAL if enabled.
        var piSubquery = dsl.select(DSL.field("id"))
                            .from(DSL.table(DSL.sql(PROTOCOL_INSTANCES.getName() + finalClause())))
                            .where(DSL.condition(
                                    PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUIDOrNull(?)", pid));

        return dsl.select(
                    DSL.field("countIf(si." + STEP_INSTANCES.STATE.getName() + " = 'COMPLETED')", Long.class).as("resolved_count"),
                    DSL.field("countIf(si." + STEP_INSTANCES.STATE.getName() + " = 'MISSED')", Long.class).as("escalated_count"),
                    DSL.field("count()", Long.class).as("total_overdue"),
                    DSL.field("avgIf(dateDiff('second', d." + DEVIATIONS.DETECTED_AT.getName() +
                              ", si." + STEP_INSTANCES.COMPLETED_AT.getName() + ") / 86400.0" +
                              ", si." + STEP_INSTANCES.STATE.getName() + " = 'COMPLETED')", Double.class).as("avg_days_to_resolve"))
                  .from(d)
                  .join(si).on(DSL.condition(
                          "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                  .where(DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()).eq("OVERDUE"))
                  .and(DSL.condition("toUUIDOrNull(?) IS NULL", pid)
                      .or(DSL.field("d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName()).in(piSubquery)))
                  .and(DSL.condition(
                          occurredAt() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          occurredAt() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .fetch()
                  .map(r -> new Object[]{r.value1(), r.value2(), r.value3(), r.value4()});
    }

    @Override
    public List<Object[]> countByTypeSince(OffsetDateTime since, String facilityId) {
        String fid = str(facilityId);
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");   // for occurredAt() clinical date
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table("mv_patient_facility_latest").as("pf");
        return dsl.select(
                    DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()),
                    DSL.field("count()", Long.class))
                  .from(d)
                  .leftJoin(si).on(DSL.condition(
                          "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                  .join(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .leftJoin(pf).on(DSL.condition(
                          "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .where(DSL.condition(
                          occurredAt() + " >= parseDateTime64BestEffort(?)",
                          dt(since)))
                  .and(DSL.condition("? = '' OR pf.facility_id = ?", fid, fid))
                  .groupBy(DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countByTypeFiltered(UUID protocolDefinitionId, String facilityId, String district,
                                              OffsetDateTime startDate, OffsetDateTime endDate) {
        // MV-first: read the pre-aggregated mv_daily_deviation_kpis (one row per occurrence-day × dims)
        // instead of a live 4-table join. Each deviation is counted once on its clinical occurrence
        // day, so sum(deviation_count) over the range is the true total (no snapshot double-count).
        String fid = str(facilityId);
        String pid = uuid(protocolDefinitionId);
        return dsl.select(
                    DSL.field("deviation_type"),
                    DSL.field("sum(deviation_count)", Long.class))
                  .from(DSL.table(DSL.sql("mv_daily_deviation_kpis" + finalClause())))
                  .where(DSL.condition("snapshot_date >= toDate(parseDateTime64BestEffort(?))", dtStart(startDate)))
                  .and(DSL.condition("snapshot_date <= toDate(parseDateTime64BestEffort(?))", dtEnd(endDate)))
                  .and(DSL.condition("? = '' OR facility_id = ?", fid, fid))
                  .and(districtScope("facility_id", district))
                  .and(DSL.condition("toUUIDOrNull(?) IS NULL OR protocol_definition_id = ?", pid, pid))
                  .groupBy(DSL.field("deviation_type"))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countByTypeInRange(OffsetDateTime startDate, OffsetDateTime endDate,
                                             String facilityId) {
        String fid = str(facilityId);
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");   // for occurredAt() clinical date
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table("mv_patient_facility_latest").as("pf");

        return dsl.select(
                    DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()),
                    DSL.field("count()", Long.class))
                  .from(d)
                  .leftJoin(si).on(DSL.condition(
                          "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                  .join(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .leftJoin(pf).on(DSL.condition(
                          "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .where(DSL.condition(
                          occurredAt() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          occurredAt() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .and(DSL.condition("? = '' OR pf.facility_id = ?", fid, fid))
                  .groupBy(DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> findRepeatDeviationPatients(int minDeviations, String facilityId,
                                                       OffsetDateTime startDate,
                                                       OffsetDateTime endDate) {
        String fid = str(facilityId);
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");   // for occurredAt() clinical date
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table("mv_patient_facility_latest").as("pf");

        return dsl.select(
                    DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()),
                    DSL.field("count()", Long.class).as("total_deviations"),
                    DSL.field("countIf(d." + DEVIATIONS.DEVIATION_TYPE.getName() + " = 'OVERDUE')", Long.class).as("overdue_count"),
                    DSL.field("countIf(d." + DEVIATIONS.DEVIATION_TYPE.getName() + " = 'MISSED')", Long.class).as("missed_count"),
                    DSL.field("countIf(d." + DEVIATIONS.DEVIATION_TYPE.getName() + " = 'ORDER_VIOLATION')", Long.class).as("order_violation_count"),
                    DSL.field("uniq(pi.id)", Long.class).as("affected_protocols"),
                    DSL.field("uniq(d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + ")", Long.class).as("affected_steps"))
                  .from(d)
                  .leftJoin(si).on(DSL.condition(
                          "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                  .join(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .leftJoin(pf).on(DSL.condition(
                          "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .where(DSL.condition("? = '' OR pf.facility_id = ?", fid, fid))
                  .and(DSL.condition(
                          occurredAt() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          occurredAt() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .having(DSL.field("count()", Long.class).ge((long) minDeviations))
                  .orderBy(DSL.field("total_deviations").desc())
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class), r.get(1, Long.class), r.get(2, Long.class),
                          r.get(3, Long.class), r.get(4, Long.class), r.get(5, Long.class),
                          r.get(6, Long.class)});
    }

    @Override
    public List<Object[]> countDeviationsByFacility() {
        var d   = finalAs(DEVIATIONS, "d");
        var pi  = finalAs(PROTOCOL_INSTANCES, "pi");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");

        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()),
                    DSL.field("uniq(d." + DEVIATIONS.ID.getName() + ")", Long.class).as("deviation_count"))
                  .from(d)
                  .join(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.SUBJECT.getName() + " = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).ne(""))
                  .groupBy(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countDeviationsByFacility(OffsetDateTime startDate, OffsetDateTime endDate) {
        // Date-scoped: deviations DETECTED within [startDate, endDate], attributed to a facility via
        // the patient's current facility (mv_patient_facility_latest) — the same attribution the
        // ranking uses for tracked patients. Null bounds are open (all-time).
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");   // for occurredAt() clinical date
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table(DSL.sql("mv_patient_facility_latest pf" + finalClause()));
        String detectedAt = occurredAt();  // clinical occurrence date, not system detection time

        org.jooq.Condition where = DSL.field("pf.facility_id").ne("");
        if (startDate != null) {
            where = where.and(DSL.condition(
                    detectedAt + " >= parseDateTime64BestEffort(?)", startDate.toString()));
        }
        if (endDate != null) {
            where = where.and(DSL.condition(
                    detectedAt + " <= parseDateTime64BestEffort(?)", endDate.toString()));
        }

        return dsl.select(
                    DSL.field("pf.facility_id", String.class),
                    DSL.field("uniq(d." + DEVIATIONS.ID.getName() + ")", Long.class).as("deviation_count"))
                  .from(d)
                  .leftJoin(si).on(DSL.condition(
                          "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                  .join(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .join(pf).on(DSL.condition(
                          "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .where(where)
                  .groupBy(DSL.field("pf.facility_id"))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    /** Shared WHERE clause for the by-facility(+type) queries — {@link
     *  #findDeviationsByFacilityAndType} and {@link #countFacilitiesWithDeviations} must scope
     *  identically or the pagination total won't match the page contents. */
    private org.jooq.Condition byFacilityWhere(String fid, String district, String pid,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        String detectedAt = occurredAt();
        org.jooq.Condition where = DSL.field("pf.facility_id").ne("");
        where = where.and(DSL.condition("? = '' OR pf.facility_id = ?", fid, fid));
        where = where.and(districtScope("pf.facility_id", district));
        where = where.and(DSL.condition(
                "toUUIDOrNull(?) IS NULL OR pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUIDOrNull(?)",
                pid, pid));
        if (startDate != null) {
            where = where.and(DSL.condition(
                    detectedAt + " >= parseDateTime64BestEffort(?)", startDate.toString()));
        }
        if (endDate != null) {
            where = where.and(DSL.condition(
                    detectedAt + " <= parseDateTime64BestEffort(?)", endDate.toString()));
        }
        return where;
    }

    private static final java.util.Set<String> SORT_COLUMNS = java.util.Set.of(
            "overdue_count", "missed_count", "order_violation_count", "total_deviations");

    @Override
    public List<Object[]> findDeviationsByFacilityAndType(String facilityId, String district, UUID protocolDefinitionId,
                                                           OffsetDateTime startDate, OffsetDateTime endDate,
                                                           String sortBy, int limit, int offset) {
        String fid = str(facilityId);
        String pid = uuid(protocolDefinitionId);
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table(DSL.sql("mv_patient_facility_latest pf" + finalClause()));
        String devId = "d." + DEVIATIONS.ID.getName();
        String devType = "d." + DEVIATIONS.DEVIATION_TYPE.getName();
        // Whitelisted against SORT_COLUMNS — never interpolate an unvalidated value into ORDER BY.
        String sortColumn = SORT_COLUMNS.contains(sortBy) ? sortBy : "total_deviations";

        return dsl.select(
                    DSL.field("pf.facility_id", String.class),
                    DSL.field("uniqIf(" + devId + ", " + devType + " = 'OVERDUE')", Long.class).as("overdue_count"),
                    DSL.field("uniqIf(" + devId + ", " + devType + " = 'MISSED')", Long.class).as("missed_count"),
                    DSL.field("uniqIf(" + devId + ", " + devType + " = 'ORDER_VIOLATION')", Long.class).as("order_violation_count"),
                    DSL.field("uniq(" + devId + ")", Long.class).as("total_deviations"))
                  .from(d)
                  .leftJoin(si).on(DSL.condition(
                          "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                  .join(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .join(pf).on(DSL.condition(
                          "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .where(byFacilityWhere(fid, district, pid, startDate, endDate))
                  .groupBy(DSL.field("pf.facility_id"))
                  .orderBy(DSL.field(sortColumn).desc())
                  .limit(limit)
                  .offset(offset)
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class), r.get(1, Long.class), r.get(2, Long.class),
                          r.get(3, Long.class), r.get(4, Long.class)});
    }

    @Override
    public long countFacilitiesWithDeviations(String facilityId, String district, UUID protocolDefinitionId,
                                              OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        String pid = uuid(protocolDefinitionId);
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table(DSL.sql("mv_patient_facility_latest pf" + finalClause()));

        Long r = dsl.select(DSL.field("uniq(pf.facility_id)", Long.class))
                    .from(d)
                    .leftJoin(si).on(DSL.condition(
                            "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                    .join(pi).on(DSL.condition(
                            "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                    .join(pf).on(DSL.condition(
                            "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                    .where(byFacilityWhere(fid, district, pid, startDate, endDate))
                    .fetchOne(0, Long.class);
        return r == null ? 0L : r;
    }

    @Override
    public long countDistinctPatientsWithDeviations() {
        var d  = finalAs(DEVIATIONS, "d");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");

        Long r = dsl.select(
                        DSL.field("uniq(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ")", Long.class))
                    .from(d)
                    .join(pi).on(DSL.condition(
                            "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countDistinctPatientsWithDeviationsBetween(OffsetDateTime startDate,
                                                            OffsetDateTime endDate) {
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");   // for occurredAt() clinical date
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        String detectedAt = occurredAt();  // clinical occurrence date, not system detection time
        String enrolledAt = "pi." + PROTOCOL_INSTANCES.ENROLLED_AT.getName();

        org.jooq.Condition where = DSL.trueCondition();
        if (startDate != null) {
            where = where.and(DSL.condition(
                    detectedAt + " >= parseDateTime64BestEffort(?)", startDate.toString()));
            where = where.and(DSL.condition(
                    enrolledAt + " >= parseDateTime64BestEffort(?)", startDate.toString()));
        }
        if (endDate != null) {
            where = where.and(DSL.condition(
                    detectedAt + " <= parseDateTime64BestEffort(?)", endDate.toString()));
            where = where.and(DSL.condition(
                    enrolledAt + " <= parseDateTime64BestEffort(?)", endDate.toString()));
        }

        Long r = dsl.select(
                        DSL.field("uniq(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ")", Long.class))
                    .from(d)
                    .leftJoin(si).on(DSL.condition(
                            "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                    .join(pi).on(DSL.condition(
                            "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                    .where(where)
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countDistinctPatientsWithDeviationsBetween(String facilityId,
                                                            OffsetDateTime startDate, OffsetDateTime endDate) {
        if (facilityId == null || facilityId.isEmpty()) {
            return countDistinctPatientsWithDeviationsBetween(startDate, endDate);
        }
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");   // for occurredAt() clinical date
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table("mv_patient_facility_latest").as("pf");
        String occurred   = occurredAt();  // clinical occurrence date, not system detection time
        String enrolledAt = "pi." + PROTOCOL_INSTANCES.ENROLLED_AT.getName();

        org.jooq.Condition where = DSL.field("pf.facility_id").eq(facilityId);
        if (startDate != null) {
            where = where.and(DSL.condition(occurred   + " >= parseDateTime64BestEffort(?)", startDate.toString()));
            where = where.and(DSL.condition(enrolledAt + " >= parseDateTime64BestEffort(?)", startDate.toString()));
        }
        if (endDate != null) {
            where = where.and(DSL.condition(occurred   + " <= parseDateTime64BestEffort(?)", endDate.toString()));
            where = where.and(DSL.condition(enrolledAt + " <= parseDateTime64BestEffort(?)", endDate.toString()));
        }

        Long r = dsl.select(
                        DSL.field("uniq(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ")", Long.class))
                    .from(d)
                    .leftJoin(si).on(DSL.condition(
                            "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                    .join(pi).on(DSL.condition(
                            "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                    .join(pf).on(DSL.condition(
                            "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                    .where(where)
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    // ── RI-36 non-compliant among the matched-event cohort ────────────────────────
    // Of the RI-36 tracked cohort (patients with a protocol-MATCHED event in range — see
    // InboundEventRepository.countDistinctPatientsWithMatchedEvents), how many have a deviation whose
    // clinical OCCURRENCE date is in [start,end] (NOT detected_at — matches the Deviations page).
    // The cohort is a ClickHouse IN-subquery (not a bound-param list → no max_query_size risk).
    @Override
    public long countDistinctNonCompliantAmongMatched(String facilityId, String district,
                                                       OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        String dist = str(district);
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");   // for occurredAt() clinical date
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        String occurred = occurredAt();

        String cohortIn =
                "pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + " IN ("
                + " SELECT iel.subject FROM inbound_event_logs iel" + finalClause()
                + " WHERE iel.status = 'ACCEPTED' AND iel.subject != ''"
                + " AND (? = '' OR iel.facility_id = ?)"
                + " AND (? = '' OR iel.facility_id IN (SELECT facility_id FROM facility" + finalClause()
                + " WHERE _is_deleted = 0 AND lower(district_name) = lower(?)))"
                + " AND iel.cloudevents_id IN (SELECT cel.cloudevents_id FROM compliance_event_logs cel"
                + finalClause() + " WHERE cel.processing_status = 'MATCHED')"
                + " AND (? != '1' OR iel.event_time >= parseDateTime64BestEffort(?))"
                + " AND (? != '1' OR iel.event_time <= parseDateTime64BestEffort(?)))";

        org.jooq.Condition where = DSL.condition(cohortIn,
                fid, fid,
                dist, dist,
                startDate == null ? "0" : "1", dtStart(startDate),
                endDate   == null ? "0" : "1", dtEnd(endDate));
        // Deviation OCCURRENCE (clinical) in range — NOT detected_at, NO enrolled_at.
        if (startDate != null) {
            where = where.and(DSL.condition(occurred + " >= parseDateTime64BestEffort(?)", startDate.toString()));
        }
        if (endDate != null) {
            where = where.and(DSL.condition(occurred + " <= parseDateTime64BestEffort(?)", endDate.toString()));
        }

        Long r = dsl.select(
                        DSL.field("uniq(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ")", Long.class))
                    .from(d)
                    .leftJoin(si).on(DSL.condition(
                            "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                    .join(pi).on(DSL.condition(
                            "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                    .where(where)
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public Object[] aggregateDeviationMetrics(UUID protocolDefinitionId) {
        var d  = finalAs(DEVIATIONS, "d");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        String devType  = "d." + DEVIATIONS.DEVIATION_TYPE.getName();
        String zeroUuid = "toUUID('00000000-0000-0000-0000-000000000000')";

        Table<?> perInstance = dsl.select(
                    DSL.field("pi.id"),
                    DSL.field("countIf(" + devType + " = 'OVERDUE')",         Long.class).as("overdue_devs"),
                    DSL.field("countIf(" + devType + " = 'MISSED')",          Long.class).as("missed_devs"),
                    DSL.field("countIf(" + devType + " = 'ORDER_VIOLATION')", Long.class).as("order_devs"),
                    DSL.field("countIf(d.id != " + zeroUuid + ")",            Long.class).as("total_devs")
                )
                .from(pi)
                .leftJoin(d).on(DSL.condition(
                        "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .where(DSL.condition(
                        "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                        protocolDefinitionId.toString()))
                .groupBy(DSL.field("pi.id"))
                .asTable("t");

        org.jooq.Record r = dsl.select(
                    DSL.field("countIf(t.total_devs = 0)", Long.class).as("compliant_patients"),
                    DSL.field("sum(t.total_devs)",          Long.class).as("total_deviations"),
                    DSL.field("sum(t.overdue_devs)",        Long.class).as("overdue_deviations"),
                    DSL.field("sum(t.missed_devs)",         Long.class).as("missed_deviations"),
                    DSL.field("sum(t.order_devs)",          Long.class).as("order_violation_deviations")
                )
                .from(perInstance)
                .fetchOne();
        return r != null ? r.intoArray() : new Object[]{0L, 0L, 0L, 0L, 0L};
    }

    @Override
    public Object[] aggregateDeviationMetricsByFacility(String facilityId) {
        var d  = finalAs(DEVIATIONS, "d");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table("mv_patient_facility_latest").as("pf");
        String devType  = "d." + DEVIATIONS.DEVIATION_TYPE.getName();
        String zeroUuid = "toUUID('00000000-0000-0000-0000-000000000000')";

        Table<?> perInstance = dsl.select(
                    DSL.field("pi.id"),
                    DSL.field("countIf(" + devType + " = 'OVERDUE')",         Long.class).as("overdue_devs"),
                    DSL.field("countIf(" + devType + " = 'MISSED')",          Long.class).as("missed_devs"),
                    DSL.field("countIf(" + devType + " = 'ORDER_VIOLATION')", Long.class).as("order_devs"),
                    DSL.field("countIf(d.id != " + zeroUuid + ")",            Long.class).as("total_devs")
                )
                .from(pi)
                .join(pf).on(DSL.condition(
                        "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .leftJoin(d).on(DSL.condition(
                        "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .where(DSL.field("pf.facility_id").eq(facilityId))
                .groupBy(DSL.field("pi.id"))
                .asTable("t");

        org.jooq.Record r = dsl.select(
                    DSL.field("countIf(t.total_devs = 0)", Long.class).as("compliant_patients"),
                    DSL.field("sum(t.total_devs)",          Long.class).as("total_deviations"),
                    DSL.field("sum(t.overdue_devs)",        Long.class).as("overdue_deviations"),
                    DSL.field("sum(t.missed_devs)",         Long.class).as("missed_deviations"),
                    DSL.field("sum(t.order_devs)",          Long.class).as("order_violation_deviations")
                )
                .from(perInstance)
                .fetchOne();
        return r != null ? r.intoArray() : new Object[]{0L, 0L, 0L, 0L, 0L};
    }

    @Override
    public Object[] aggregateDeviationMetricsByProtocolAndFacility(UUID protocolDefinitionId, String facilityId) {
        var d  = finalAs(DEVIATIONS, "d");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table("mv_patient_facility_latest").as("pf");
        String devType  = "d." + DEVIATIONS.DEVIATION_TYPE.getName();
        String zeroUuid = "toUUID('00000000-0000-0000-0000-000000000000')";

        Table<?> perInstance = dsl.select(
                    DSL.field("pi.id"),
                    DSL.field("countIf(" + devType + " = 'OVERDUE')",         Long.class).as("overdue_devs"),
                    DSL.field("countIf(" + devType + " = 'MISSED')",          Long.class).as("missed_devs"),
                    DSL.field("countIf(" + devType + " = 'ORDER_VIOLATION')", Long.class).as("order_devs"),
                    DSL.field("countIf(d.id != " + zeroUuid + ")",            Long.class).as("total_devs")
                )
                .from(pi)
                .join(pf).on(DSL.condition(
                        "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .leftJoin(d).on(DSL.condition(
                        "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .where(DSL.condition(
                        "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                        protocolDefinitionId.toString()))
                .and(DSL.field("pf.facility_id").eq(facilityId))
                .groupBy(DSL.field("pi.id"))
                .asTable("t");

        org.jooq.Record r = dsl.select(
                    DSL.field("countIf(t.total_devs = 0)", Long.class).as("compliant_patients"),
                    DSL.field("sum(t.total_devs)",          Long.class).as("total_deviations"),
                    DSL.field("sum(t.overdue_devs)",        Long.class).as("overdue_deviations"),
                    DSL.field("sum(t.missed_devs)",         Long.class).as("missed_deviations"),
                    DSL.field("sum(t.order_devs)",          Long.class).as("order_violation_deviations")
                )
                .from(perInstance)
                .fetchOne();
        return r != null ? r.intoArray() : new Object[]{0L, 0L, 0L, 0L, 0L};
    }

    @Override
    public Object[] aggregateDeviationMetricsAll() {
        var d  = finalAs(DEVIATIONS, "d");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        String devType  = "d." + DEVIATIONS.DEVIATION_TYPE.getName();
        String zeroUuid = "toUUID('00000000-0000-0000-0000-000000000000')";

        Table<?> perInstance = dsl.select(
                    DSL.field("pi.id"),
                    DSL.field("countIf(" + devType + " = 'OVERDUE')",         Long.class).as("overdue_devs"),
                    DSL.field("countIf(" + devType + " = 'MISSED')",          Long.class).as("missed_devs"),
                    DSL.field("countIf(" + devType + " = 'ORDER_VIOLATION')", Long.class).as("order_devs"),
                    DSL.field("countIf(d.id != " + zeroUuid + ")",            Long.class).as("total_devs")
                )
                .from(pi)
                .leftJoin(d).on(DSL.condition(
                        "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .groupBy(DSL.field("pi.id"))
                .asTable("t");

        org.jooq.Record r = dsl.select(
                    DSL.field("countIf(t.total_devs = 0)", Long.class).as("compliant_patients"),
                    DSL.field("sum(t.total_devs)",          Long.class).as("total_deviations"),
                    DSL.field("sum(t.overdue_devs)",        Long.class).as("overdue_deviations"),
                    DSL.field("sum(t.missed_devs)",         Long.class).as("missed_deviations"),
                    DSL.field("sum(t.order_devs)",          Long.class).as("order_violation_deviations")
                )
                .from(perInstance)
                .fetchOne();
        return r != null ? r.intoArray() : new Object[]{0L, 0L, 0L, 0L, 0L};
    }
}
