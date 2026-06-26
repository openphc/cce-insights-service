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
                            "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                            dtStart(startDate)))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                            dtEnd(endDate)))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countDistinctActiveFacilities(OffsetDateTime startDate, OffsetDateTime endDate) {
        var iel = finalAs(INBOUND_EVENT_LOGS, "iel");
        Long r = dsl.select(DSL.field("uniq(iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + ")", Long.class))
                    .from(iel)
                    .where(DSL.field("iel." + INBOUND_EVENT_LOGS.STATUS.getName()).eq("ACCEPTED"))
                    .and(DSL.field("iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName()).ne(""))
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
                            "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                            dtStart(startDate)))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
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
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
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
                            "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                            dtStart(startDate)))
                    .and(DSL.condition(
                            "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                            dtEnd(endDate)))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public boolean facilityTransmittedInRange(String facilityId,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        if (facilityId == null || facilityId.isEmpty()) return false;
        return countAccepted(facilityId, startDate, endDate) > 0;
    }

    @Override
    public List<Object[]> findEventTrends(String interval, String facilityId, String source,
                                           OffsetDateTime startDate, OffsetDateTime endDate) {
        String fid = str(facilityId);
        String src = str(source);
        String periodExpr = dateTruncExpr(interval, "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName());
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
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field("iel." + INBOUND_EVENT_LOGS.SOURCE.getName()))
                  .orderBy(DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }
}
