package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.IntelligenceDelivery;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class IntelligenceDeliveryRepositoryImpl
        extends AbstractClickHouseRepository<IntelligenceDelivery, UUID>
        implements IntelligenceDeliveryRepository {

    public IntelligenceDeliveryRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    protected String getTableName() {
        return "intelligence_deliveries";
    }

    @Override
    protected RowMapper<IntelligenceDelivery> rowMapper() {
        return (rs, n) -> IntelligenceDelivery.builder()
                .id(UUID.fromString(rs.getString("id")))
                .intelligenceEventId(parseUUID(rs.getString("intelligence_event_id")))
                .actionDefinitionId(parseUUID(rs.getString("action_definition_id")))
                .destinationAdaptorMappingId(parseUUID(rs.getString("destination_adaptor_mapping_id")))
                .actionType(rs.getString("action_type"))
                .status(rs.getString("status"))
                .subject(rs.getString("subject"))
                .protocolCanonical(rs.getString("protocol_canonical"))
                .actionId(rs.getString("action_id"))
                .severity(rs.getString("severity"))
                .destination(rs.getString("destination"))
                .attemptCount(rs.getInt("attempt_count"))
                .createdAt(toOffsetDateTime(rs, "created_at"))
                .updatedAt(toOffsetDateTime(rs, "updated_at"))
                .deliveredAt(toOffsetDateTime(rs, "delivered_at"))
                .build();
    }

    @Override
    public List<Object[]> countByStatus() {
        return jdbc.query(
                "SELECT status, count() FROM intelligence_deliveries" + finalClause() + " GROUP BY status",
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> countByActionType() {
        return jdbc.query(
                "SELECT action_type, count() FROM intelligence_deliveries" + finalClause() + " GROUP BY action_type",
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> countBySeverity() {
        return jdbc.query(
                "SELECT severity, count() FROM intelligence_deliveries" + finalClause() + " GROUP BY severity",
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> countByDestination() {
        return jdbc.query(
                "SELECT destination, count() AS cnt FROM intelligence_deliveries" + finalClause() +
                " GROUP BY destination ORDER BY cnt DESC",
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public long countDelivered() {
        Long r = jdbc.queryForObject(
                "SELECT count() FROM intelligence_deliveries" + finalClause() + " WHERE status = 'DELIVERED'",
                new MapSqlParameterSource(), Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countFailed() {
        Long r = jdbc.queryForObject(
                "SELECT count() FROM intelligence_deliveries" + finalClause() + " WHERE status = 'FAILED'",
                new MapSqlParameterSource(), Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public Double avgDeliveryLatencySeconds() {
        Double r = jdbc.queryForObject(
                "SELECT avg(toFloat64(dateDiff('millisecond', created_at, delivered_at))) / 1000.0 " +
                "FROM intelligence_deliveries" + finalClause() + " WHERE status = 'DELIVERED'",
                new MapSqlParameterSource(), Double.class);
        return r;
    }

    @Override
    public long countFiltered(OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        Long r = jdbc.queryForObject(
                "SELECT count() FROM intelligence_deliveries" + finalClause() +
                " WHERE created_at >= parseDateTime64BestEffort(:s) " +
                "AND created_at <= parseDateTime64BestEffort(:e)",
                p, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countDeliveredFiltered(OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        Long r = jdbc.queryForObject(
                "SELECT count() FROM intelligence_deliveries" + finalClause() + " WHERE status = 'DELIVERED' " +
                "AND created_at >= parseDateTime64BestEffort(:s) " +
                "AND created_at <= parseDateTime64BestEffort(:e)",
                p, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countFailedFiltered(OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        Long r = jdbc.queryForObject(
                "SELECT count() FROM intelligence_deliveries" + finalClause() + " WHERE status = 'FAILED' " +
                "AND created_at >= parseDateTime64BestEffort(:s) " +
                "AND created_at <= parseDateTime64BestEffort(:e)",
                p, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public Double avgDeliveryLatencySecondsFiltered(OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.queryForObject(
                "SELECT avg(toFloat64(dateDiff('millisecond', created_at, delivered_at))) / 1000.0 " +
                "FROM intelligence_deliveries" + finalClause() + " WHERE status = 'DELIVERED' " +
                "AND created_at >= parseDateTime64BestEffort(:s) " +
                "AND created_at <= parseDateTime64BestEffort(:e)",
                p, Double.class);
    }

    @Override
    public List<Object[]> countByStatusFiltered(OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT status, count() FROM intelligence_deliveries" + finalClause() +
                " WHERE created_at >= parseDateTime64BestEffort(:s) " +
                "AND created_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY status",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> countByActionTypeFiltered(OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT action_type, count() FROM intelligence_deliveries" + finalClause() +
                " WHERE created_at >= parseDateTime64BestEffort(:s) " +
                "AND created_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY action_type",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> countBySeverityFiltered(OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT severity, count() FROM intelligence_deliveries" + finalClause() +
                " WHERE created_at >= parseDateTime64BestEffort(:s) " +
                "AND created_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY severity",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> countByDestinationFiltered(OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT destination, count() AS cnt FROM intelligence_deliveries" + finalClause() +
                " WHERE created_at >= parseDateTime64BestEffort(:s) " +
                "AND created_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY destination ORDER BY cnt DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }
}
