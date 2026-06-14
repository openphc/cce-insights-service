package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.enums.DeviationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DeviationRepositoryImpl
        extends AbstractClickHouseRepository<Deviation, UUID>
        implements DeviationRepository {

    public DeviationRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    protected String getTableName() {
        return "deviations";
    }

    @Override
    protected RowMapper<Deviation> rowMapper() {
        return (rs, n) -> {
            DeviationType dt = null;
            try { dt = DeviationType.valueOf(rs.getString("deviation_type")); } catch (Exception ignored) {}
            return Deviation.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .protocolInstanceId(parseUUID(rs.getString("protocol_instance_id")))
                    .stepInstanceId(parseUUID(rs.getString("step_instance_id")))
                    .deviationType(dt)
                    .detectedAt(toOffsetDateTime(rs, "detected_at"))
                    .metadata(rs.getString("metadata"))
                    .intelligenceEventId(parseUUID(rs.getString("intelligence_event_id")))
                    .build();
        };
    }

    @Override
    public List<Deviation> findByProtocolInstanceId(UUID protocolInstanceId) {
        return jdbc.query(
                "SELECT * FROM deviations" + finalClause() + " WHERE protocol_instance_id = toUUID(:id)",
                Map.of("id", protocolInstanceId.toString()), rowMapper());
    }

    @Override
    public Page<Deviation> findByDeviationType(DeviationType type, Pageable pageable) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("type", type.name())
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<Deviation> content = jdbc.query(
                "SELECT * FROM deviations" + finalClause() + " WHERE deviation_type = :type " +
                "ORDER BY detected_at DESC LIMIT :limit OFFSET :offset",
                p, rowMapper());
        Long total = jdbc.queryForObject(
                "SELECT count() FROM deviations" + finalClause() + " WHERE deviation_type = :type",
                Map.of("type", type.name()), Long.class);
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Object[]> findFilteredDeviations(String deviationType, String facilityId,
                                                  OffsetDateTime startDate, OffsetDateTime endDate,
                                                  int lim) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("dtype", str(deviationType))
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate))
                .addValue("lim", lim);
        return jdbc.query(
                "SELECT d.id, pi.patient_id, d.protocol_instance_id, pi.protocol_canonical, " +
                "d.step_instance_id, si.action_id, d.deviation_type, d.detected_at, " +
                "pf.facility_id " +
                "FROM deviations d" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON d.protocol_instance_id = pi.id " +
                "JOIN step_instances si" + finalClause() + " ON d.step_instance_id = si.id " +
                "LEFT JOIN mv_patient_facility_latest pf ON pf.patient_id = pi.patient_id " +
                "WHERE (:dtype = '' OR d.deviation_type = :dtype) " +
                "AND (:fid = '' OR pf.facility_id = :fid) " +
                "AND d.detected_at >= parseDateTime64BestEffort(:s) " +
                "AND d.detected_at <= parseDateTime64BestEffort(:e) " +
                "ORDER BY d.detected_at DESC LIMIT :lim",
                p, (rs, n) -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7),
                        toOffsetDateTime(rs, "detected_at"), rs.getString(9)});
    }

    @Override
    public List<Object[]> findDeviationTrends(String interval, OffsetDateTime startDate,
                                               OffsetDateTime endDate, String facilityId,
                                               String actionId) {
        String periodExpr = dateTruncExpr(interval, "d.detected_at");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate))
                .addValue("fid", str(facilityId))
                .addValue("aid", str(actionId));
        return jdbc.query(
                "SELECT " + periodExpr + " AS period, d.deviation_type, count() AS cnt " +
                "FROM deviations d" + finalClause() + " " +
                "JOIN step_instances si" + finalClause() + " ON d.step_instance_id = si.id " +
                "LEFT JOIN protocol_instances pi" + finalClause() + " ON d.protocol_instance_id = pi.id " +
                "LEFT JOIN mv_patient_facility_latest pf ON pf.patient_id = pi.patient_id " +
                "WHERE d.detected_at >= parseDateTime64BestEffort(:s) " +
                "AND d.detected_at <= parseDateTime64BestEffort(:e) " +
                "AND (:fid = '' OR pf.facility_id = :fid) " +
                "AND (:aid = '' OR si.action_id = :aid) " +
                "GROUP BY period, d.deviation_type ORDER BY period",
                p, (rs, n) -> new Object[]{rs.getObject(1), rs.getString(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> findDeviationsByAction(UUID protocolDefId, OffsetDateTime startDate,
                                                  OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("pid", uuid(protocolDefId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT si.action_id, pi.protocol_definition_id, pi.protocol_canonical, " +
                "count() AS total_deviations, " +
                "countIf(d.deviation_type = 'OVERDUE') AS overdue_count, " +
                "countIf(d.deviation_type = 'MISSED') AS missed_count, " +
                "countIf(d.deviation_type = 'ORDER_VIOLATION') AS order_violation_count, " +
                "uniq(pi.patient_id) AS affected_patients " +
                "FROM deviations d" + finalClause() + " " +
                "JOIN step_instances si" + finalClause() + " ON d.step_instance_id = si.id " +
                "JOIN protocol_instances pi" + finalClause() + " ON d.protocol_instance_id = pi.id " +
                "WHERE (toUUIDOrNull(:pid) IS NULL OR pi.protocol_definition_id = toUUIDOrNull(:pid)) " +
                "AND d.detected_at >= parseDateTime64BestEffort(:s) " +
                "AND d.detected_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY si.action_id, pi.protocol_definition_id, pi.protocol_canonical " +
                "ORDER BY total_deviations DESC",
                p, (rs, n) -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8)});
    }

    @Override
    public List<Object[]> findResolutionRate(UUID protocolDefId, OffsetDateTime startDate,
                                             OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("pid", uuid(protocolDefId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT " +
                "countIf(si.state = 'COMPLETED') AS resolved_count, " +
                "countIf(si.state = 'MISSED') AS escalated_count, " +
                "count() AS total_overdue, " +
                "avgIf(dateDiff('second', d.detected_at, si.completed_at) / 86400.0, " +
                "      si.state = 'COMPLETED') AS avg_days_to_resolve " +
                "FROM deviations d" + finalClause() + " " +
                "JOIN step_instances si" + finalClause() + " ON d.step_instance_id = si.id " +
                "WHERE d.deviation_type = 'OVERDUE' " +
                "AND (toUUIDOrNull(:pid) IS NULL OR d.protocol_instance_id IN (" +
                "  SELECT id FROM protocol_instances" + finalClause() +
                "  WHERE protocol_definition_id = toUUIDOrNull(:pid))) " +
                "AND d.detected_at >= parseDateTime64BestEffort(:s) " +
                "AND d.detected_at <= parseDateTime64BestEffort(:e)",
                p, (rs, n) -> new Object[]{
                        rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getDouble(4)});
    }

    @Override
    public List<Object[]> countByTypeSince(OffsetDateTime since) {
        return jdbc.query(
                "SELECT deviation_type, count() FROM deviations" + finalClause() +
                " WHERE detected_at >= parseDateTime64BestEffort(:since) " +
                "GROUP BY deviation_type",
                Map.of("since", dt(since)),
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> countByTypeInRange(OffsetDateTime startDate, OffsetDateTime endDate,
                                             String facilityId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate))
                .addValue("fid", str(facilityId));
        return jdbc.query(
                "SELECT d.deviation_type, count() " +
                "FROM deviations d" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON d.protocol_instance_id = pi.id " +
                "LEFT JOIN mv_patient_facility_latest pf ON pf.patient_id = pi.patient_id " +
                "WHERE d.detected_at >= parseDateTime64BestEffort(:s) " +
                "AND d.detected_at <= parseDateTime64BestEffort(:e) " +
                "AND (:fid = '' OR pf.facility_id = :fid) " +
                "GROUP BY d.deviation_type",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> findRepeatDeviationPatients(int minDeviations, String facilityId,
                                                       OffsetDateTime startDate,
                                                       OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate))
                .addValue("min", minDeviations);
        return jdbc.query(
                "SELECT pi.patient_id, count() AS total_deviations, " +
                "countIf(d.deviation_type = 'OVERDUE') AS overdue_count, " +
                "countIf(d.deviation_type = 'MISSED') AS missed_count, " +
                "countIf(d.deviation_type = 'ORDER_VIOLATION') AS order_violation_count, " +
                "uniq(pi.id) AS affected_protocols, " +
                "uniq(d.step_instance_id) AS affected_steps " +
                "FROM deviations d" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON d.protocol_instance_id = pi.id " +
                "LEFT JOIN mv_patient_facility_latest pf ON pf.patient_id = pi.patient_id " +
                "WHERE (:fid = '' OR pf.facility_id = :fid) " +
                "AND d.detected_at >= parseDateTime64BestEffort(:s) " +
                "AND d.detected_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY pi.patient_id " +
                "HAVING count() >= :min " +
                "ORDER BY total_deviations DESC",
                p, (rs, n) -> new Object[]{
                        rs.getString(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)});
    }

    @Override
    public List<Object[]> countDeviationsByFacility() {
        return jdbc.query(
                "SELECT iel.facility_id, uniq(d.id) AS deviation_count " +
                "FROM deviations d" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON d.protocol_instance_id = pi.id " +
                "JOIN inbound_event_logs iel" + finalClause() + " ON iel.subject = pi.patient_id " +
                "WHERE iel.facility_id != '' " +
                "GROUP BY iel.facility_id",
                new MapSqlParameterSource(),
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public long countDistinctPatientsWithDeviations() {
        Long r = jdbc.queryForObject(
                "SELECT uniq(pi.patient_id) " +
                "FROM deviations d" + finalClause() + " " +
                "JOIN protocol_instances pi" + finalClause() + " ON d.protocol_instance_id = pi.id",
                new MapSqlParameterSource(), Long.class);
        return r != null ? r : 0L;
    }
}
