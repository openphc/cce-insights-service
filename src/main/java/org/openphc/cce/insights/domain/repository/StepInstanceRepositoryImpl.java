package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.SlaStatus;
import org.openphc.cce.insights.domain.enums.StepStatus;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.openphc.cce.insights.jooq.Tables.DEVIATIONS;
import static org.openphc.cce.insights.jooq.Tables.INBOUND_EVENT_LOGS;
import static org.openphc.cce.insights.jooq.Tables.MV_PATIENT_FACILITY_LATEST;
import static org.openphc.cce.insights.jooq.Tables.PROTOCOL_INSTANCES;
import static org.openphc.cce.insights.jooq.Tables.STEP_INSTANCES;

@Repository
public class StepInstanceRepositoryImpl
        extends AbstractClickHouseRepository<StepInstance, UUID>
        implements StepInstanceRepository {

    public StepInstanceRepositoryImpl(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected String getTableName() {
        return STEP_INSTANCES.getName();
    }

    @Override
    protected StepInstance fromRecord(Record r) {
        return toStepInstance(r);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // ClickHouse aggregate function helpers
    // ══════════════════════════════════════════════════════════════════════════════

    private static Field<Long> uniq(String columnExpr) {
        return DSL.field("uniq(" + columnExpr + ")", Long.class);
    }

    private static Field<Long> uniqIf(String columnExpr, String condition) {
        return DSL.field("uniqIf(" + columnExpr + ", " + condition + ")", Long.class);
    }

    private static Field<Double> avgIf(String expression, String condition) {
        return DSL.field("avgIf(" + expression + ", " + condition + ")", Double.class);
    }

    private static Field<Double> medianIf(String expression, String condition) {
        return DSL.field("medianIf(" + expression + ", " + condition + ")", Double.class);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Reusable sub-query builders
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Derives the "completed_steps" aggregate field — steps with step_status COMPLETED
     * that have no deviation record (1.x also counted SKIPPED, which 2.0.0 dropped). Uses a LEFT
     * JOIN on deviations (alias "d") rather than a subquery inside the aggregate, because
     * ClickHouse does not support subqueries inside aggregate function conditions (Code 62 syntax error).
     * The caller must add: LEFT JOIN finalAs(DEVIATIONS, "d") ON d.step_instance_id = si.id
     */
    private Field<Long> completedStepsAggregate(String stepAlias) {
        // ClickHouse LEFT JOIN on non-Nullable UUID columns returns the zero UUID (not NULL)
        // for unmatched rows, so isNull(d.id) is always false. Compare against zero UUID instead.
        return DSL.field(
                "uniqIf(" + stepAlias + ".id, " + stepAlias + "." + STEP_INSTANCES.STEP_STATUS.getName() +
                " = 'COMPLETED' AND d.id = toUUID('00000000-0000-0000-0000-000000000000'))",
                Long.class
        ).as("completed_steps");
    }

    /**
     * Subquery that returns distinct (subject, practitioner_ref) pairs from
     * inbound_event_logs — joined onto protocol instances to resolve practitioner info.
     */
    private Table<?> practitionerPairsSubquery() {
        var inboundEventLogs = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()))
                .from(inboundEventLogs)
                .where(DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()).ne(""))
                .groupBy(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()))
                .asTable("iel");
    }

    /**
     * Same as practitionerPairsSubquery() but with date-range and facility filters applied.
     * All filter values are passed as bind parameters — jOOQ handles parameterisation safely.
     */
    private Table<?> practitionerPairsSubqueryFiltered(OffsetDateTime startDate,
                                                        OffsetDateTime endDate,
                                                        String facilityId) {
        String fid = str(facilityId);
        var inboundEventLogs = finalAs(INBOUND_EVENT_LOGS, "iel");
        return dsl.select(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()))
                .from(inboundEventLogs)
                .where(DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()).ne(""))
                .and(DSL.condition(
                        "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " >= parseDateTime64BestEffort(?)",
                        dtStart(startDate)))
                .and(DSL.condition(
                        "iel." + INBOUND_EVENT_LOGS.RECEIVED_AT.getName() + " <= parseDateTime64BestEffort(?)",
                        dtEnd(endDate)))
                .and(DSL.condition(
                        "? = '' OR iel." + INBOUND_EVENT_LOGS.FACILITY_ID.getName() + " = ?",
                        fid, fid))
                .groupBy(
                    DSL.field("iel." + INBOUND_EVENT_LOGS.SUBJECT.getName()),
                    DSL.field("iel." + INBOUND_EVENT_LOGS.PRACTITIONER_REF.getName()))
                .asTable("iel");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Result mappers
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Maps a jOOQ Record to StepInstance.
     * Column names come directly from generated field metadata — no intermediate constants.
     * If a column is renamed in ClickHouse and generateJooq is re-run, the .getName()
     * call here breaks at compile time, surfacing the mismatch immediately.
     */
    private StepInstance toStepInstance(Record r) {
        StepStatus stepStatus = null;
        try { stepStatus = StepStatus.valueOf(r.get(STEP_INSTANCES.STEP_STATUS.getName(), String.class)); }
        catch (Exception ignored) {}

        // sla_status is '' until the Step SLA Service reaches a verdict — kept as null here.
        SlaStatus slaStatus = null;
        try {
            String slaStr = r.get(STEP_INSTANCES.SLA_STATUS.getName(), String.class);
            if (slaStr != null && !slaStr.isEmpty()) slaStatus = SlaStatus.valueOf(slaStr);
        } catch (Exception ignored) {}

        Integer repeatIdx = r.get(STEP_INSTANCES.REPEAT_INDEX.getName(), Integer.class);
        return StepInstance.builder()
                .id(r.get(STEP_INSTANCES.ID.getName(), UUID.class))
                .protocolInstanceId(r.get(STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName(), UUID.class))
                .actionId(r.get(STEP_INSTANCES.ACTION_ID.getName(), String.class))
                .repeatIndex(repeatIdx != null ? repeatIdx : 0)
                .stepStatus(stepStatus)
                .slaStatus(slaStatus)
                .dueDate(recordDateTime(r, STEP_INSTANCES.DUE_DATE.getName()))
                .completedAt(recordDateTime(r, STEP_INSTANCES.COMPLETED_AT.getName()))
                .completedBySource(r.get(STEP_INSTANCES.COMPLETED_BY_SOURCE.getName(), String.class))
                .matchedEventId(parseUUID(r.get(STEP_INSTANCES.MATCHED_EVENT_ID.getName(), String.class)))
                .requiredBehavior(r.get(STEP_INSTANCES.REQUIRED_BEHAVIOR.getName(), String.class))
                .build();
    }

    /** Maps a step-analytics Record (11 columns) to Object[]. */
    private static Object[] toStepAnalyticsRow(Record r) {
        return new Object[]{
                r.get(STEP_INSTANCES.ACTION_ID.getName(), String.class),
                r.get("total_instances",         Long.class),
                r.get("completed_count",         Long.class),
                r.get("completed_on_time_count", Long.class),
                r.get("completed_late_count",    Long.class),
                r.get("overdue_count",           Long.class),
                r.get("missed_count",            Long.class),
                r.get("not_started_count",       Long.class),
                r.get("sla_unjudged_count",      Long.class),
                r.get("avg_days_to_complete",    Double.class),
                r.get("median_days_to_complete", Double.class)
        };
    }

    /**
     * Per-action patient counts shared by the two step-analytics queries (aliases si, pi).
     * 2.0.0 two-status model: step_status says whether the step was recorded, sla_status whether it
     * was on time. overdue/missed are SLA verdicts, so they include steps completed after the
     * threshold; completed_on_time = COMPLETED+MET, completed_late = COMPLETED+OVERDUE|MISSED.
     */
    private static List<Field<?>> stepAnalyticsFields() {
        String patient = "pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName();
        String status  = "si." + STEP_INSTANCES.STEP_STATUS.getName();
        String sla     = "si." + STEP_INSTANCES.SLA_STATUS.getName();
        String daysToComplete = "dateDiff('second', si.due_date, si.completed_at) / 86400.0";
        String wasCompleted   = status + " = 'COMPLETED' AND isNotNull(si.due_date)";
        return List.of(
                DSL.field("si." + STEP_INSTANCES.ACTION_ID.getName())
                   .as(STEP_INSTANCES.ACTION_ID.getName()),
                uniq(patient).as("total_instances"),
                uniqIf(patient, status + " = 'COMPLETED'").as("completed_count"),
                uniqIf(patient, status + " = 'COMPLETED' AND " + sla + " = 'MET'").as("completed_on_time_count"),
                uniqIf(patient, status + " = 'COMPLETED' AND " + sla + " IN ('OVERDUE','MISSED')").as("completed_late_count"),
                uniqIf(patient, sla + " = 'OVERDUE'").as("overdue_count"),
                uniqIf(patient, sla + " = 'MISSED'").as("missed_count"),
                uniqIf(patient, status + " = 'NOT_STARTED'").as("not_started_count"),
                uniqIf(patient, sla + " = ''").as("sla_unjudged_count"),
                avgIf(daysToComplete, wasCompleted).as("avg_days_to_complete"),
                medianIf(daysToComplete, wasCompleted).as("median_days_to_complete"));
    }

    /**
     * Step-metric aggregate shared by the aggregateStepMetrics* methods (aliases si, pi; si is
     * LEFT-joined, so a pi with no steps yields one row with step_status '' — every count below
     * excludes it). Column order = {@link StepInstanceRepository#aggregateStepMetrics} and the
     * step_* columns of mv_daily_compliance_kpis.
     */
    private static List<Field<?>> stepMetricsFields() {
        String status = "si." + STEP_INSTANCES.STEP_STATUS.getName();
        String sla    = "si." + STEP_INSTANCES.SLA_STATUS.getName();
        return List.of(
                DSL.field("countIf(" + status + " = 'COMPLETED')",                         Long.class).as("completed"),
                DSL.field("countIf(" + status + " = 'NOT_STARTED')",                       Long.class).as("not_started"),
                DSL.field("countIf(" + sla + " = 'MET')",                                  Long.class).as("sla_met"),
                DSL.field("countIf(" + sla + " = 'OVERDUE')",                              Long.class).as("sla_overdue"),
                DSL.field("countIf(" + sla + " = 'MISSED')",                               Long.class).as("sla_missed"),
                DSL.field("countIf(" + status + " != '' AND " + sla + " = '')",           Long.class).as("sla_unjudged"),
                DSL.field("countIf(" + status + " = 'COMPLETED' AND " + sla + " = 'MET')", Long.class).as("completed_on_time"),
                DSL.field("countIf(" + status + " = 'COMPLETED' AND " + sla + " IN ('OVERDUE','MISSED'))",
                                                                                           Long.class).as("completed_late"),
                DSL.field("countIf(" + status + " != '')",                                 Long.class).as("total_steps"),
                DSL.field("uniq(pi.id)",                                                   Long.class).as("total_enrollments"));
    }

    /** Maps a compliance Record (groupBy column, total_steps, completed_steps) to Object[]. */
    private static Object[] toComplianceRow(Record r, String groupByCol) {
        return new Object[]{
                r.get(groupByCol,        String.class),
                r.get("total_steps",     Long.class),
                r.get("completed_steps", Long.class)
        };
    }

    /** Maps a completion-funnel Record (action_id, reached, completed) to Object[]. */
    private static Object[] toFunnelRow(Record r) {
        return new Object[]{
                r.get(STEP_INSTANCES.ACTION_ID.getName(), String.class),
                r.get("reached_count",   Long.class),
                r.get("completed_count", Long.class)
        };
    }

    /** Maps a referral-event Record (facility_id, outbound, inbound) to Object[]. */
    private static Object[] toReferralRow(Record r) {
        return new Object[]{
                r.get("facility_id",     String.class),
                r.get("outbound_events", Long.class),
                r.get("inbound_events",  Long.class)
        };
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Repository methods — full jOOQ DSL
    // ══════════════════════════════════════════════════════════════════════════════

    @Override
    public List<StepInstance> findByProtocolInstanceId(UUID protocolInstanceId) {
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        return dsl.select(DSL.asterisk())
                  .from(stepInstances)
                  .where(DSL.condition(
                          "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = toUUID(?)",
                          protocolInstanceId.toString()))
                  .fetch()
                  .map(this::toStepInstance);
    }

    @Override
    public List<StepInstance> findByProtocolInstanceIdIn(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> idStrings = ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.toList());
        var si = finalAs(STEP_INSTANCES, "si");
        return dsl.select(DSL.asterisk())
                  .from(si)
                  .where(DSL.field("si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName()).in(idStrings))
                  .fetch()
                  .map(this::toStepInstance);
    }

    @Override
    public List<Object[]> findProtocolStepMetricsByFacility(String facilityId) {
        var si = finalAs(STEP_INSTANCES, "si");
        var pi = protocolInstancesWithCanonical("pi");
        var pf = MV_PATIENT_FACILITY_LATEST.as("pf");
        String status = "si." + STEP_INSTANCES.STEP_STATUS.getName();

        return dsl.select(
                    DSL.field("pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName()),
                    DSL.field("any(pi.protocol_canonical)").as("protocol_canonical"),
                    DSL.field("uniq(pi.id)", Long.class).as("enrollments"),
                    DSL.field("countIf(" + status + " != '')", Long.class).as("total_steps"),
                    DSL.field("countIf(" + status + " = 'COMPLETED')", Long.class).as("completed_steps")
                )
                .from(pi)
                .join(pf).on(DSL.condition(
                        "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .leftJoin(si).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .where(DSL.field("pf.facility_id").eq(facilityId))
                .groupBy(DSL.field("pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName()))
                .fetch()
                .map(r -> new Object[]{
                        r.get(0, String.class),
                        r.get(1, String.class),
                        r.get(2, Long.class),
                        r.get(3, Long.class),
                        r.get(4, Long.class)
                });
    }

    @Override
    public List<StepInstance> findByProtocolInstanceIdOrderByDueDateAsc(UUID protocolInstanceId) {
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        return dsl.select(DSL.asterisk())
                  .from(stepInstances)
                  .where(DSL.condition(
                          "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = toUUID(?)",
                          protocolInstanceId.toString()))
                  .orderBy(DSL.field("si." + STEP_INSTANCES.DUE_DATE.getName()).asc())
                  .fetch()
                  .map(this::toStepInstance);
    }

    @Override
    public List<Object[]> countByProtocolInstanceIdGroupByStatus(UUID protocolInstanceId) {
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        return dsl.select(
                    DSL.field("si." + STEP_INSTANCES.STEP_STATUS.getName()),
                    DSL.field("si." + STEP_INSTANCES.SLA_STATUS.getName()),
                    DSL.count())
                  .from(stepInstances)
                  .where(DSL.condition(
                          "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = toUUID(?)",
                          protocolInstanceId.toString()))
                  .groupBy(
                          DSL.field("si." + STEP_INSTANCES.STEP_STATUS.getName()),
                          DSL.field("si." + STEP_INSTANCES.SLA_STATUS.getName()))
                  .fetch()
                  .map(r -> new Object[]{r.value1(), r.value2(), r.value3()});
    }

    @Override
    public List<Object[]> findSlaThresholdsByStepInstanceIdIn(List<UUID> stepInstanceIds) {
        if (stepInstanceIds == null || stepInstanceIds.isEmpty()) return List.of();
        List<String> idStrings = stepInstanceIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.toList());
        List<Object[]> result = new java.util.ArrayList<>();
        for (List<String> chunk : chunkIds(idStrings)) {
            result.addAll(dsl.select(
                        DSL.field("sla.step_instance_id"),
                        DSL.field("sla.due_threshold").as("due_threshold"),
                        DSL.field("sla.missed_threshold").as("missed_threshold"))
                    .from(slaThresholds())
                    .where(DSL.field("sla.step_instance_id").in(chunk))
                    .fetch()
                    .map(r -> new Object[]{
                            parseUUID(r.get(0, String.class)),
                            recordDateTime(r, "due_threshold"),
                            recordDateTime(r, "missed_threshold")}));
        }
        return result;
    }

    @Override
    public List<Object[]> findStepAnalytics(UUID protocolDefId) {
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        var protocolInstances = finalAs(PROTOCOL_INSTANCES, "pi");

        return dsl.select(stepAnalyticsFields())
                .from(stepInstances)
                .join(protocolInstances).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .where(DSL.condition(
                        "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                        protocolDefId.toString()))
                .groupBy(DSL.field("si." + STEP_INSTANCES.ACTION_ID.getName()))
                .fetch()
                .map(StepInstanceRepositoryImpl::toStepAnalyticsRow);
    }

    @Override
    public List<Object[]> findStepAnalyticsByFacility(UUID protocolDefId, String facilityId) {
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        var protocolInstances = finalAs(PROTOCOL_INSTANCES, "pi");
        var patientFacility = MV_PATIENT_FACILITY_LATEST.as("pf");

        return dsl.select(stepAnalyticsFields())
                .from(stepInstances)
                .join(protocolInstances).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .join(patientFacility).on(DSL.condition(
                        "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .where(DSL.condition(
                        "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                        protocolDefId.toString()))
                .and(DSL.field("pf.facility_id").eq(facilityId))
                .groupBy(DSL.field("si." + STEP_INSTANCES.ACTION_ID.getName()))
                .fetch()
                .map(StepInstanceRepositoryImpl::toStepAnalyticsRow);
    }

    @Override
    public List<Object[]> findCompletionFunnel(UUID protocolDefId) {
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        var protocolInstances = finalAs(PROTOCOL_INSTANCES, "pi");

        return dsl.select(
                    DSL.field("si." + STEP_INSTANCES.ACTION_ID.getName())
                       .as(STEP_INSTANCES.ACTION_ID.getName()),
                    uniq("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()).as("reached_count"),
                    uniqIf("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName(),
                            "si." + STEP_INSTANCES.STEP_STATUS.getName() + " = 'COMPLETED'").as("completed_count")
                )
                .from(stepInstances)
                .join(protocolInstances).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .where(DSL.condition(
                        "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                        protocolDefId.toString()))
                .groupBy(DSL.field("si." + STEP_INSTANCES.ACTION_ID.getName()))
                .fetch()
                .map(StepInstanceRepositoryImpl::toFunnelRow);
    }

    @Override
    public List<Object[]> findStepComplianceByFacility(UUID protocolDefinitionId) {
        String pid = protocolDefinitionId != null ? protocolDefinitionId.toString() : "";
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        var protocolInstances = finalAs(PROTOCOL_INSTANCES, "pi");
        var patientFacility = MV_PATIENT_FACILITY_LATEST.as("pf");
        var deviations = finalAs(DEVIATIONS, "d");

        return dsl.select(
                    DSL.field("pf.facility_id").as("facility_id"),
                    uniq("si.id").as("total_steps"),
                    completedStepsAggregate("si")
                )
                .from(stepInstances)
                .join(protocolInstances).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .join(patientFacility).on(DSL.condition(
                        "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .leftJoin(deviations).on(DSL.condition("d.step_instance_id = si.id"))
                .where(DSL.field("pf.facility_id").ne(""))
                .and(DSL.condition(
                        "toUUIDOrNull(?) IS NULL OR pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() +
                        " = toUUIDOrNull(?)", pid, pid))
                .groupBy(DSL.field("pf.facility_id"))
                .fetch()
                .map(r -> toComplianceRow(r, "facility_id"));
    }

    @Override
    public List<Object[]> findReferralEventCountsByFacility(UUID protocolDefinitionId) {
        String pid = protocolDefinitionId != null ? protocolDefinitionId.toString() : "";
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        var protocolInstances = finalAs(PROTOCOL_INSTANCES, "pi");
        var patientFacility = MV_PATIENT_FACILITY_LATEST.as("pf");
        String actionId = "si." + STEP_INSTANCES.ACTION_ID.getName();

        return dsl.select(
                    DSL.field("pf.facility_id").as("facility_id"),
                    DSL.field("uniqIf(si.id, endsWith(" + actionId + ", '-referral'))",
                            Long.class).as("outbound_events"),
                    DSL.field("uniqIf(si.id, endsWith(" + actionId + ", '-referral-ack'))",
                            Long.class).as("inbound_events")
                )
                .from(stepInstances)
                .join(protocolInstances).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .join(patientFacility).on(DSL.condition(
                        "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .where(DSL.field("pf.facility_id").ne(""))
                .and(DSL.condition(
                        "(endsWith(" + actionId + ", '-referral') OR endsWith(" + actionId + ", '-referral-ack'))"))
                .and(DSL.field("si." + STEP_INSTANCES.STEP_STATUS.getName()).eq("COMPLETED"))
                .and(DSL.condition(
                        "toUUIDOrNull(?) IS NULL OR pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() +
                        " = toUUIDOrNull(?)", pid, pid))
                .groupBy(DSL.field("pf.facility_id"))
                .fetch()
                .map(StepInstanceRepositoryImpl::toReferralRow);
    }

    @Override
    public List<Object[]> findStepComplianceByPractitioner() {
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        var protocolInstances = finalAs(PROTOCOL_INSTANCES, "pi");
        var practitionerPairs = practitionerPairsSubquery();
        var deviations = finalAs(DEVIATIONS, "d");

        return dsl.select(
                    DSL.field("iel.practitioner_ref").as("practitioner_ref"),
                    uniq("si.id").as("total_steps"),
                    completedStepsAggregate("si")
                )
                .from(stepInstances)
                .join(protocolInstances).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .join(practitionerPairs).on(DSL.condition(
                        "iel.subject = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .leftJoin(deviations).on(DSL.condition("d.step_instance_id = si.id"))
                .where(DSL.field("iel.practitioner_ref").ne(""))
                .groupBy(DSL.field("iel.practitioner_ref"))
                .fetch()
                .map(r -> toComplianceRow(r, "practitioner_ref"));
    }

    @Override
    public List<Object[]> findStepComplianceByPractitionerFiltered(OffsetDateTime startDate,
                                                                    OffsetDateTime endDate,
                                                                    String facilityId) {
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        var protocolInstances = finalAs(PROTOCOL_INSTANCES, "pi");
        var practitionerPairs = practitionerPairsSubqueryFiltered(startDate, endDate, facilityId);
        var deviations = finalAs(DEVIATIONS, "d");

        return dsl.select(
                    DSL.field("iel.practitioner_ref").as("practitioner_ref"),
                    uniq("si.id").as("total_steps"),
                    completedStepsAggregate("si")
                )
                .from(stepInstances)
                .join(protocolInstances).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .join(practitionerPairs).on(DSL.condition(
                        "iel.subject = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .leftJoin(deviations).on(DSL.condition("d.step_instance_id = si.id"))
                .where(DSL.field("iel.practitioner_ref").ne(""))
                .groupBy(DSL.field("iel.practitioner_ref"))
                .fetch()
                .map(r -> toComplianceRow(r, "practitioner_ref"));
    }

    @Override
    public List<Object[]> findStepComplianceByPatientIds(List<String> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) return List.of();
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        var protocolInstances = finalAs(PROTOCOL_INSTANCES, "pi");
        var deviations = finalAs(DEVIATIONS, "d");

        return dsl.select(
                    DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()).as("patient_id"),
                    uniq("si.id").as("total_steps"),
                    completedStepsAggregate("si")
                )
                .from(stepInstances)
                .join(protocolInstances).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .leftJoin(deviations).on(DSL.condition("d.step_instance_id = si.id"))
                .where(DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()).in(patientIds))
                .groupBy(DSL.field("pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .fetch()
                .map(r -> toComplianceRow(r, "patient_id"));
    }

    @Override
    public List<Object[]> findAtRiskHotspotCounts() {
        var stepInstances = finalAs(STEP_INSTANCES, "si");
        var protocolInstances = finalAs(PROTOCOL_INSTANCES, "pi");
        var patientFacility = MV_PATIENT_FACILITY_LATEST.as("pf");
        String stepStatus = "si." + STEP_INSTANCES.STEP_STATUS.getName();
        String slaStatus = "si." + STEP_INSTANCES.SLA_STATUS.getName();
        String patientId = "pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName();

        // Outstanding steps only: a step completed late keeps its OVERDUE/MISSED verdict but no
        // longer puts the patient at risk (1.x: state MISSED / OVERDUE, never COMPLETED).
        var perPatient = dsl.select(
                    DSL.field("pf.facility_id").as("facility_id"),
                    DSL.field(patientId).as("patient_id"),
                    DSL.field("maxIf(1, " + stepStatus + " != 'COMPLETED' AND " + slaStatus + " = 'MISSED')",
                            Integer.class).as("has_missed"),
                    DSL.field("maxIf(1, " + stepStatus + " != 'COMPLETED' AND " + slaStatus + " = 'OVERDUE')",
                            Integer.class).as("has_overdue")
                )
                .from(stepInstances)
                .join(protocolInstances).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .join(patientFacility).on(DSL.condition("pf.patient_id = " + patientId))
                .where(DSL.field("pf.facility_id").ne(""))
                .groupBy(DSL.field("pf.facility_id"), DSL.field(patientId))
                .asTable("per_patient");

        return dsl.select(
                    DSL.field("per_patient.facility_id", String.class).as("facility_id"),
                    DSL.field("countIf(per_patient.has_missed = 1)", Long.class).as("non_compliant"),
                    DSL.field("countIf(per_patient.has_missed = 0 AND per_patient.has_overdue = 1)", Long.class).as("at_risk"),
                    DSL.field("countIf(per_patient.has_missed = 0 AND per_patient.has_overdue = 0)", Long.class).as("on_track")
                )
                .from(perPatient)
                .groupBy(DSL.field("per_patient.facility_id"))
                .fetch()
                .map(r -> new Object[]{
                        r.get("facility_id", String.class),
                        r.get("non_compliant", Long.class),
                        r.get("at_risk", Long.class),
                        r.get("on_track", Long.class)
                });
    }

    @Override
    public Object[] aggregateStepMetrics(UUID protocolDefinitionId) {
        var si = finalAs(STEP_INSTANCES, "si");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");

        org.jooq.Record r = dsl.select(stepMetricsFields())
                .from(pi)
                .leftJoin(si).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .where(DSL.condition(
                        "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                        protocolDefinitionId.toString()))
                .fetchOne();
        return r != null ? r.intoArray() : new Object[]{0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L};
    }

    @Override
    public Object[] aggregateStepMetricsByFacility(String facilityId) {
        var si = finalAs(STEP_INSTANCES, "si");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = MV_PATIENT_FACILITY_LATEST.as("pf");

        org.jooq.Record r = dsl.select(stepMetricsFields())
                .from(pi)
                .join(pf).on(DSL.condition(
                        "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .leftJoin(si).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .where(DSL.field("pf.facility_id").eq(facilityId))
                .fetchOne();
        return r != null ? r.intoArray() : new Object[]{0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L};
    }

    @Override
    public Object[] aggregateStepMetricsByProtocolAndFacility(UUID protocolDefinitionId, String facilityId) {
        var si = finalAs(STEP_INSTANCES, "si");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");
        var pf = MV_PATIENT_FACILITY_LATEST.as("pf");

        org.jooq.Record r = dsl.select(stepMetricsFields())
                .from(pi)
                .join(pf).on(DSL.condition(
                        "pf.patient_id = pi." + PROTOCOL_INSTANCES.PATIENT_ID.getName()))
                .leftJoin(si).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .where(DSL.condition(
                        "pi." + PROTOCOL_INSTANCES.PROTOCOL_DEFINITION_ID.getName() + " = toUUID(?)",
                        protocolDefinitionId.toString()))
                .and(DSL.field("pf.facility_id").eq(facilityId))
                .fetchOne();
        return r != null ? r.intoArray() : new Object[]{0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L};
    }

    @Override
    public Object[] aggregateStepMetricsAll() {
        var si = finalAs(STEP_INSTANCES, "si");
        var pi = finalAs(PROTOCOL_INSTANCES, "pi");

        org.jooq.Record r = dsl.select(stepMetricsFields())
                .from(pi)
                .leftJoin(si).on(DSL.condition(
                        "si." + STEP_INSTANCES.PROTOCOL_INSTANCE_ID.getName() + " = pi.id"))
                .fetchOne();
        return r != null ? r.intoArray() : new Object[]{0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L};
    }

    @Override
    public Object[] aggregateConsentMetrics() {
        var si = finalAs(STEP_INSTANCES, "si");
        String stepStatus = "si." + STEP_INSTANCES.STEP_STATUS.getName();
        String actionId = "si." + STEP_INSTANCES.ACTION_ID.getName();

        org.jooq.Record r = dsl.select(
                    DSL.field("countIf(" + actionId + " = 'consent-request' AND " + stepStatus + " = 'COMPLETED')",
                            Long.class).as("total_received"),
                    DSL.field("countIf(" + actionId + " = 'consent-verification' AND " + stepStatus + " = 'COMPLETED')",
                            Long.class).as("total_verified")
                )
                .from(si)
                .fetchOne();
        return r != null ? r.intoArray() : new Object[]{0L, 0L};
    }
}
