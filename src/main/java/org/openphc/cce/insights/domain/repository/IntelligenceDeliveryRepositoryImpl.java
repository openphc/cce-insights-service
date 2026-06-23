package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.openphc.cce.insights.domain.entity.IntelligenceDelivery;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.openphc.cce.insights.jooq.Tables.INTELLIGENCE_DELIVERIES;

@Repository
public class IntelligenceDeliveryRepositoryImpl
        extends AbstractClickHouseRepository<IntelligenceDelivery, UUID>
        implements IntelligenceDeliveryRepository {

    public IntelligenceDeliveryRepositoryImpl(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected String getTableName() {
        return INTELLIGENCE_DELIVERIES.getName();
    }

    @Override
    protected IntelligenceDelivery fromRecord(Record r) {
        return IntelligenceDelivery.builder()
                .id(r.get(INTELLIGENCE_DELIVERIES.ID.getName(), UUID.class))
                .intelligenceEventId(parseUUID(r.get(INTELLIGENCE_DELIVERIES.INTELLIGENCE_EVENT_ID.getName(), String.class)))
                .actionDefinitionId(parseUUID(r.get(INTELLIGENCE_DELIVERIES.ACTION_DEFINITION_ID.getName(), String.class)))
                .destinationAdaptorMappingId(parseUUID(r.get(INTELLIGENCE_DELIVERIES.DESTINATION_ADAPTOR_MAPPING_ID.getName(), String.class)))
                .actionType(r.get(INTELLIGENCE_DELIVERIES.ACTION_TYPE.getName(), String.class))
                .status(r.get(INTELLIGENCE_DELIVERIES.STATUS.getName(), String.class))
                .subject(r.get(INTELLIGENCE_DELIVERIES.SUBJECT.getName(), String.class))
                .protocolCanonical(r.get(INTELLIGENCE_DELIVERIES.PROTOCOL_CANONICAL.getName(), String.class))
                .actionId(r.get(INTELLIGENCE_DELIVERIES.ACTION_ID.getName(), String.class))
                .severity(r.get(INTELLIGENCE_DELIVERIES.SEVERITY.getName(), String.class))
                .destination(r.get(INTELLIGENCE_DELIVERIES.DESTINATION.getName(), String.class))
                .attemptCount(r.get(INTELLIGENCE_DELIVERIES.ATTEMPT_COUNT.getName(), Integer.class))
                .createdAt(recordDateTime(r, INTELLIGENCE_DELIVERIES.CREATED_AT.getName()))
                .updatedAt(recordDateTime(r, INTELLIGENCE_DELIVERIES.UPDATED_AT.getName()))
                .deliveredAt(recordDateTime(r, INTELLIGENCE_DELIVERIES.DELIVERED_AT.getName()))
                .build();
    }

    private String table() {
        return INTELLIGENCE_DELIVERIES.getName() + finalClause();
    }

    @Override
    public List<IntelligenceDelivery> findBySubject(String subject) {
        return dsl.selectFrom(DSL.table(DSL.sql(table())))
                  .where(DSL.field(INTELLIGENCE_DELIVERIES.SUBJECT.getName()).eq(subject))
                  .orderBy(DSL.field(INTELLIGENCE_DELIVERIES.CREATED_AT.getName()).desc())
                  .fetch()
                  .map(r -> fromRecord(r));
    }

    @Override
    public List<Object[]> countByStatus() {
        return dsl.select(
                    DSL.field(INTELLIGENCE_DELIVERIES.STATUS.getName()),
                    DSL.field("count()", Long.class))
                  .from(DSL.table(DSL.sql(table())))
                  .groupBy(DSL.field(INTELLIGENCE_DELIVERIES.STATUS.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countByActionType() {
        return dsl.select(
                    DSL.field(INTELLIGENCE_DELIVERIES.ACTION_TYPE.getName()),
                    DSL.field("count()", Long.class))
                  .from(DSL.table(DSL.sql(table())))
                  .groupBy(DSL.field(INTELLIGENCE_DELIVERIES.ACTION_TYPE.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countBySeverity() {
        return dsl.select(
                    DSL.field(INTELLIGENCE_DELIVERIES.SEVERITY.getName()),
                    DSL.field("count()", Long.class))
                  .from(DSL.table(DSL.sql(table())))
                  .groupBy(DSL.field(INTELLIGENCE_DELIVERIES.SEVERITY.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countByDestination() {
        return dsl.select(
                    DSL.field(INTELLIGENCE_DELIVERIES.DESTINATION.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(DSL.table(DSL.sql(table())))
                  .groupBy(DSL.field(INTELLIGENCE_DELIVERIES.DESTINATION.getName()))
                  .orderBy(DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public long countDelivered() {
        Long r = dsl.select(DSL.field("count()", Long.class))
                    .from(DSL.table(DSL.sql(table())))
                    .where(DSL.field(INTELLIGENCE_DELIVERIES.STATUS.getName()).eq("DELIVERED"))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countFailed() {
        Long r = dsl.select(DSL.field("count()", Long.class))
                    .from(DSL.table(DSL.sql(table())))
                    .where(DSL.field(INTELLIGENCE_DELIVERIES.STATUS.getName()).eq("FAILED"))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public Double avgDeliveryLatencySeconds() {
        return dsl.select(DSL.field(
                        "avg(toFloat64(dateDiff('millisecond', " +
                        INTELLIGENCE_DELIVERIES.CREATED_AT.getName() + ", " +
                        INTELLIGENCE_DELIVERIES.DELIVERED_AT.getName() + "))) / 1000.0",
                        Double.class))
                  .from(DSL.table(DSL.sql(table())))
                  .where(DSL.field(INTELLIGENCE_DELIVERIES.STATUS.getName()).eq("DELIVERED"))
                  .fetchOne(0, Double.class);
    }

    @Override
    public long countFiltered(OffsetDateTime startDate, OffsetDateTime endDate, String protocolCanonical) {
        Long r = dsl.select(DSL.field("count()", Long.class))
                    .from(DSL.table(DSL.sql(table())))
                    .where(dateRange(startDate, endDate))
                    .and(protocolCondition(protocolCanonical))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countDeliveredFiltered(OffsetDateTime startDate, OffsetDateTime endDate, String protocolCanonical) {
        Long r = dsl.select(DSL.field("count()", Long.class))
                    .from(DSL.table(DSL.sql(table())))
                    .where(DSL.field(INTELLIGENCE_DELIVERIES.STATUS.getName()).eq("DELIVERED"))
                    .and(dateRange(startDate, endDate))
                    .and(protocolCondition(protocolCanonical))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countFailedFiltered(OffsetDateTime startDate, OffsetDateTime endDate, String protocolCanonical) {
        Long r = dsl.select(DSL.field("count()", Long.class))
                    .from(DSL.table(DSL.sql(table())))
                    .where(DSL.field(INTELLIGENCE_DELIVERIES.STATUS.getName()).eq("FAILED"))
                    .and(dateRange(startDate, endDate))
                    .and(protocolCondition(protocolCanonical))
                    .fetchOne(0, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public Double avgDeliveryLatencySecondsFiltered(OffsetDateTime startDate, OffsetDateTime endDate, String protocolCanonical) {
        return dsl.select(DSL.field(
                        "avg(toFloat64(dateDiff('millisecond', " +
                        INTELLIGENCE_DELIVERIES.CREATED_AT.getName() + ", " +
                        INTELLIGENCE_DELIVERIES.DELIVERED_AT.getName() + "))) / 1000.0",
                        Double.class))
                  .from(DSL.table(DSL.sql(table())))
                  .where(DSL.field(INTELLIGENCE_DELIVERIES.STATUS.getName()).eq("DELIVERED"))
                  .and(dateRange(startDate, endDate))
                  .and(protocolCondition(protocolCanonical))
                  .fetchOne(0, Double.class);
    }

    @Override
    public List<Object[]> countByStatusFiltered(OffsetDateTime startDate, OffsetDateTime endDate, String protocolCanonical) {
        return dsl.select(
                    DSL.field(INTELLIGENCE_DELIVERIES.STATUS.getName()),
                    DSL.field("count()", Long.class))
                  .from(DSL.table(DSL.sql(table())))
                  .where(dateRange(startDate, endDate))
                  .and(protocolCondition(protocolCanonical))
                  .groupBy(DSL.field(INTELLIGENCE_DELIVERIES.STATUS.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countByActionTypeFiltered(OffsetDateTime startDate, OffsetDateTime endDate, String protocolCanonical) {
        return dsl.select(
                    DSL.field(INTELLIGENCE_DELIVERIES.ACTION_TYPE.getName()),
                    DSL.field("count()", Long.class))
                  .from(DSL.table(DSL.sql(table())))
                  .where(dateRange(startDate, endDate))
                  .and(protocolCondition(protocolCanonical))
                  .groupBy(DSL.field(INTELLIGENCE_DELIVERIES.ACTION_TYPE.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countBySeverityFiltered(OffsetDateTime startDate, OffsetDateTime endDate, String protocolCanonical) {
        return dsl.select(
                    DSL.field(INTELLIGENCE_DELIVERIES.SEVERITY.getName()),
                    DSL.field("count()", Long.class))
                  .from(DSL.table(DSL.sql(table())))
                  .where(dateRange(startDate, endDate))
                  .and(protocolCondition(protocolCanonical))
                  .groupBy(DSL.field(INTELLIGENCE_DELIVERIES.SEVERITY.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    @Override
    public List<Object[]> countByDestinationFiltered(OffsetDateTime startDate, OffsetDateTime endDate, String protocolCanonical) {
        return dsl.select(
                    DSL.field(INTELLIGENCE_DELIVERIES.DESTINATION.getName()),
                    DSL.field("count()", Long.class).as("cnt"))
                  .from(DSL.table(DSL.sql(table())))
                  .where(dateRange(startDate, endDate))
                  .and(protocolCondition(protocolCanonical))
                  .groupBy(DSL.field(INTELLIGENCE_DELIVERIES.DESTINATION.getName()))
                  .orderBy(DSL.field("cnt").desc())
                  .fetch()
                  .map(r -> new Object[]{r.get(0, String.class), r.get(1, Long.class)});
    }

    private org.jooq.Condition dateRange(OffsetDateTime startDate, OffsetDateTime endDate) {
        return DSL.condition(
                INTELLIGENCE_DELIVERIES.CREATED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                dtStart(startDate))
            .and(DSL.condition(
                INTELLIGENCE_DELIVERIES.CREATED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                dtEnd(endDate)));
    }

    private org.jooq.Condition protocolCondition(String protocolCanonical) {
        return (protocolCanonical == null || protocolCanonical.isBlank())
                ? DSL.noCondition()
                : DSL.field(INTELLIGENCE_DELIVERIES.PROTOCOL_CANONICAL.getName()).eq(protocolCanonical);
    }
}
