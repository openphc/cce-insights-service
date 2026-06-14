package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.CompletionStatus;
import org.openphc.cce.insights.domain.enums.StepState;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class StepInstanceRepositoryImpl
        extends AbstractClickHouseRepository<StepInstance, UUID>
        implements StepInstanceRepository {

    public StepInstanceRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    protected String getTableName() {
        return "step_instances";
    }

    @Override
    protected RowMapper<StepInstance> rowMapper() {
        return (rs, n) -> {
            StepState state = null;
            try { state = StepState.valueOf(rs.getString("state")); } catch (Exception ignored) {}
            CompletionStatus cs = null;
            try {
                String csStr = rs.getString("completion_status");
                if (csStr != null && !csStr.isEmpty()) cs = CompletionStatus.valueOf(csStr);
            } catch (Exception ignored) {}
            return StepInstance.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .protocolInstanceId(parseUUID(rs.getString("protocol_instance_id")))
                    .actionId(rs.getString("action_id"))
                    .repeatIndex(rs.getInt("repeat_index"))
                    .state(state)
                    .dueDate(toOffsetDateTime(rs, "due_date"))
                    .overdueDate(toOffsetDateTime(rs, "overdue_date"))
                    .missedDate(toOffsetDateTime(rs, "missed_date"))
                    .completedAt(toOffsetDateTime(rs, "completed_at"))
                    .completionStatus(cs)
                    .completedBySource(rs.getString("completed_by_source"))
                    .completedByEventId(parseUUID(rs.getString("completed_by_event_id")))
                    .requiredBehavior(rs.getString("required_behavior"))
                    .build();
        };
    }

    @Override
    public List<StepInstance> findByProtocolInstanceId(UUID protocolInstanceId) {
        return jdbc.query(
                "SELECT * FROM step_instances" + finalClause() + " WHERE protocol_instance_id = toUUID(:id)",
                Map.of("id", protocolInstanceId.toString()), rowMapper());
    }

    @Override
    public List<StepInstance> findByProtocolInstanceIdOrderByDueDateAsc(UUID protocolInstanceId) {
        return jdbc.query(
                "SELECT * FROM step_instances" + finalClause() + " WHERE protocol_instance_id = toUUID(:id) " +
                "ORDER BY due_date ASC",
                Map.of("id", protocolInstanceId.toString()), rowMapper());
    }

    @Override
    public List<Object[]> countByProtocolInstanceIdGroupByState(UUID protocolInstanceId) {
        return jdbc.query(
                "SELECT state, count() FROM step_instances" + finalClause() +
                " WHERE protocol_instance_id = toUUID(:id) GROUP BY state",
                Map.of("id", protocolInstanceId.toString()),
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> findStepAnalytics(UUID protocolDefId) {
        return jdbc.query(
                "SELECT si.action_id, " +
                "uniq(pi.patient_id) AS total_instances, " +
                "uniqIf(pi.patient_id, si.state = 'COMPLETED') AS completed_count, " +
                "uniqIf(pi.patient_id, si.completion_status = 'EARLY') AS early_count, " +
                "uniqIf(pi.patient_id, si.completion_status = 'ON_TIME') AS on_time_count, " +
                "uniqIf(pi.patient_id, si.completion_status = 'LATE') AS late_count, " +
                "uniqIf(pi.patient_id, si.state = 'OVERDUE') AS overdue_count, " +
                "uniqIf(pi.patient_id, si.state = 'MISSED') AS missed_count, " +
                "uniqIf(pi.patient_id, si.state = 'SKIPPED') AS skipped_count, " +
                "uniqIf(pi.patient_id, si.state IN ('PENDING','DUE')) AS pending_count, " +
                "avgIf(dateDiff('second', si.due_date, si.completed_at) / 86400.0, " +
                "      si.state = 'COMPLETED' AND isNotNull(si.due_date)) AS avg_days_to_complete, " +
                "medianIf(dateDiff('second', si.due_date, si.completed_at) / 86400.0, " +
                "         si.state = 'COMPLETED' AND isNotNull(si.due_date)) AS median_days_to_complete " +
                "FROM step_instances si" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON si.protocol_instance_id = pi.id " +
                "WHERE pi.protocol_definition_id = toUUID(:id) " +
                "GROUP BY si.action_id",
                Map.of("id", protocolDefId.toString()),
                (rs, n) -> new Object[]{
                        rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8),
                        rs.getLong(9), rs.getLong(10), rs.getDouble(11), rs.getDouble(12)});
    }

    @Override
    public List<Object[]> findStepAnalyticsByFacility(UUID protocolDefId, String facilityId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", protocolDefId.toString())
                .addValue("fid", facilityId);
        return jdbc.query(
                "SELECT si.action_id, " +
                "uniq(pi.patient_id) AS total_instances, " +
                "uniqIf(pi.patient_id, si.state = 'COMPLETED') AS completed_count, " +
                "uniqIf(pi.patient_id, si.completion_status = 'EARLY') AS early_count, " +
                "uniqIf(pi.patient_id, si.completion_status = 'ON_TIME') AS on_time_count, " +
                "uniqIf(pi.patient_id, si.completion_status = 'LATE') AS late_count, " +
                "uniqIf(pi.patient_id, si.state = 'OVERDUE') AS overdue_count, " +
                "uniqIf(pi.patient_id, si.state = 'MISSED') AS missed_count, " +
                "uniqIf(pi.patient_id, si.state = 'SKIPPED') AS skipped_count, " +
                "uniqIf(pi.patient_id, si.state IN ('PENDING','DUE')) AS pending_count, " +
                "avgIf(dateDiff('second', si.due_date, si.completed_at) / 86400.0, " +
                "      si.state = 'COMPLETED' AND isNotNull(si.due_date)) AS avg_days_to_complete, " +
                "medianIf(dateDiff('second', si.due_date, si.completed_at) / 86400.0, " +
                "         si.state = 'COMPLETED' AND isNotNull(si.due_date)) AS median_days_to_complete " +
                "FROM step_instances si" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON si.protocol_instance_id = pi.id " +
                "JOIN mv_patient_facility_latest pf ON pf.patient_id = pi.patient_id " +
                "WHERE pi.protocol_definition_id = toUUID(:id) AND pf.facility_id = :fid " +
                "GROUP BY si.action_id",
                p,
                (rs, n) -> new Object[]{
                        rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8),
                        rs.getLong(9), rs.getLong(10), rs.getDouble(11), rs.getDouble(12)});
    }

    @Override
    public List<Object[]> findCompletionFunnel(UUID protocolDefId) {
        return jdbc.query(
                "SELECT si.action_id, " +
                "uniq(pi.patient_id) AS reached_count, " +
                "uniqIf(pi.patient_id, si.state = 'COMPLETED') AS completed_count " +
                "FROM step_instances si" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON si.protocol_instance_id = pi.id " +
                "WHERE pi.protocol_definition_id = toUUID(:id) " +
                "GROUP BY si.action_id",
                Map.of("id", protocolDefId.toString()),
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> findStepComplianceByFacility() {
        return jdbc.query(
                "SELECT pf.facility_id, " +
                "uniq(si.id) AS total_steps, " +
                "uniqIf(si.id, si.state IN ('COMPLETED','SKIPPED') " +
                "  AND si.id NOT IN (SELECT step_instance_id FROM deviations" + finalClause() + ")) AS completed_steps " +
                "FROM step_instances si" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON si.protocol_instance_id = pi.id " +
                "JOIN mv_patient_facility_latest pf ON pf.patient_id = pi.patient_id " +
                "WHERE pf.facility_id != '' " +
                "GROUP BY pf.facility_id",
                new MapSqlParameterSource(),
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> findReferralEventCountsByFacility() {
        return jdbc.query(
                "SELECT pf.facility_id, " +
                "uniqIf(si.id, endsWith(si.action_id, '-referral')) AS outbound_events, " +
                "uniqIf(si.id, endsWith(si.action_id, '-referral-ack')) AS inbound_events " +
                "FROM step_instances si" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON si.protocol_instance_id = pi.id " +
                "JOIN mv_patient_facility_latest pf ON pf.patient_id = pi.patient_id " +
                "WHERE pf.facility_id != '' " +
                "AND (endsWith(si.action_id, '-referral') OR endsWith(si.action_id, '-referral-ack')) " +
                "AND si.state = 'COMPLETED' " +
                "GROUP BY pf.facility_id",
                new MapSqlParameterSource(),
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> findStepComplianceByPractitioner() {
        return jdbc.query(
                "SELECT iel.practitioner_ref, " +
                "uniq(si.id) AS total_steps, " +
                "uniqIf(si.id, si.state IN ('COMPLETED','SKIPPED') " +
                "  AND si.id NOT IN (SELECT step_instance_id FROM deviations" + finalClause() + ")) AS completed_steps " +
                "FROM step_instances si" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON si.protocol_instance_id = pi.id " +
                "JOIN (SELECT subject, practitioner_ref " +
                "      FROM inbound_event_logs" + finalClause() + " WHERE practitioner_ref != '' " +
                "      GROUP BY subject, practitioner_ref) iel ON iel.subject = pi.patient_id " +
                "WHERE iel.practitioner_ref != '' " +
                "GROUP BY iel.practitioner_ref",
                new MapSqlParameterSource(),
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> findStepComplianceByPractitionerFiltered(OffsetDateTime startDate,
                                                                    OffsetDateTime endDate,
                                                                    String facilityId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate))
                .addValue("fid", str(facilityId));
        return jdbc.query(
                "SELECT iel.practitioner_ref, " +
                "uniq(si.id) AS total_steps, " +
                "uniqIf(si.id, si.state IN ('COMPLETED','SKIPPED') " +
                "  AND si.id NOT IN (SELECT step_instance_id FROM deviations" + finalClause() + ")) AS completed_steps " +
                "FROM step_instances si" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON si.protocol_instance_id = pi.id " +
                "JOIN (SELECT subject, practitioner_ref " +
                "      FROM inbound_event_logs" + finalClause() + " WHERE practitioner_ref != '' " +
                "      AND received_at >= parseDateTime64BestEffort(:s) " +
                "      AND received_at <= parseDateTime64BestEffort(:e) " +
                "      AND (:fid = '' OR facility_id = :fid) " +
                "      GROUP BY subject, practitioner_ref) iel ON iel.subject = pi.patient_id " +
                "WHERE iel.practitioner_ref != '' " +
                "GROUP BY iel.practitioner_ref",
                p,
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3)});
    }
}
