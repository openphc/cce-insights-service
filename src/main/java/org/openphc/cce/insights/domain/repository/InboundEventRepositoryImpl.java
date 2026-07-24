package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.openphc.cce.insights.domain.entity.InboundEvent;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.openphc.cce.insights.jooq.Tables.COMPLIANCE_EVENT_LOGS;
import static org.openphc.cce.insights.jooq.Tables.INBOUND_EVENT_LOGS;

@Repository
public class InboundEventRepositoryImpl
        extends AbstractClickHouseRepository<InboundEvent, UUID>
        implements InboundEventRepository {

    public InboundEventRepositoryImpl(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected String getTableName() {
        return INBOUND_EVENT_LOGS.getName();
    }

    @Override
    protected InboundEvent fromRecord(Record r) {
        return toInboundEvent(r);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Result mappers
    // ══════════════════════════════════════════════════════════════════════════════

    private InboundEvent toInboundEvent(Record r) {
        return InboundEvent.builder()
                .id(r.get(INBOUND_EVENT_LOGS.ID.getName(), UUID.class))
                .cloudeventsId(r.get(INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName(), String.class))
                .source(r.get(INBOUND_EVENT_LOGS.SOURCE.getName(), String.class))
                .type(r.get(INBOUND_EVENT_LOGS.EVENT_TYPE.getName(), String.class))
                .subject(r.get(INBOUND_EVENT_LOGS.SUBJECT.getName(), String.class))
                .eventTime(recordDateTime(r, INBOUND_EVENT_LOGS.EVENT_TIME.getName()))
                .facilityId(r.get(INBOUND_EVENT_LOGS.FACILITY_ID.getName(), String.class))
                .correlationId(r.get(INBOUND_EVENT_LOGS.CORRELATION_ID.getName(), String.class))
                .rawPayload(r.get(INBOUND_EVENT_LOGS.RAW_PAYLOAD.getName(), String.class))
                .status(r.get(INBOUND_EVENT_LOGS.STATUS.getName(), String.class))
                .rejectionReason(r.get(INBOUND_EVENT_LOGS.REJECTION_REASON.getName(), String.class))
                .errorDetails(r.get(INBOUND_EVENT_LOGS.ERROR_DETAILS.getName(), String.class))
                .receivedAt(recordDateTime(r, INBOUND_EVENT_LOGS.RECEIVED_AT.getName()))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Repository methods — full jOOQ DSL
    // ══════════════════════════════════════════════════════════════════════════════

    @Override
    public List<String> findDistinctSources() {
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.selectDistinct(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()))
                  .from(iel)
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()).ne(""))
                  .orderBy(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()))
                  .fetch(0, String.class);
    }

    @Override
    public long countDistinctPatientSubjectsBySource(String source, String facilityId,
                                                      OffsetDateTime startDate,
                                                      OffsetDateTime endDate) {
        String src = str(source);
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        Long r = dsl.select(DSL.field("uniq(iel." + INBOUND_EVENT_LOGS.SUBJECT.getName() + ")", Long.class))
                    .from(iel)
                    .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("ACCEPTED"))
                    .and(DSL.field("iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()).ne(""))
                    .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.SOURCE.getName() + " = ?", src, src))
                    .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " >= parseDateTime64BestEffort(?)",
                            dtStart(startDate)))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " <= parseDateTime64BestEffort(?)",
                            dtEnd(endDate)))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    // ── RI-36 tracked cohort ──────────────────────────────────────────────────────
    // Distinct patients whose events are "considered by a protocol" in the range: an ACCEPTED inbound
    // event whose event_time is in [start,end] AND whose cloudevents_id matched a protocol
    // (compliance_event_logs.processing_status = 'MATCHED' — whether it created a new protocol_instance
    // or advanced an existing journey's step). Scoped by event_time (NOT enrolled_at) + facility.
    // Excludes events not considered by any protocol (Consent onboarding, zero-match). Optional
    // facilityId ('' = all) and optional dates (null = unbounded).
    @Override
    public long countDistinctPatientsWithMatchedEvents(String facilityId, String district,
                                                       OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        Long r = dsl.select(DSL.field("uniq(iel." + INBOUND_EVENT_LOGS.SUBJECT.getName() + ")", Long.class))
                    .from(iel)
                    .where(matchedCohortCondition(fid, startDate, endDate))
                    .and(districtScope("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName(), district))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    /** "district blank OR facility_id in the district's facilities" — resolved via the facility ref. */
    private org.jooq.Condition districtScope(String facilityColumn, String district) {
        String d = str(district);
        return DSL.condition("? = '' OR " + facilityColumn
                + " IN (SELECT facility_id FROM facility" + finalClause()
                + " WHERE _is_deleted = 0 AND lower(district_name) = lower(?))", d, d);
    }

    /** WHERE clause (alias iel) selecting the "considered-by-a-protocol" inbound-event cohort. */
    private org.jooq.Condition matchedCohortCondition(String fid,
                                                      OffsetDateTime startDate, OffsetDateTime endDate) {
        return DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("ACCEPTED")
                .and(DSL.field("iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()).ne(""))
                .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                .and(DSL.condition("iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName()
                        + " IN (SELECT cel.cloudevents_id FROM compliance_event_logs cel" + finalClause()
                        + " WHERE cel.processing_status = 'MATCHED')"))
                .and(DSL.condition("? != '1' OR iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName()
                        + " >= parseDateTime64BestEffort(?)", startDate == null ? "0" : "1", dtStart(startDate)))
                .and(DSL.condition("? != '1' OR iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName()
                        + " <= parseDateTime64BestEffort(?)", endDate == null ? "0" : "1", dtEnd(endDate)));
    }

    @Override
    public long countDistinctActiveFacilities(OffsetDateTime startDate, OffsetDateTime endDate) {
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        Long r = dsl.select(DSL.field("uniq(iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + ")", Long.class))
                    .from(iel)
                    .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("ACCEPTED"))
                    .and(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).ne(""))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " >= parseDateTime64BestEffort(?)",
                            dtStart(startDate)))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " <= parseDateTime64BestEffort(?)",
                            dtEnd(endDate)))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countEventsBySource(String source, String facilityId,
                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        String src = str(source);
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        Long r = dsl.select(DSL.field("count()", Long.class))
                    .from(iel)
                    .where(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.SOURCE.getName() + " = ?", src, src))
                    .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " >= parseDateTime64BestEffort(?)",
                            dtStart(startDate)))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " <= parseDateTime64BestEffort(?)",
                            dtEnd(endDate)))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public List<Object[]> countDistinctPatientsBySourceGroupedByFacility(String source,
                                                                          OffsetDateTime startDate,
                                                                          OffsetDateTime endDate) {
        String src = str(source);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()),
                    DSL.field("uniq(iel." + INBOUND_EVENT_LOGS.SUBJECT.getName() + ")", Long.class).as("patient_count"))
                  .from(iel)
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("ACCEPTED"))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).ne(""))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()).ne(""))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.SOURCE.getName() + " = ?", src, src))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()))
                  .orderBy(DSL.field("patient_count").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countByStatus(String facilityId, String source,
                                         OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        String src = str(source);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()),
                    DSL.field("count()", Long.class))
                  .from(iel)
                  .where(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.SOURCE.getName() + " = ?", src, src))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countByRejectionReason(String facilityId, String source,
                                                   OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        String src = str(source);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.REJECTION_REASON.getName()),
                    DSL.field("count()", Long.class))
                  .from(iel)
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("REJECTED"))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.REJECTION_REASON.getName()).ne(""))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.SOURCE.getName() + " = ?", src, src))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field("iel." + INBOUND_EVENT_LOGS.REJECTION_REASON.getName()))
                  .orderBy(DSL.field("count()", Long.class).desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> findIngestionTrends(String interval, String facilityId, String source,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        String src = str(source);
        // Ingestion Pipeline is a SYSTEM/technical view (throughput our system received), so it
        // filters and buckets on received_at, NOT event_time. Only the clinical Events page trend
        // (findEventTrends) uses event_time.
        String periodExpr = dateTruncExpr(interval, "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName());
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field(DSL.sql(periodExpr)).as("period"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(iel)
                  .where(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.SOURCE.getName() + " = ?", src, src))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field(DSL.sql("period")), DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()))
                  .orderBy(DSL.field(DSL.sql("period")))
                  .fetch()
                  .map(r -> new Object[]{r.value1(), r.get(1, String.class), r.value3()});
    }

    @Override
    public List<Object[]> countBySourceAndStatus(String facilityId,
                                                   OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(iel)
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()).ne(""))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                          DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()))
                  .orderBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                          DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, String.class), r.get(2, Long.class)});
    }

    @Override
    public List<Object[]> countBySourceAndRejectionReason(String facilityId,
                                                           OffsetDateTime startDate,
                                                           OffsetDateTime endDate) {
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.REJECTION_REASON.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(iel)
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("REJECTED"))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()).ne(""))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.REJECTION_REASON.getName()).ne(""))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                          DSL.field("iel." + INBOUND_EVENT_LOGS.REJECTION_REASON.getName()))
                  .orderBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                          DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, String.class), r.get(2, Long.class)});
    }

    @Override
    public List<Object[]> findPipelineLossBySource(String facilityId,
                                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        // Subquery: accepted events whose cloudevents_id is not in compliance_event_logs
        var celSubquery = dsl.select(DSL.field(COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                             .from(DSL.table(DSL.sql(COMPLIANCE_EVENT_LOGS.getName() + finalClause())));
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                    DSL.field("count()", Long.class).as("lost_count"))
                  .from(iel)
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("ACCEPTED"))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName()).notIn(celSubquery))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()))
                  .orderBy(DSL.field("lost_count").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public long countPipelineLoss(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        var celSubquery = dsl.select(DSL.field(COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                             .from(DSL.table(DSL.sql(COMPLIANCE_EVENT_LOGS.getName() + finalClause())));
        Long r = dsl.select(DSL.field("count()", Long.class))
                    .from(iel)
                    .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("ACCEPTED"))
                    .and(DSL.field("iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName()).notIn(celSubquery))
                    .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                            dtStart(startDate)))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                            dtEnd(endDate)))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countAccepted(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        Long r = dsl.select(DSL.field("count()", Long.class))
                    .from(iel)
                    .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("ACCEPTED"))
                    .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " >= parseDateTime64BestEffort(?)",
                            dtStart(startDate)))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " <= parseDateTime64BestEffort(?)",
                            dtEnd(endDate)))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public boolean facilityTransmittedInRange(String facilityId,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        if (facilityId == null || facilityId.isEmpty()) return false;
        // MV-first: read the event_time-keyed event-volume MV (clinical activity presence).
        Long n = dsl.select(DSL.field("sum(event_count)", Long.class))
                    .from(DSL.table("mv_event_volume_hourly"))
                    .where(DSL.condition("facility_id = ?", str(facilityId)))
                    .and(DSL.condition("toDate(hour) >= toDate(parseDateTime64BestEffort(?))", dtStart(startDate)))
                    .and(DSL.condition("toDate(hour) <= toDate(parseDateTime64BestEffort(?))", dtEnd(endDate)))
                    .fetchOne(0, Long.class);
        return n != null && n > 0;
    }

    @Override
    public List<Object[]> eventCountByFacilityFromMv(OffsetDateTime startDate, OffsetDateTime endDate) {
        // Facility ranking "Events (period)" — one grouped read of the event_time event-volume MV
        // (replaces N per-facility countAccepted() calls). Equivalent: MV holds ACCEPTED events by event_time.
        return dsl.select(
                    DSL.field("facility_id"),
                    DSL.field("sum(event_count)", Long.class))
                  .from(DSL.table("mv_event_volume_hourly"))
                  .where(DSL.condition("facility_id != ''"))
                  .and(DSL.condition("toDate(hour) >= toDate(parseDateTime64BestEffort(?))", dtStart(startDate)))
                  .and(DSL.condition("toDate(hour) <= toDate(parseDateTime64BestEffort(?))", dtEnd(endDate)))
                  .groupBy(DSL.field("facility_id"))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    // ── Events page: clinical event volume from mv_event_volume_hourly (event_time, ACCEPTED) ──

    @Override
    public List<Object[]> eventVolumeByResourceType(String facilityId, String source, String district,
                                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId); String src = str(source);
        return dsl.select(DSL.field("resource_type"), DSL.field("sum(event_count)", Long.class))
                  .from(DSL.table("mv_event_volume_hourly"))
                  .where(DSL.condition("resource_type != ''"))
                  .and(DSL.condition("? = '' OR facility_id = ?", fid, fid))
                  .and(districtScope("facility_id", district))
                  .and(DSL.condition("? = '' OR source = ?", src, src))
                  .and(DSL.condition("toDate(hour) >= toDate(parseDateTime64BestEffort(?))", dtStart(startDate)))
                  .and(DSL.condition("toDate(hour) <= toDate(parseDateTime64BestEffort(?))", dtEnd(endDate)))
                  .groupBy(DSL.field("resource_type"))
                  .orderBy(DSL.field("sum(event_count)", Long.class).desc())
                  .fetch().map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> eventVolumeByFacilityAndType(String facilityId, String source, String resourceType, String district,
                                                        OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId); String src = str(source); String rt = str(resourceType);
        // No facility_id != '' filter: facility-less events (RelatedPerson/Patient/etc.) come through
        // with facility_id='' so the caller can bucket them as "Unassigned" and reconcile with Total.
        return dsl.select(DSL.field("facility_id"), DSL.field("resource_type"), DSL.field("sum(event_count)", Long.class))
                  .from(DSL.table("mv_event_volume_hourly"))
                  .where(DSL.condition("? = '' OR facility_id = ?", fid, fid))
                  .and(districtScope("facility_id", district))
                  .and(DSL.condition("? = '' OR source = ?", src, src))
                  .and(DSL.condition("? = '' OR resource_type = ?", rt, rt))
                  .and(DSL.condition("toDate(hour) >= toDate(parseDateTime64BestEffort(?))", dtStart(startDate)))
                  .and(DSL.condition("toDate(hour) <= toDate(parseDateTime64BestEffort(?))", dtEnd(endDate)))
                  .groupBy(DSL.field("facility_id"), DSL.field("resource_type"))
                  .fetch().map(r -> new Object[]{r.get(0, String.class), r.get(1, String.class), r.get(2, Long.class)});
    }

    @Override
    public List<Object[]> eventVolumeBySource(String facilityId, String district, OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        return dsl.select(DSL.field("source"), DSL.field("sum(event_count)", Long.class))
                  .from(DSL.table("mv_event_volume_hourly"))
                  .where(DSL.condition("source != ''"))
                  .and(DSL.condition("? = '' OR facility_id = ?", fid, fid))
                  .and(districtScope("facility_id", district))
                  .and(DSL.condition("toDate(hour) >= toDate(parseDateTime64BestEffort(?))", dtStart(startDate)))
                  .and(DSL.condition("toDate(hour) <= toDate(parseDateTime64BestEffort(?))", dtEnd(endDate)))
                  .groupBy(DSL.field("source"))
                  .fetch().map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> eventVolumeTrends(String interval, String facilityId, String source, String district,
                                            OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId); String src = str(source);
        String periodExpr = dateTruncExpr(interval, "hour");
        return dsl.select(DSL.field(DSL.sql(periodExpr)).as("period"), DSL.field("resource_type"),
                          DSL.field("sum(event_count)", Long.class).as("cnt"))
                  .from(DSL.table("mv_event_volume_hourly"))
                  .where(DSL.condition("? = '' OR facility_id = ?", fid, fid))
                  .and(districtScope("facility_id", district))
                  .and(DSL.condition("? = '' OR source = ?", src, src))
                  .and(DSL.condition("toDate(hour) >= toDate(parseDateTime64BestEffort(?))", dtStart(startDate)))
                  .and(DSL.condition("toDate(hour) <= toDate(parseDateTime64BestEffort(?))", dtEnd(endDate)))
                  .groupBy(DSL.field(DSL.sql("period")), DSL.field("resource_type"))
                  .orderBy(DSL.field(DSL.sql("period")))
                  .fetch().map(r -> new Object[]{r.value1(), r.get(1, String.class), r.value3()});
    }

    // Zero-match code/category are read ad-hoc from raw_payload (not materialized): the JSON path
    // depends on the FHIR resourceType (Observation/ServiceRequest/Condition use `code`,
    // MedicationRequest uses `medicationCodeableConcept`), so a single materialized column can't
    // cover every shape. Mirrors the nested-if extraction style already used for facility_id
    // (schema/01-create-tables.sql) — arrayElement()/JSONExtractString() on a missing path return
    // '' rather than throwing, so no extra null-guards are needed.
    private static String zeroMatchCodeExpr() {
        String raw = "iel." + INBOUND_EVENT_LOGS.RAW_PAYLOAD.getName();
        String codeCoding = "arrayElement(JSONExtractArrayRaw(" + raw + ", 'data', 'code', 'coding'), 1)";
        String medCoding = "arrayElement(JSONExtractArrayRaw(" + raw + ", 'data', 'medicationCodeableConcept', 'coding'), 1)";
        return "if(JSONExtractString(" + raw + ", 'data', 'code', 'text') != '', JSONExtractString(" + raw + ", 'data', 'code', 'text'), "
             + "if(JSONExtractString(" + codeCoding + ", 'display') != '', JSONExtractString(" + codeCoding + ", 'display'), "
             + "if(JSONExtractString(" + codeCoding + ", 'code') != '', JSONExtractString(" + codeCoding + ", 'code'), "
             + "if(JSONExtractString(" + raw + ", 'data', 'medicationCodeableConcept', 'text') != '', JSONExtractString(" + raw + ", 'data', 'medicationCodeableConcept', 'text'), "
             + "JSONExtractString(" + medCoding + ", 'display')))))";
    }

    private static String zeroMatchCategoryExpr() {
        String raw = "iel." + INBOUND_EVENT_LOGS.RAW_PAYLOAD.getName();
        String categoryFirst = "arrayElement(JSONExtractArrayRaw(" + raw + ", 'data', 'category'), 1)";
        String categoryCoding = "arrayElement(JSONExtractArrayRaw(" + categoryFirst + ", 'coding'), 1)";
        return "if(JSONExtractString(" + categoryFirst + ", 'text') != '', JSONExtractString(" + categoryFirst + ", 'text'), "
             + "JSONExtractString(" + categoryCoding + ", 'display'))";
    }

    @Override
    public List<Object[]> findZeroMatchEvents(String facilityId, String district,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()).as("resource_type"),
                    DSL.field(DSL.sql(zeroMatchCodeExpr())).as("code"),
                    DSL.field(DSL.sql(zeroMatchCategoryExpr())).as("category"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).as("facility_id"),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()).eq("ZERO_MATCH"))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(districtScope("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName(), district))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(
                          DSL.field(DSL.sql("resource_type")),
                          DSL.field(DSL.sql("code")),
                          DSL.field(DSL.sql("category")),
                          DSL.field(DSL.sql("facility_id")))
                  .orderBy(DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class),
                          r.get(1, String.class),
                          r.get(2, String.class),
                          r.get(3, String.class),
                          r.get(4, Long.class)
                  });
    }

    @Override
    public OffsetDateTime findLastReceivedAt() {
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        var r = dsl.select(DSL.field("max(iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + ")").as("last_received"))
                    .from(iel)
                    .fetchOne();
        return r != null ? recordDateTime(r, "last_received") : null;
    }

    @Override
    public Object[] eventProcessingKpis(String facilityId, String district, OffsetDateTime startDate, OffsetDateTime endDate) {
        // Events page cards from mv_daily_event_kpis (event_time day × facility). total/matched/
        // zeromatch/duplicate/pipeline_loss all over the SAME inbound event set → rates reconcile.
        String fid = str(facilityId);
        var iel = DSL.table(DSL.sql("mv_daily_event_kpis" + finalClause()));
        var r = dsl.select(
                    DSL.field("sum(total_events)", Long.class),
                    DSL.field("sum(matched_count)", Long.class),
                    DSL.field("sum(zero_match_count)", Long.class),
                    DSL.field("sum(duplicate_count)", Long.class),
                    DSL.field("sum(pipeline_loss_count)", Long.class))
                  .from(iel)
                  .where(DSL.condition("? = '' OR facility_id = ?", fid, fid))
                  .and(districtScope("facility_id", district))
                  .and(DSL.condition("? != '1' OR snapshot_date >= toDate(parseDateTime64BestEffort(?))",
                          startDate == null ? "0" : "1", dtStart(startDate)))
                  .and(DSL.condition("? != '1' OR snapshot_date <= toDate(parseDateTime64BestEffort(?))",
                          endDate == null ? "0" : "1", dtEnd(endDate)))
                  .fetchOne();
        if (r == null) return new Object[]{0L, 0L, 0L, 0L, 0L};
        return new Object[]{ nz(r.get(0)), nz(r.get(1)), nz(r.get(2)), nz(r.get(3)), nz(r.get(4)) };
    }

    private static long nz(Object v) { return v == null ? 0L : ((Number) v).longValue(); }

    // received_at (system-time) ACCEPTED count — for the Ingestion pipeline-loss denominator only.
    // The Ingestion page is the sole SYSTEM/technical view; countAccepted() stays event_time (clinical).
    @Override
    public long countAcceptedByReceivedAt(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        Long r = dsl.select(DSL.field("count()", Long.class))
                    .from(iel)
                    .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("ACCEPTED"))
                    .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                            dtStart(startDate)))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                            dtEnd(endDate)))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public List<Object[]> findEventTrends(String interval, String facilityId, String source,
                                           OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        String src = str(source);
        // Bucket the trend x-axis by clinical event_time to match the event_time date filter below
        // (was received_at — a row could pass the event_time filter yet plot in a different bucket).
        String periodExpr = dateTruncExpr(interval, "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName());
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field(DSL.sql(periodExpr)).as("period"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()),
                    DSL.field("count()", Long.class).as("event_count"))
                  .from(iel)
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).ne("DUPLICATE"))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()).ne(""))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.SOURCE.getName() + " = ?", src, src))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(
                          DSL.field(DSL.sql("period")),
                          DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()))
                  .orderBy(DSL.field(DSL.sql("period")))
                  .fetch()
                  .map(r -> new Object[]{r.value1(), r.get(1, String.class), r.value3()});
    }

    @Override
    public List<Object[]> countBySource(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(iel)
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()).ne(""))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()))
                  .orderBy(DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Referrals KPI (event_time-keyed) — reads mv_daily_referral_kpis (schema/07).
    //
    // A "referral received by HIE" is an ACCEPTED inbound event counted once per accepted row (RI-35):
    //   (A) PROD: an Encounter with Encounter.type[].coding[].display = 'TRANSFER_ENCOUNTER' —
    //       ingestion-based, independent of compliance matching, so it no longer under-reports
    //       (transfers for not-yet-enrolled patients) or lags behind step matching / CDC; plus
    //   (B) DEV/DEMO fallback: accepted events that completed a Referral step (the demo referral is a
    //       markerless ServiceRequest, identifiable only via the match) — deduped so prod never
    //       double-counts. The MV bakes in both branches. Keyed on event_time day × facility with the
    //       12-month rolling window, so these reads are simple snapshot_date-range sums.
    // ══════════════════════════════════════════════════════════════════════════════

    @Override
    public long countReferralsReceivedByHIE(String facilityId,
                                            OffsetDateTime startDate, OffsetDateTime endDate) {
        return referralTotal("sum(referral_count)", facilityId, startDate, endDate);
    }

    @Override
    public long countReferralsMatched(String facilityId,
                                      OffsetDateTime startDate, OffsetDateTime endDate) {
        return referralTotal("sum(matched_count)", facilityId, startDate, endDate);
    }

    private long referralTotal(String aggregate, String facilityId,
                               OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var mv = DSL.table(DSL.sql("mv_daily_referral_kpis" + finalClause()));
        Long r = dsl.select(DSL.field(aggregate, Long.class))
                    .from(mv)
                    .where(DSL.condition("? = '' OR facility_id = ?", fid, fid))
                    .and(DSL.condition("? != '1' OR snapshot_date >= toDate(parseDateTime64BestEffort(?))",
                            startDate == null ? "0" : "1", dtStart(startDate)))
                    .and(DSL.condition("? != '1' OR snapshot_date <= toDate(parseDateTime64BestEffort(?))",
                            endDate == null ? "0" : "1", dtEnd(endDate)))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public List<Object[]> countReferralsReceivedByHIEGroupedByFacility(OffsetDateTime startDate,
                                                                        OffsetDateTime endDate) {
        var mv = DSL.table(DSL.sql("mv_daily_referral_kpis" + finalClause()));
        return dsl.select(
                    DSL.field("facility_id", String.class),
                    DSL.field("sum(referral_count)", Long.class).as("received"),
                    DSL.field("sum(matched_count)", Long.class).as("matched"))
                  .from(mv)
                  .where(DSL.field("facility_id").ne(""))
                  .and(DSL.condition("? != '1' OR snapshot_date >= toDate(parseDateTime64BestEffort(?))",
                          startDate == null ? "0" : "1", dtStart(startDate)))
                  .and(DSL.condition("? != '1' OR snapshot_date <= toDate(parseDateTime64BestEffort(?))",
                          endDate == null ? "0" : "1", dtEnd(endDate)))
                  .groupBy(DSL.field("facility_id"))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class), r.get(2, Long.class)});
    }

    @Override
    public List<Object[]> referralsReceivedByHIEByPatient(OffsetDateTime startDate, OffsetDateTime endDate) {
        // Referral-step completions keyed by the completing event (branch B). Carries the step's
        // clinical completion date, which is the per-patient date the patient-detail timeline shows —
        // NOT the referral ServiceRequest's event_time (that comes from occurrenceDateTime, a uniform
        // scheduled date). Same referral-step match the pipeline's mv_daily_referral_kpis uses (schema/07).
        String refSteps =
                "(SELECT cel.cloudevents_id AS cid, max(si.completed_at) AS completed_at"
                + " FROM compliance_event_logs cel" + finalClause()
                + " JOIN step_instances si" + finalClause() + " ON si.completed_by_event_id = cel.id"
                + " WHERE match(si.action_id, '^(.+-referral|referral)$')"
                + " GROUP BY cel.cloudevents_id) rs";
        // (A) prod TRANSFER_ENCOUNTER Encounter (ingestion-based, no matched step — dated by event_time).
        String transferEncounter =
                "(iel.resource_type = 'Encounter' AND arrayExists("
                + " t -> arrayExists("
                + "        c -> JSONExtractString(c, 'display') = 'TRANSFER_ENCOUNTER',"
                + "        JSONExtractArrayRaw(t, 'coding')),"
                + " JSONExtractArrayRaw(JSONExtractRaw(iel.raw_payload, 'data'), 'type')))";
        // Referral clinical date: matched referral (rs.cid set) → step completed_at (reconciles with
        // patient detail); transfer encounter → event_time. rs is LEFT-joined, so ClickHouse fills
        // rs.cid='' for non-matches (join-fills-defaults, not nulls). The card/list are BOTH filtered
        // and displayed on THIS date, so the global date range applies to the date the user sees.
        String refDate = "if(rs.cid != '', rs.completed_at, iel.event_time)";

        return dsl.select(
                    DSL.field("iel.subject", String.class),
                    DSL.field("any(iel.facility_id)", String.class),
                    DSL.field("formatDateTime(max(" + refDate + "), '%Y-%m-%dT%H:%i:%SZ')", String.class),
                    DSL.field("count()", Long.class),
                    DSL.field("countIf(rs.cid != '')", Long.class))
                  .from(DSL.table(DSL.sql("inbound_event_logs iel" + finalClause())))
                  .leftJoin(DSL.table(DSL.sql(refSteps)))
                        .on(DSL.condition("rs.cid = iel.cloudevents_id"))
                  .where(DSL.condition("iel.status = 'ACCEPTED'"))
                  .and(DSL.condition("iel.subject != ''"))
                  .and(DSL.condition("(" + transferEncounter + " OR rs.cid != '')"))
                  .and(DSL.condition(refDate + " >= parseDateTime64BestEffort(?)", dtStart(startDate)))
                  .and(DSL.condition(refDate + " <= parseDateTime64BestEffort(?)", dtEnd(endDate)))
                  .groupBy(DSL.field("iel.subject"))
                  .orderBy(DSL.field("max(" + refDate + ")").desc())
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class),
                          r.get(1, String.class),
                          r.get(2, String.class),
                          r.get(3, Long.class),
                          r.get(4, Long.class)
                  });
    }
}
