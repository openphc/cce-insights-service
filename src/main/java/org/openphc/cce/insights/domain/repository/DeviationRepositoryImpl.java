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

    @Override
    public List<Object[]> findFilteredDeviations(String deviationType, String facilityId,
                                                  OffsetDateTime startDate, OffsetDateTime endDate,
                                                  int lim) {
        String dtype = str(deviationType);
        String fid   = str(facilityId);
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
                    DSL.field("pf.facility_id"))
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
                  .and(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .orderBy(DSL.field("d." + DEVIATIONS.DETECTED_AT.getName()).desc())
                  .limit(lim)
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class), r.get(1, String.class), r.get(2, String.class),
                          r.get(3, String.class), r.get(4, String.class), r.get(5, String.class),
                          r.get(6, String.class),
                          recordDateTime(r, DEVIATIONS.DETECTED_AT.getName()),
                          r.get(8, String.class)});
    }

    @Override
    public List<Object[]> findDeviationTrends(String interval, OffsetDateTime startDate,
                                               OffsetDateTime endDate, String facilityId,
                                               String actionId) {
        String fid = str(facilityId);
        String aid = str(actionId);
        String periodExpr = dateTruncExpr(interval, "d." + DEVIATIONS.DETECTED_AT.getName());
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table("mv_patient_facility_latest").as("pf");

        return dsl.select(
                    DSL.field(DSL.sql(periodExpr)).as("period"),
                    DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(d)
                  .join(si).on(DSL.condition(
                          "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                  .leftJoin(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .leftJoin(pf).on(DSL.condition(
                          "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .where(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .and(DSL.condition("? = '' OR pf.facility_id = ?", fid, fid))
                  .and(DSL.condition(
                          "? = '' OR si." + STEP_INSTANCES.ACTION_ID.getName() + " = ?", aid, aid))
                  .groupBy(
                          DSL.field(DSL.sql("period")),
                          DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()))
                  .orderBy(DSL.field(DSL.sql("period")))
                  .fetch()
                  .map(r -> new Object[]{r.value1(), r.get(1, String.class), r.value3()});
    }

    @Override
    public List<Object[]> findDeviationsByAction(UUID protocolDefId, OffsetDateTime startDate,
                                                  OffsetDateTime endDate) {
        String pid = uuid(protocolDefId);
        var d  = finalAs(DEVIATIONS, "d");
        var si = finalAs(STEP_INSTANCES, "si");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");

        return dsl.select(
                    DSL.field("si." + STEP_INSTANCES.ACTION_ID.getName()),
                    DSL.field("pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName()),
                    DSL.field("pi." + PROTOCOL_INSTANCES.PROTOCOL_CANONICAL.getName()),
                    DSL.field("count()", Long.class).as("total_deviations"),
                    DSL.field("countIf(d." + DEVIATIONS.DEVIATION_TYPE.getName() + " = 'OVERDUE')", Long.class).as("overdue_count"),
                    DSL.field("countIf(d." + DEVIATIONS.DEVIATION_TYPE.getName() + " = 'MISSED')", Long.class).as("missed_count"),
                    DSL.field("countIf(d." + DEVIATIONS.DEVIATION_TYPE.getName() + " = 'ORDER_VIOLATION')", Long.class).as("order_violation_count"),
                    DSL.field("uniq(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ")", Long.class).as("affected_patients"))
                  .from(d)
                  .join(si).on(DSL.condition(
                          "d." + DEVIATIONS.STEP_INSTANCE_ID.getName() + " = si.id"))
                  .join(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .where(DSL.condition(
                          "toUUIDOrNull(?) IS NULL OR pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() +
                          " = toUUIDOrNull(?)", pid, pid))
                  .and(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(
                          DSL.field("si." + STEP_INSTANCES.ACTION_ID.getName()),
                          DSL.field("pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName()),
                          DSL.field("pi." + PROTOCOL_INSTANCES.PROTOCOL_CANONICAL.getName()))
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
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .fetch()
                  .map(r -> new Object[]{r.value1(), r.value2(), r.value3(), r.value4()});
    }

    @Override
    public List<Object[]> countByTypeSince(OffsetDateTime since) {
        var d = finalAs(DEVIATIONS, "d");
        return dsl.select(
                    DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()),
                    DSL.field("count()", Long.class))
                  .from(d)
                  .where(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dt(since)))
                  .groupBy(DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countByTypeInRange(OffsetDateTime startDate, OffsetDateTime endDate,
                                             String facilityId) {
        String fid = str(facilityId);
        var d  = finalAs(DEVIATIONS, "d");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = DSL.table("mv_patient_facility_latest").as("pf");

        return dsl.select(
                    DSL.field("d." + DEVIATIONS.DEVIATION_TYPE.getName()),
                    DSL.field("count()", Long.class))
                  .from(d)
                  .join(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .leftJoin(pf).on(DSL.condition(
                          "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .where(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " <= parseDateTime64BestEffort(?)",
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
                  .join(pi).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .leftJoin(pf).on(DSL.condition(
                          "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .where(DSL.condition("? = '' OR pf.facility_id = ?", fid, fid))
                  .and(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "d." + DEVIATIONS.DETECTED_AT.getName() + " <= parseDateTime64BestEffort(?)",
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
}
