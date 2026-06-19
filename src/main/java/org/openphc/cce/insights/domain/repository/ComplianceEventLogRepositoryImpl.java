package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.openphc.cce.insights.domain.entity.ComplianceEventLog;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.openphc.cce.insights.jooq.Tables.COMPLIANCE_EVENT_LOGS;
import static org.openphc.cce.insights.jooq.Tables.INBOUND_EVENT_LOGS;
import static org.openphc.cce.insights.jooq.Tables.PROTOCOL_INSTANCES;

@Repository
public class ComplianceEventLogRepositoryImpl
        extends AbstractClickHouseRepository<ComplianceEventLog, UUID>
        implements ComplianceEventLogRepository {

    public ComplianceEventLogRepositoryImpl(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected String getTableName() {
        return COMPLIANCE_EVENT_LOGS.getName();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Result mappers
    // ══════════════════════════════════════════════════════════════════════════════

    /** Maps a base compliance_event_logs Record (no joined iel columns). */
    @Override
    protected ComplianceEventLog fromRecord(Record r) {
        return ComplianceEventLog.builder()
                .id(r.get(COMPLIANCE_EVENT_LOGS.ID.getName(), UUID.class))
                .cloudeventsId(r.get(COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName(), String.class))
                .source(r.get(COMPLIANCE_EVENT_LOGS.SOURCE.getName(), String.class))
                .data(r.get(COMPLIANCE_EVENT_LOGS.DATA.getName(), String.class))
                .processingStatus(r.get(COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName(), String.class))
                .receivedAt(recordDateTime(r, COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName()))
                .subject(null)
                .type(null)
                .eventTime(null)
                .facilityId(null)
                .build();
    }

    /** Maps a Record from queries that JOIN inbound_event_logs (full column set). */
    private ComplianceEventLog toComplianceEventLogJoined(Record r) {
        return ComplianceEventLog.builder()
                .id(r.get("id", UUID.class))
                .cloudeventsId(r.get("cloudevents_id", String.class))
                .subject(r.get("subject", String.class))
                .type(r.get("event_type", String.class))
                .eventTime(recordDateTime(r, "event_time"))
                .receivedAt(recordDateTime(r, "received_at"))
                .source(r.get("source", String.class))
                .data(r.get("data", String.class))
                .processingStatus(r.get("processing_status", String.class))
                .facilityId(r.get("facility_id", String.class))
                .protocolInstanceId(parseUUID(r.get("protocol_instance_id", String.class)))
                .protocolDefinitionId(parseUUID(r.get("protocol_definition_id", String.class)))
                .actionId(r.get("action_id", String.class))
                .matchedStepInstanceId(parseUUID(r.get("matched_step_instance_id", String.class)))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Repository methods — full jOOQ DSL
    // ══════════════════════════════════════════════════════════════════════════════

    @Override
    public List<ComplianceEventLog> findBySubjectOrderByEventTimeDesc(String subject) {
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.ID.getName()).as("id"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName()).as("cloudevents_id"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()).as("subject"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.EVENT_TYPE.getName()).as("event_type"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName()).as("event_time"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName()).as("received_at"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()).as("source"),
                    DSL.field("JSONExtractRaw(iel." + INBOUND_EVENT_LOGS.RAW_PAYLOAD.getName() + ", 'data')").as("data"),
                    DSL.field("COALESCE(cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName() +
                              ", iel." + INBOUND_EVENT_LOGS.STATUS.getName() + ")").as("processing_status"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).as("facility_id"),
                    DSL.val("").as("protocol_instance_id"),
                    DSL.val("").as("protocol_definition_id"),
                    DSL.val("").as("action_id"),
                    DSL.val("").as("matched_step_instance_id"))
                  .from(iel)
                  .leftJoin(cel).on(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()).eq(subject))
                  .orderBy(DSL.field("iel." + INBOUND_EVENT_LOGS.EVENT_TIME.getName()).desc())
                  .fetch()
                  .map(this::toComplianceEventLogJoined);
    }

    @Override
    public List<ComplianceEventLog> findByComplianceEventIds(List<UUID> complianceEventIds) {
        if (complianceEventIds == null || complianceEventIds.isEmpty()) return List.of();
        List<String> ids = complianceEventIds.stream().map(UUID::toString).toList();
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        return dsl.select(
                    DSL.field("cel." + COMPLIANCE_EVENT_LOGS.ID.getName()).as("id"),
                    DSL.field("cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()).as("cloudevents_id"),
                    DSL.val("").as("subject"),
                    DSL.val("").as("event_type"),
                    DSL.field("cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName()).as("event_time"),
                    DSL.field("cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName()).as("received_at"),
                    DSL.field("cel." + COMPLIANCE_EVENT_LOGS.SOURCE.getName()).as("source"),
                    DSL.field("cel." + COMPLIANCE_EVENT_LOGS.DATA.getName()).as("data"),
                    DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()).as("processing_status"),
                    DSL.val("").as("facility_id"),
                    DSL.val("").as("protocol_instance_id"),
                    DSL.val("").as("protocol_definition_id"),
                    DSL.val("").as("action_id"),
                    DSL.val("").as("matched_step_instance_id"))
                  .from(cel)
                  .where(DSL.field("cel." + COMPLIANCE_EVENT_LOGS.ID.getName()).in(ids))
                  .fetch()
                  .map(this::toComplianceEventLogJoined);
    }

    @Override
    public List<String> findDistinctFacilityIds() {
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.selectDistinct(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()))
                  .from(iel)
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).ne(""))
                  .orderBy(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()))
                  .fetch(0, String.class);
    }

    @Override
    @Cacheable(value = "analytics", key = "'facility-names'")
    public List<Object[]> findFacilityNames() {
        // Single-pass scan: one read of inbound_event_logs with anyIf() per resource type.
        // Replaces the previous UNION ALL which scanned the table twice.
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        String fid  = "iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName();
        String rt   = "iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName();
        String raw  = "iel." + INBOUND_EVENT_LOGS.RAW_PAYLOAD.getName();

        String encounterName   = "JSONExtractString(JSONExtractRaw(arrayElement(JSONExtractArrayRaw(JSONExtractRaw(" + raw + ", 'data'), 'location'), 1), 'location'), 'display')";
        String serviceReqName  = "JSONExtractString(arrayElement(JSONExtractArrayRaw(JSONExtractRaw(" + raw + ", 'data'), 'locationReference'), 1), 'display')";

        return dsl.select(
                    DSL.field(fid),
                    DSL.field("COALESCE(" +
                            "NULLIF(anyIf(" + encounterName  + ", " + rt + " = 'Encounter'), ''), " +
                            "NULLIF(anyIf(" + serviceReqName + ", " + rt + " = 'ServiceRequest'), ''), " +
                            fid + ")").as("facility_name"))
                  .from(iel)
                  .where(DSL.field(rt).in("Encounter", "ServiceRequest"))
                  .and(DSL.field(fid).ne(""))
                  .groupBy(DSL.field(fid))
                  .orderBy(DSL.field(fid))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, String.class)});
    }

    @Override
    public List<String> findDistinctPractitioners() {
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.selectDistinct(DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()))
                  .from(iel)
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()).ne(""))
                  .orderBy(DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()))
                  .fetch(0, String.class);
    }

    @Override
    public List<Object[]> countByResourceType(String facilityId, String source,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        String src = str(source);
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()).ne("DUPLICATE"))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()).ne(""))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.SOURCE.getName() + " = ?", src, src))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()))
                  .orderBy(DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countByFacility(OffsetDateTime startDate, OffsetDateTime endDate) {
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()).ne("DUPLICATE"))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).ne(""))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()),
                          DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()))
                  .orderBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()),
                          DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, String.class), r.get(2, Long.class)});
    }

    @Override
    public List<Object[]> countByPractitioner(String facilityId,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_DISPLAY.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()).ne("DUPLICATE"))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()).ne(""))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()),
                          DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_DISPLAY.getName()),
                          DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()))
                  .orderBy(DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class), r.get(1, String.class),
                          r.get(2, String.class), r.get(3, Long.class)});
    }

    @Override
    public List<Object[]> countBySource(String facilityId,
                                         OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()).ne("DUPLICATE"))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()).ne(""))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                          DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()))
                  .orderBy(DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, String.class), r.get(2, Long.class)});
    }

    @Override
    public List<Object[]> findEventTrends(String interval, String facilityId, String source,
                                           String resourceType,
                                           OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        String src = str(source);
        String rt  = str(resourceType);
        String periodExpr = dateTruncExpr(interval, "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName());
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field(DSL.sql(periodExpr)).as("period"),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()),
                    DSL.field("count()", Long.class).as("event_count"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()).ne("DUPLICATE"))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.SOURCE.getName() + " = ?", src, src))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName() + " = ?", rt, rt))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(
                          DSL.field(DSL.sql("period")),
                          DSL.field("iel." + INBOUND_EVENT_LOGS.RESOURCE_TYPE.getName()))
                  .orderBy(DSL.field(DSL.sql("period")))
                  .fetch()
                  .map(r -> new Object[]{r.value1(), r.get(1, String.class), r.value3()});
    }

    @Override
    public List<Object[]> countByProcessingStatus(String facilityId,
                                                    OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()),
                    DSL.field("count()", Long.class))
                  .from(cel)
                  .leftJoin(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> findProcessingQualityBySource(String source, String facilityId,
                                                          OffsetDateTime startDate,
                                                          OffsetDateTime endDate) {
        String src = str(source);
        String fid = str(facilityId);
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                    DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()).ne(""))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.SOURCE.getName() + " = ?", src, src))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "cel." + COMPLIANCE_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()),
                          DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()))
                  .orderBy(DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, String.class), r.get(2, Long.class)});
    }

    @Override
    public List<Object[]> findFacilityEventCounts(UUID protocolDefId) {
        String pid = protocolDefId != null ? protocolDefId.toString() : "";
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        var pi  = finalAs(PROTOCOL_INSTANCES, "pi");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()),
                    DSL.field("uniqExact(pi.id)", Long.class).as("total_enrollments"),
                    DSL.field("count()", Long.class).as("total_events"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .leftJoin(pi).on(DSL.condition(
                          "pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() +
                          " = iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()))
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).ne(""))
                  .and(DSL.condition(
                          "toUUIDOrNull(?) IS NULL OR pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() +
                          " = toUUIDOrNull(?)", pid, pid))
                  .groupBy(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()))
                  .orderBy(DSL.field("total_events").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class), r.get(2, Long.class)});
    }

    @Override
    public List<Object[]> findActivePatientsByFacility(UUID protocolDefId) {
        String pid = protocolDefId != null ? protocolDefId.toString() : "";
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        var pi  = finalAs(PROTOCOL_INSTANCES, "pi");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()),
                    DSL.field("uniq(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ")", Long.class).as("patient_count"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .join(pi).on(DSL.condition(
                          "pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() +
                          " = iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()))
                  .where(DSL.field("pi." + PROTOCOL_INSTANCES.STATUS.getName()).eq("ACTIVE"))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).ne(""))
                  .and(DSL.condition(
                          "toUUIDOrNull(?) IS NULL OR pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() +
                          " = toUUIDOrNull(?)", pid, pid))
                  .groupBy(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()))
                  .orderBy(DSL.field("patient_count").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> findFacilityPatientMapping() {
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        var pi  = finalAs(PROTOCOL_INSTANCES, "pi");
        return dsl.selectDistinct(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()),
                    DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .join(pi).on(DSL.condition(
                          "pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() +
                          " = iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()))
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).ne(""))
                  .orderBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()),
                          DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, String.class)});
    }

    @Override
    public List<Object[]> findPatientsByFacility(String facilityId) {
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        var pi  = finalAs(PROTOCOL_INSTANCES, "pi");
        return dsl.selectDistinct(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()),
                    DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()),
                    DSL.field("pi.id").as("protocol_instance_id"))
                  .from(iel)
                  .join(pi).on(DSL.condition(
                          "pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() +
                          " = iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()))
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).eq(facilityId))
                  .and(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).ne(""))
                  .orderBy(DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class), r.get(1, String.class),
                          parseUUID(r.get(2, String.class))});
    }

    @Override
    public List<Object[]> findPractitionerSummary() {
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_DISPLAY.getName()),
                    DSL.field("any(iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + ")").as("facility_id"),
                    DSL.field("count()", Long.class).as("event_count"),
                    DSL.field("uniq(iel." + INBOUND_EVENT_LOGS.SUBJECT.getName() + ")", Long.class).as("patient_count"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()).ne(""))
                  .and(DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()).ne("DUPLICATE"))
                  .groupBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()),
                          DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_DISPLAY.getName()))
                  .orderBy(DSL.field("event_count").desc())
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class), r.get(1, String.class), r.get(2, String.class),
                          r.get(3, Long.class), r.get(4, Long.class)});
    }

    @Override
    public List<Object[]> findPractitionerSummaryFiltered(OffsetDateTime startDate,
                                                            OffsetDateTime endDate,
                                                            String facilityId) {
        String fid = str(facilityId);
        var cel = finalAs(COMPLIANCE_EVENT_LOGS, "cel");
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_DISPLAY.getName()),
                    DSL.field("any(iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + ")").as("facility_id"),
                    DSL.field("count()", Long.class).as("event_count"),
                    DSL.field("uniq(iel." + INBOUND_EVENT_LOGS.SUBJECT.getName() + ")", Long.class).as("patient_count"))
                  .from(cel)
                  .join(iel).on(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.CLOUDEVENTS_ID.getName() +
                          " = cel." + COMPLIANCE_EVENT_LOGS.CLOUDEVENTS_ID.getName()))
                  .where(DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()).ne(""))
                  .and(DSL.field("cel." + COMPLIANCE_EVENT_LOGS.PROCESSING_STATUS.getName()).ne("DUPLICATE"))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .and(DSL.condition("? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?", fid, fid))
                  .groupBy(
                          DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()),
                          DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_DISPLAY.getName()))
                  .orderBy(DSL.field("event_count").desc())
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class), r.get(1, String.class), r.get(2, String.class),
                          r.get(3, Long.class), r.get(4, Long.class)});
    }
}
