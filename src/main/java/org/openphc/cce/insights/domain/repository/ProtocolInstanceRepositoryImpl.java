package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.enums.ProtocolInstanceStatus;
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
public class ProtocolInstanceRepositoryImpl
        extends AbstractClickHouseRepository<ProtocolInstance, UUID>
        implements ProtocolInstanceRepository {

    public ProtocolInstanceRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    protected String getTableName() {
        return "protocol_instances";
    }

    @Override
    protected RowMapper<ProtocolInstance> rowMapper() {
        return (rs, n) -> {
            String statusStr = rs.getString("status");
            ProtocolInstanceStatus status = null;
            try { status = ProtocolInstanceStatus.valueOf(statusStr); } catch (Exception ignored) {}
            return ProtocolInstance.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .protocolDefinitionId(parseUUID(rs.getString("protocol_definition_id")))
                    .patientId(rs.getString("patient_id"))
                    .protocolCanonical(rs.getString("protocol_canonical"))
                    .status(status)
                    .enrolledAt(toOffsetDateTime(rs, "enrolled_at"))
                    .createdAt(toOffsetDateTime(rs, "enrolled_at"))
                    .updatedAt(toOffsetDateTime(rs, "updated_at"))
                    .build();
        };
    }

    @Override
    public List<String> findDistinctPatientIds() {
        return jdbc.queryForList(
                "SELECT DISTINCT patient_id FROM protocol_instances" + finalClause() + " ORDER BY patient_id",
                Map.of(), String.class);
    }

    @Override
    public List<ProtocolInstance> findByPatientId(String patientId) {
        return jdbc.query(
                "SELECT * FROM protocol_instances" + finalClause() + " WHERE patient_id = :pid",
                Map.of("pid", patientId), rowMapper());
    }

    @Override
    public List<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId) {
        return jdbc.query(
                "SELECT * FROM protocol_instances" + finalClause() + " WHERE protocol_definition_id = toUUID(:id)",
                Map.of("id", protocolDefinitionId.toString()), rowMapper());
    }

    @Override
    public Page<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId, Pageable pageable) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", protocolDefinitionId.toString())
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<ProtocolInstance> content = jdbc.query(
                "SELECT * FROM protocol_instances" + finalClause() +
                " WHERE protocol_definition_id = toUUID(:id) " +
                "ORDER BY enrolled_at DESC LIMIT :limit OFFSET :offset",
                p, rowMapper());
        Long total = jdbc.queryForObject(
                "SELECT count() FROM protocol_instances" + finalClause() + " WHERE protocol_definition_id = toUUID(:id)",
                Map.of("id", protocolDefinitionId.toString()), Long.class);
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Object[]> countByProtocolDefinitionIdGroupByStatus(UUID protocolDefId) {
        return jdbc.query(
                "SELECT status, count() FROM protocol_instances" + finalClause() +
                " WHERE protocol_definition_id = toUUID(:id) GROUP BY status",
                Map.of("id", protocolDefId.toString()),
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> findEnrollmentTrends(UUID protocolDefId, String interval,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        String periodExpr = dateTruncExpr(interval, "enrolled_at");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", protocolDefId.toString())
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT " + periodExpr + " AS period, count() AS enrollments " +
                "FROM protocol_instances" + finalClause() +
                " WHERE protocol_definition_id = toUUID(:id) " +
                "AND enrolled_at >= parseDateTime64BestEffort(:s) " +
                "AND enrolled_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY period ORDER BY period",
                p, (rs, n) -> new Object[]{rs.getObject(1), rs.getLong(2)});
    }

    @Override
    public Page<ProtocolInstance> findByProtocolDefinitionIdAndStatus(UUID protocolDefId,
                                                                       ProtocolInstanceStatus status,
                                                                       Pageable pageable) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", protocolDefId.toString())
                .addValue("status", status.name())
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<ProtocolInstance> content = jdbc.query(
                "SELECT * FROM protocol_instances" + finalClause() +
                " WHERE protocol_definition_id = toUUID(:id) AND status = :status " +
                "ORDER BY enrolled_at DESC LIMIT :limit OFFSET :offset",
                p, rowMapper());
        Long total = jdbc.queryForObject(
                "SELECT count() FROM protocol_instances" + finalClause() +
                " WHERE protocol_definition_id = toUUID(:id) AND status = :status",
                Map.of("id", protocolDefId.toString(), "status", status.name()), Long.class);
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<ProtocolInstance> findByProtocolDefinitionIdAndPatientIdContaining(UUID protocolDefId,
                                                                                    String patientId,
                                                                                    Pageable pageable) {
        String pattern = "%" + patientId.toLowerCase() + "%";
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", protocolDefId.toString())
                .addValue("pattern", pattern)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<ProtocolInstance> content = jdbc.query(
                "SELECT * FROM protocol_instances" + finalClause() +
                " WHERE protocol_definition_id = toUUID(:id) " +
                "AND lower(patient_id) LIKE :pattern " +
                "ORDER BY enrolled_at DESC LIMIT :limit OFFSET :offset",
                p, rowMapper());
        Long total = jdbc.queryForObject(
                "SELECT count() FROM protocol_instances" + finalClause() +
                " WHERE protocol_definition_id = toUUID(:id) AND lower(patient_id) LIKE :pattern",
                Map.of("id", protocolDefId.toString(), "pattern", pattern), Long.class);
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
}
