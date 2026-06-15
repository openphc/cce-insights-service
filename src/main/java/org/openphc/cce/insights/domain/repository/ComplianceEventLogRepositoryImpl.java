package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.ComplianceEventLog;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ComplianceEventLogRepositoryImpl
        extends AbstractClickHouseRepository<ComplianceEventLog, UUID>
        implements ComplianceEventLogRepository {

    public ComplianceEventLogRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    protected String getTableName() {
        return "compliance_event_logs";
    }

    @Override
    protected RowMapper<ComplianceEventLog> rowMapper() {
        return (rs, n) -> ComplianceEventLog.builder()
                .id(UUID.fromString(rs.getString("id")))
                .cloudeventsId(rs.getString("cloudevents_id"))
                .source(rs.getString("source"))
                .data(rs.getString("data"))
                .processingStatus(rs.getString("processing_status"))
                .receivedAt(toOffsetDateTime(rs, "received_at"))
                .subject(null)
                .type(null)
                .eventTime(null)
                .facilityId(null)
                .protocolInstanceId(parseUUID(tryGetString(rs, "protocol_instance_id")))
                .protocolDefinitionId(parseUUID(tryGetString(rs, "protocol_definition_id")))
                .actionId(tryGetString(rs, "action_id"))
                .matchedStepInstanceId(parseUUID(tryGetString(rs, "matched_step_instance_id")))
                .build();
    }

    /** Full row mapper used by join queries that include inbound_event_logs fields. */
    private RowMapper<ComplianceEventLog> rowMapperJoined() {
        return (rs, n) -> ComplianceEventLog.builder()
                .id(UUID.fromString(rs.getString("id")))
                .cloudeventsId(rs.getString("cloudevents_id"))
                .subject(rs.getString("subject"))
                .type(rs.getString("event_type"))
                .eventTime(toOffsetDateTime(rs, "event_time"))
                .receivedAt(toOffsetDateTime(rs, "received_at"))
                .source(rs.getString("source"))
                .data(rs.getString("data"))
                .processingStatus(rs.getString("processing_status"))
                .facilityId(rs.getString("facility_id"))
                .protocolInstanceId(parseUUID(tryGetString(rs, "protocol_instance_id")))
                .protocolDefinitionId(parseUUID(tryGetString(rs, "protocol_definition_id")))
                .actionId(tryGetString(rs, "action_id"))
                .matchedStepInstanceId(parseUUID(tryGetString(rs, "matched_step_instance_id")))
                .build();
    }

    @Override
    public List<ComplianceEventLog> findBySubjectOrderByEventTimeDesc(String subject) {
        return jdbc.query(
                "SELECT iel.id AS id, iel.cloudevents_id AS cloudevents_id, " +
                "iel.subject AS subject, iel.event_type AS event_type, " +
                "iel.event_time AS event_time, iel.received_at AS received_at, " +
                "iel.source AS source, " +
                "JSONExtractRaw(iel.raw_payload, 'data') AS data, " +
                "COALESCE(cel.processing_status, iel.status) AS processing_status, " +
                "iel.facility_id AS facility_id, " +
                "'' AS protocol_instance_id, '' AS protocol_definition_id, " +
                "'' AS action_id, '' AS matched_step_instance_id " +
                "FROM inbound_event_logs iel" + finalClause() + " " +
                "LEFT JOIN compliance_event_logs cel" + finalClause() + " ON cel.cloudevents_id = iel.cloudevents_id " +
                "WHERE iel.subject = :sub ORDER BY iel.event_time DESC",
                Map.of("sub", subject), rowMapperJoined());
    }

    @Override
    public List<ComplianceEventLog> findByComplianceEventIds(List<UUID> complianceEventIds) {
        if (complianceEventIds == null || complianceEventIds.isEmpty()) return List.of();
        List<String> ids = complianceEventIds.stream().map(UUID::toString).toList();
        return jdbc.query(
                "SELECT id, cloudevents_id, " +
                "'' AS subject, '' AS event_type, " +
                "received_at AS event_time, received_at AS received_at, " +
                "source, " +
                "data, " +
                "processing_status, '' AS facility_id, " +
                "'' AS protocol_instance_id, '' AS protocol_definition_id, " +
                "'' AS action_id, '' AS matched_step_instance_id " +
                "FROM compliance_event_logs" + finalClause() + " " +
                "WHERE id IN (:ids)",
                Map.of("ids", ids), rowMapperJoined());
    }

    @Override
    public List<String> findDistinctFacilityIds() {
        return jdbc.queryForList(
                "SELECT DISTINCT facility_id FROM inbound_event_logs" + finalClause() +
                " WHERE facility_id != '' ORDER BY facility_id",
                Map.of(), String.class);
    }

    @Override
    public List<Object[]> findFacilityNames() {
        return jdbc.query(
                "SELECT facility_id, " +
                "COALESCE(NULLIF(anyIf(facility_name, facility_name != ''), ''), facility_id) AS facility_name " +
                "FROM ( " +
                "  SELECT facility_id, " +
                "  JSONExtractString(JSONExtractRaw(arrayElement(JSONExtractArrayRaw(JSONExtractRaw(raw_payload, 'data'), 'location'), 1), 'location'), 'display') AS facility_name " +
                "  FROM inbound_event_logs" + finalClause() + " WHERE resource_type = 'Encounter' AND facility_id != '' " +
                "  UNION ALL " +
                "  SELECT facility_id, " +
                "  JSONExtractString(arrayElement(JSONExtractArrayRaw(JSONExtractRaw(raw_payload, 'data'), 'locationReference'), 1), 'display') AS facility_name " +
                "  FROM inbound_event_logs" + finalClause() + " WHERE resource_type = 'ServiceRequest' AND facility_id != '' " +
                ") GROUP BY facility_id ORDER BY facility_id",
                new MapSqlParameterSource(),
                (rs, n) -> new Object[]{rs.getString(1), rs.getString(2)});
    }

    @Override
    public List<String> findDistinctPractitioners() {
        return jdbc.queryForList(
                "SELECT DISTINCT practitioner_ref FROM inbound_event_logs" + finalClause() +
                " WHERE practitioner_ref != '' ORDER BY practitioner_ref",
                Map.of(), String.class);
    }

    @Override
    public List<Object[]> countByResourceType(String facilityId, String source,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("src", str(source))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT iel.resource_type, count() AS cnt " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "WHERE cel.processing_status != 'DUPLICATE' " +
                "AND iel.resource_type != '' " +
                "AND (:fid = '' OR iel.facility_id = :fid) " +
                "AND (:src = '' OR iel.source = :src) " +
                "AND cel.received_at >= parseDateTime64BestEffort(:s) " +
                "AND cel.received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY iel.resource_type ORDER BY cnt DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> countByFacility(OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT iel.facility_id, iel.resource_type, count() AS cnt " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "WHERE cel.processing_status != 'DUPLICATE' " +
                "AND iel.facility_id != '' " +
                "AND cel.received_at >= parseDateTime64BestEffort(:s) " +
                "AND cel.received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY iel.facility_id, iel.resource_type " +
                "ORDER BY iel.facility_id, cnt DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> countByPractitioner(String facilityId,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT iel.practitioner_ref, iel.practitioner_display, iel.resource_type, count() AS cnt " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "WHERE cel.processing_status != 'DUPLICATE' " +
                "AND iel.practitioner_ref != '' " +
                "AND (:fid = '' OR iel.facility_id = :fid) " +
                "AND cel.received_at >= parseDateTime64BestEffort(:s) " +
                "AND cel.received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY iel.practitioner_ref, iel.practitioner_display, iel.resource_type ORDER BY cnt DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)});
    }

    @Override
    public List<Object[]> countBySource(String facilityId,
                                         OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT iel.source, iel.resource_type, count() AS cnt " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "WHERE cel.processing_status != 'DUPLICATE' " +
                "AND iel.source != '' " +
                "AND (:fid = '' OR iel.facility_id = :fid) " +
                "AND cel.received_at >= parseDateTime64BestEffort(:s) " +
                "AND cel.received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY iel.source, iel.resource_type ORDER BY cnt DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> findEventTrends(String interval, String facilityId, String source,
                                           String resourceType,
                                           OffsetDateTime startDate, OffsetDateTime endDate) {
        String periodExpr = dateTruncExpr(interval, "cel.received_at");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("src", str(source))
                .addValue("rt", str(resourceType))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT " + periodExpr + " AS period, iel.resource_type, count() AS event_count " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "WHERE cel.processing_status != 'DUPLICATE' " +
                "AND (:fid = '' OR iel.facility_id = :fid) " +
                "AND (:src = '' OR iel.source = :src) " +
                "AND (:rt = '' OR iel.resource_type = :rt) " +
                "AND cel.received_at >= parseDateTime64BestEffort(:s) " +
                "AND cel.received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY period, iel.resource_type ORDER BY period",
                p, (rs, n) -> new Object[]{rs.getObject(1), rs.getString(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> countByProcessingStatus(String facilityId,
                                                    OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT cel.processing_status, count() " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "LEFT JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "WHERE (:fid = '' OR iel.facility_id = :fid) " +
                "AND cel.received_at >= parseDateTime64BestEffort(:s) " +
                "AND cel.received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY cel.processing_status",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> findProcessingQualityBySource(String source, String facilityId,
                                                          OffsetDateTime startDate,
                                                          OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("src", str(source))
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT iel.source, cel.processing_status, count() AS cnt " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "WHERE iel.source != '' " +
                "AND (:src = '' OR iel.source = :src) " +
                "AND (:fid = '' OR iel.facility_id = :fid) " +
                "AND cel.received_at >= parseDateTime64BestEffort(:s) " +
                "AND cel.received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY iel.source, cel.processing_status ORDER BY cnt DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> findFacilityEventCounts(UUID protocolDefId) {
        String pid = protocolDefId != null ? protocolDefId.toString() : "";
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("pid", pid);
        return jdbc.query(
                "SELECT iel.facility_id, uniqExact(pi.id) AS total_enrollments, count() AS total_events " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "LEFT JOIN protocol_instances pi" + finalClause() + " ON pi.patient_id = iel.subject " +
                "WHERE iel.facility_id != '' " +
                "AND (toUUIDOrNull(:pid) IS NULL OR pi.protocol_definition_id = toUUIDOrNull(:pid)) " +
                "GROUP BY iel.facility_id ORDER BY total_events DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> findActivePatientsByFacility(UUID protocolDefId) {
        String pid = protocolDefId != null ? protocolDefId.toString() : "";
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("pid", pid);
        return jdbc.query(
                "SELECT iel.facility_id, uniq(pi.patient_id) AS patient_count " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "JOIN protocol_instances pi" + finalClause() + " ON pi.patient_id = iel.subject " +
                "WHERE pi.status = 'ACTIVE' AND iel.facility_id != '' " +
                "AND (toUUIDOrNull(:pid) IS NULL OR pi.protocol_definition_id = toUUIDOrNull(:pid)) " +
                "GROUP BY iel.facility_id ORDER BY patient_count DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> findFacilityPatientMapping() {
        return jdbc.query(
                "SELECT DISTINCT iel.facility_id, pi.patient_id " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "JOIN protocol_instances pi" + finalClause() + " ON pi.patient_id = iel.subject " +
                "WHERE iel.facility_id != '' " +
                "ORDER BY iel.facility_id, pi.patient_id",
                new MapSqlParameterSource(),
                (rs, n) -> new Object[]{rs.getString(1), rs.getString(2)});
    }

    @Override
    public List<Object[]> findPatientsByFacility(String facilityId) {
        return jdbc.query(
                "SELECT DISTINCT iel.facility_id, pi.patient_id, pi.id AS protocol_instance_id " +
                "FROM inbound_event_logs iel" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON pi.patient_id = iel.subject " +
                "WHERE iel.facility_id = :fid AND iel.facility_id != '' " +
                "ORDER BY pi.patient_id",
                Map.of("fid", facilityId),
                (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), parseUUID(rs.getString(3))});
    }

    @Override
    public List<Object[]> findPractitionerSummary() {
        return jdbc.query(
                "SELECT iel.practitioner_ref, iel.practitioner_display, " +
                "any(iel.facility_id) AS facility_id, " +
                "count() AS event_count, uniq(iel.subject) AS patient_count " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "WHERE iel.practitioner_ref != '' " +
                "AND cel.processing_status != 'DUPLICATE' " +
                "GROUP BY iel.practitioner_ref, iel.practitioner_display " +
                "ORDER BY event_count DESC",
                new MapSqlParameterSource(),
                (rs, n) -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5)});
    }

    @Override
    public List<Object[]> findPractitionerSummaryFiltered(OffsetDateTime startDate,
                                                            OffsetDateTime endDate,
                                                            String facilityId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate))
                .addValue("fid", str(facilityId));
        return jdbc.query(
                "SELECT iel.practitioner_ref, iel.practitioner_display, " +
                "any(iel.facility_id) AS facility_id, " +
                "count() AS event_count, uniq(iel.subject) AS patient_count " +
                "FROM compliance_event_logs cel" + finalClause() + " " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.cloudevents_id = cel.cloudevents_id " +
                "WHERE iel.practitioner_ref != '' " +
                "AND cel.processing_status != 'DUPLICATE' " +
                "AND iel.received_at >= parseDateTime64BestEffort(:s) " +
                "AND iel.received_at <= parseDateTime64BestEffort(:e) " +
                "AND (:fid = '' OR iel.facility_id = :fid) " +
                "GROUP BY iel.practitioner_ref, iel.practitioner_display " +
                "ORDER BY event_count DESC",
                p, (rs, n) -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5)});
    }
}
