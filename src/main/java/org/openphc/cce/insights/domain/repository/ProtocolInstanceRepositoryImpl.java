package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.enums.ProtocolInstanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.openphc.cce.insights.jooq.Tables.DEVIATIONS;
import static org.openphc.cce.insights.jooq.Tables.PROTOCOL_INSTANCES;

@Repository
public class ProtocolInstanceRepositoryImpl
        extends AbstractClickHouseRepository<ProtocolInstance, UUID>
        implements ProtocolInstanceRepository {

    public ProtocolInstanceRepositoryImpl(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected String getTableName() {
        return PROTOCOL_INSTANCES.getName();
    }

    @Override
    protected ProtocolInstance fromRecord(Record r) {
        return toProtocolInstance(r);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Result mappers
    // ══════════════════════════════════════════════════════════════════════════════

    private ProtocolInstance toProtocolInstance(Record r) {
        ProtocolInstanceStatus status = null;
        try {
            String s = r.get(PROTOCOL_INSTANCES.STATUS.getName(), String.class);
            if (s != null) status = ProtocolInstanceStatus.valueOf(s);
        } catch (Exception ignored) {}
        return ProtocolInstance.builder()
                .id(r.get(PROTOCOL_INSTANCES.ID.getName(), UUID.class))
                .protocolDefinitionId(r.get(PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName(), UUID.class))
                .patientId(r.get(PROTOCOL_INSTANCES.PATIENT_ID.getName(), String.class))
                .protocolCanonical(r.get(PROTOCOL_INSTANCES.PROTOCOL_CANONICAL.getName(), String.class))
                .status(status)
                .enrolledAt(recordDateTime(r, PROTOCOL_INSTANCES.ENROLLED_AT.getName()))
                .createdAt(recordDateTime(r, PROTOCOL_INSTANCES.CREATED_AT.getName()))
                .updatedAt(recordDateTime(r, PROTOCOL_INSTANCES.UPDATED_AT.getName()))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Repository methods — full jOOQ DSL
    // ══════════════════════════════════════════════════════════════════════════════

    @Override
    public List<String> findDistinctPatientIds() {
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        return dsl.selectDistinct(DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .from(pi)
                  .orderBy(DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .fetch(0, String.class);
    }

    @Override
    public long countDistinctPatientsEnrolledBetween(OffsetDateTime startDate, OffsetDateTime endDate) {
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        Long count = dsl.select(
                        DSL.field("uniq(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ")", Long.class))
                    .from(pi)
                    .where(enrollmentBetween(startDate, endDate))
                    .fetchOne(0, Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public List<ProtocolInstance> findByPatientId(String patientId) {
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        return dsl.select(DSL.asterisk())
                  .from(pi)
                  .where(DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()).eq(patientId))
                  .fetch()
                  .map(this::toProtocolInstance);
    }

    @Override
    public List<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId) {
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        return dsl.select(DSL.asterisk())
                  .from(pi)
                  .where(DSL.condition(
                          "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                          protocolDefinitionId.toString()))
                  .fetch()
                  .map(this::toProtocolInstance);
    }

    @Override
    public List<ProtocolInstance> findByProtocolDefinitionIdAndEnrolledBetween(UUID protocolDefinitionId,
                                                                                OffsetDateTime startDate,
                                                                                OffsetDateTime endDate) {
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        return dsl.select(DSL.asterisk())
                  .from(pi)
                  .where(DSL.condition(
                          "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                          protocolDefinitionId.toString()))
                  .and(enrollmentBetween(startDate, endDate))
                  .orderBy(DSL.field("pi." + PROTOCOL_INSTANCES.ENROLLED_AT.getName()).desc())
                  .fetch()
                  .map(this::toProtocolInstance);
    }

    @Override
    public Page<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId, Pageable pageable) {
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var condition = DSL.condition(
                "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                protocolDefinitionId.toString());
        List<ProtocolInstance> content = dsl.select(DSL.asterisk())
                .from(pi)
                .where(condition)
                .orderBy(DSL.field("pi." + PROTOCOL_INSTANCES.ENROLLED_AT.getName()).desc())
                .limit(pageable.getPageSize())
                .offset(pageable.getOffset())
                .fetch()
                .map(this::toProtocolInstance);
        long total = dsl.select(DSL.field("count()", Long.class))
                .from(pi)
                .where(condition)
                .fetchOne(0, Long.class);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<Object[]> countByProtocolDefinitionIdGroupByStatus(UUID protocolDefId) {
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        return dsl.select(
                    DSL.field("pi." + PROTOCOL_INSTANCES.STATUS.getName()),
                    DSL.field("count()", Long.class))
                  .from(pi)
                  .where(DSL.condition(
                          "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                          protocolDefId.toString()))
                  .groupBy(DSL.field("pi." + PROTOCOL_INSTANCES.STATUS.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.value1(), r.value2()});
    }

    @Override
    public List<Object[]> findEnrollmentTrends(UUID protocolDefId, String interval,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        String periodExpr = dateTruncExpr(interval, "pi." + PROTOCOL_INSTANCES.ENROLLED_AT.getName());
        return dsl.select(
                    DSL.field(DSL.sql(periodExpr)).as("period"),
                    DSL.field("count()", Long.class).as("enrollments"))
                  .from(pi)
                  .where(DSL.condition(
                          "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                          protocolDefId.toString()))
                  .and(DSL.condition(
                          "pi." + PROTOCOL_INSTANCES.ENROLLED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                          dtStart(startDate)))
                  .and(DSL.condition(
                          "pi." + PROTOCOL_INSTANCES.ENROLLED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                          dtEnd(endDate)))
                  .groupBy(DSL.field(DSL.sql("period")))
                  .orderBy(DSL.field(DSL.sql("period")))
                  .fetch()
                  .map(r -> new Object[]{r.value1(), r.value2()});
    }

    @Override
    public Page<ProtocolInstance> findByProtocolDefinitionIdAndStatus(UUID protocolDefId,
                                                                       ProtocolInstanceStatus status,
                                                                       Pageable pageable) {
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var condition = DSL.condition(
                "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                protocolDefId.toString())
            .and(DSL.field("pi." + PROTOCOL_INSTANCES.STATUS.getName()).eq(status.name()));
        List<ProtocolInstance> content = dsl.select(DSL.asterisk())
                .from(pi)
                .where(condition)
                .orderBy(DSL.field("pi." + PROTOCOL_INSTANCES.ENROLLED_AT.getName()).desc())
                .limit(pageable.getPageSize())
                .offset(pageable.getOffset())
                .fetch()
                .map(this::toProtocolInstance);
        long total = dsl.select(DSL.field("count()", Long.class))
                .from(pi)
                .where(condition)
                .fetchOne(0, Long.class);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<ProtocolInstance> findByProtocolDefinitionIdAndPatientIdContaining(UUID protocolDefId,
                                                                                    String patientId,
                                                                                    Pageable pageable) {
        String pattern = "%" + patientId.toLowerCase() + "%";
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var condition = DSL.condition(
                "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                protocolDefId.toString())
            .and(DSL.condition(
                    "lower(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ") LIKE ?", pattern));
        List<ProtocolInstance> content = dsl.select(DSL.asterisk())
                .from(pi)
                .where(condition)
                .orderBy(DSL.field("pi." + PROTOCOL_INSTANCES.ENROLLED_AT.getName()).desc())
                .limit(pageable.getPageSize())
                .offset(pageable.getOffset())
                .fetch()
                .map(this::toProtocolInstance);
        long total = dsl.select(DSL.field("count()", Long.class))
                .from(pi)
                .where(condition)
                .fetchOne(0, Long.class);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<Object[]> countPatientComplianceByFacility(OffsetDateTime startDate,
                                                            OffsetDateTime endDate) {
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        // alias must come BEFORE FINAL: "table alias FINAL" is valid; "table FINAL alias" is not
        var pf = DSL.table(DSL.sql("mv_patient_facility_latest pf" + finalClause()));
        var d  = finalAs(DEVIATIONS, "d");
        String zeroUuid = "toUUID('00000000-0000-0000-0000-000000000000')";
        String detectedAt = "d." + DEVIATIONS.DETECTED_AT.getName();

        String nonCompliantExpr = (startDate != null || endDate != null)
                ? "uniqIf(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ", d.id != " + zeroUuid
                    + (startDate != null ? " AND " + detectedAt + " >= parseDateTime64BestEffort('"
                        + startDate + "')" : "")
                    + (endDate != null ? " AND " + detectedAt + " <= parseDateTime64BestEffort('"
                        + endDate + "')" : "")
                    + ")"
                : "uniqIf(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ", d.id != " + zeroUuid + ")";

        return dsl.select(
                    DSL.field("pf.facility_id", String.class),
                    DSL.field("uniq(pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName() + ")", Long.class),
                    DSL.field(DSL.sql(nonCompliantExpr), Long.class))
                  .from(pi)
                  .join(pf).on(DSL.condition(
                          "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                  .leftJoin(d).on(DSL.condition(
                          "d." + DEVIATIONS.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                  .where(DSL.field("pf.facility_id").ne(""))
                  .and(enrollmentBetween(startDate, endDate))
                  .groupBy(DSL.field("pf.facility_id"))
                  .orderBy(DSL.field("pf.facility_id"))
                  .fetch()
                  .map(r -> new Object[]{
                          r.get(0, String.class),
                          toLong(r.get(1)),
                          toLong(r.get(2))
                  });
    }

    private org.jooq.Condition enrollmentBetween(OffsetDateTime startDate, OffsetDateTime endDate) {
        String enrolledAt = "pi." + PROTOCOL_INSTANCES.ENROLLED_AT.getName();
        org.jooq.Condition condition = DSL.trueCondition();
        if (startDate != null) {
            condition = condition.and(DSL.condition(
                    enrolledAt + " >= parseDateTime64BestEffort(?)", startDate.toString()));
        }
        if (endDate != null) {
            condition = condition.and(DSL.condition(
                    enrolledAt + " <= parseDateTime64BestEffort(?)", endDate.toString()));
        }
        return condition;
    }

    private static long toLong(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }
}
