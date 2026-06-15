package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.InboundEvent;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class InboundEventRepositoryImpl
        extends AbstractClickHouseRepository<InboundEvent, UUID>
        implements InboundEventRepository {

    public InboundEventRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    protected String getTableName() {
        return "inbound_event_logs";
    }

    @Override
    protected RowMapper<InboundEvent> rowMapper() {
        return (rs, n) -> InboundEvent.builder()
                .id(UUID.fromString(rs.getString("id")))
                .cloudeventsId(rs.getString("cloudevents_id"))
                .source(rs.getString("source"))
                .type(rs.getString("event_type"))
                .subject(rs.getString("subject"))
                .eventTime(toOffsetDateTime(rs, "event_time"))
                .facilityId(rs.getString("facility_id"))
                .correlationId(rs.getString("correlation_id"))
                .rawPayload(rs.getString("raw_payload"))
                .status(rs.getString("status"))
                .rejectionReason(rs.getString("rejection_reason"))
                .errorDetails(rs.getString("error_details"))
                .receivedAt(toOffsetDateTime(rs, "received_at"))
                .build();
    }

    @Override
    public List<String> findDistinctSources() {
        return jdbc.queryForList(
                "SELECT DISTINCT source FROM inbound_event_logs" + finalClause() +
                " WHERE source != '' ORDER BY source",
                Map.of(), String.class);
    }

    @Override
    public long countDistinctPatientSubjectsBySource(String source, String facilityId,
                                                      OffsetDateTime startDate,
                                                      OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("src", str(source))
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        Long r = jdbc.queryForObject(
                "SELECT uniq(subject) FROM inbound_event_logs" + finalClause() +
                " WHERE status = 'ACCEPTED' AND subject != '' " +
                "AND (:src = '' OR source = :src) " +
                "AND (:fid = '' OR facility_id = :fid) " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e)",
                p, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countDistinctActiveFacilities(OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        Long r = jdbc.queryForObject(
                "SELECT uniq(facility_id) FROM inbound_event_logs" + finalClause() +
                " WHERE status = 'ACCEPTED' AND facility_id != '' " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e)",
                p, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countEventsBySource(String source, String facilityId,
                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("src", str(source))
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        Long r = jdbc.queryForObject(
                "SELECT count() FROM inbound_event_logs" + finalClause() +
                " WHERE (:src = '' OR source = :src) " +
                "AND (:fid = '' OR facility_id = :fid) " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e)",
                p, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public List<Object[]> countDistinctPatientsBySourceGroupedByFacility(String source,
                                                                          OffsetDateTime startDate,
                                                                          OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("src", str(source))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT facility_id, uniq(subject) AS patient_count " +
                "FROM inbound_event_logs" + finalClause() +
                " WHERE status = 'ACCEPTED' AND facility_id != '' AND subject != '' " +
                "AND (:src = '' OR source = :src) " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY facility_id ORDER BY patient_count DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> countByStatus(String facilityId, String source,
                                         OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("src", str(source))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT status, count() FROM inbound_event_logs" + finalClause() +
                " WHERE (:fid = '' OR facility_id = :fid) " +
                "AND (:src = '' OR source = :src) " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY status",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> countByRejectionReason(String facilityId, String source,
                                                   OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("src", str(source))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT rejection_reason, count() FROM inbound_event_logs" + finalClause() +
                " WHERE status = 'REJECTED' AND rejection_reason != '' " +
                "AND (:fid = '' OR facility_id = :fid) " +
                "AND (:src = '' OR source = :src) " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY rejection_reason ORDER BY count() DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> findIngestionTrends(String interval, String facilityId, String source,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        String periodExpr = dateTruncExpr(interval, "received_at");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("src", str(source))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT " + periodExpr + " AS period, status, count() AS cnt " +
                "FROM inbound_event_logs" + finalClause() +
                " WHERE (:fid = '' OR facility_id = :fid) " +
                "AND (:src = '' OR source = :src) " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY period, status ORDER BY period",
                p, (rs, n) -> new Object[]{rs.getObject(1), rs.getString(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> countBySourceAndStatus(String facilityId,
                                                   OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT source, status, count() AS cnt " +
                "FROM inbound_event_logs" + finalClause() +
                " WHERE source != '' " +
                "AND (:fid = '' OR facility_id = :fid) " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY source, status ORDER BY source, cnt DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> countBySourceAndRejectionReason(String facilityId,
                                                           OffsetDateTime startDate,
                                                           OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT source, rejection_reason, count() AS cnt " +
                "FROM inbound_event_logs" + finalClause() +
                " WHERE status = 'REJECTED' AND source != '' AND rejection_reason != '' " +
                "AND (:fid = '' OR facility_id = :fid) " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY source, rejection_reason ORDER BY source, cnt DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> findPipelineLossBySource(String facilityId,
                                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT iel.source, count() AS lost_count " +
                "FROM inbound_event_logs iel" + finalClause() + " " +
                "WHERE iel.status = 'ACCEPTED' " +
                "AND iel.cloudevents_id NOT IN " +
                "  (SELECT cloudevents_id FROM compliance_event_logs" + finalClause() + ") " +
                "AND (:fid = '' OR iel.facility_id = :fid) " +
                "AND iel.received_at >= parseDateTime64BestEffort(:s) " +
                "AND iel.received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY iel.source ORDER BY lost_count DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public long countPipelineLoss(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        Long r = jdbc.queryForObject(
                "SELECT count() FROM inbound_event_logs iel" + finalClause() + " " +
                "WHERE iel.status = 'ACCEPTED' " +
                "AND iel.cloudevents_id NOT IN " +
                "  (SELECT cloudevents_id FROM compliance_event_logs" + finalClause() + ") " +
                "AND (:fid = '' OR iel.facility_id = :fid) " +
                "AND iel.received_at >= parseDateTime64BestEffort(:s) " +
                "AND iel.received_at <= parseDateTime64BestEffort(:e)",
                p, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public long countAccepted(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        Long r = jdbc.queryForObject(
                "SELECT count() FROM inbound_event_logs" + finalClause() +
                " WHERE status = 'ACCEPTED' " +
                "AND (:fid = '' OR facility_id = :fid) " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e)",
                p, Long.class);
        return r != null ? r : 0L;
    }

    @Override
    public List<Object[]> findOverlappingEvents(String sourceA, String sourceB, long windowSeconds,
                                                  String facilityId,
                                                  OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("srcA", sourceA)
                .addValue("srcB", sourceB)
                .addValue("win", windowSeconds)
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT a.resource_type, count() AS event_count " +
                "FROM inbound_event_logs a" + finalClause() + " " +
                "JOIN inbound_event_logs b" + finalClause() + " " +
                "  ON a.subject = b.subject " +
                "  AND a.event_type = b.event_type " +
                "  AND abs(dateDiff('second', a.event_time, b.event_time)) <= :win " +
                "WHERE a.source = :srcA AND b.source = :srcB " +
                "AND a.status != 'DUPLICATE' AND b.status != 'DUPLICATE' " +
                "AND a.event_time IS NOT NULL AND b.event_time IS NOT NULL " +
                "AND (:fid = '' OR a.facility_id = :fid) " +
                "AND a.received_at >= parseDateTime64BestEffort(:s) " +
                "AND a.received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY a.resource_type ORDER BY event_count DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> findUniqueToSource(String source, String otherSource, long windowSeconds,
                                               String facilityId,
                                               OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("src", source)
                .addValue("other", otherSource)
                .addValue("win", windowSeconds)
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT a.resource_type, count() AS event_count " +
                "FROM inbound_event_logs a" + finalClause() + " " +
                "WHERE a.source = :src " +
                "AND a.status != 'DUPLICATE' " +
                "AND a.event_time IS NOT NULL " +
                "AND (:fid = '' OR a.facility_id = :fid) " +
                "AND a.received_at >= parseDateTime64BestEffort(:s) " +
                "AND a.received_at <= parseDateTime64BestEffort(:e) " +
                "AND NOT EXISTS (" +
                "  SELECT 1 FROM inbound_event_logs b" + finalClause() + " " +
                "  WHERE b.source = :other " +
                "  AND b.status != 'DUPLICATE' " +
                "  AND b.subject = a.subject " +
                "  AND b.event_type = a.event_type " +
                "  AND b.event_time IS NOT NULL " +
                "  AND abs(dateDiff('second', a.event_time, b.event_time)) <= :win) " +
                "GROUP BY a.resource_type ORDER BY event_count DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    @Override
    public List<Object[]> findOverlappingEventSamples(String sourceA, String sourceB, long windowSeconds,
                                                       String facilityId,
                                                       OffsetDateTime startDate, OffsetDateTime endDate,
                                                       int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("srcA", sourceA)
                .addValue("srcB", sourceB)
                .addValue("win", windowSeconds)
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate))
                .addValue("lim", limit);
        return jdbc.query(
                "SELECT a.id AS event_a_id, b.id AS event_b_id, " +
                "a.subject, a.resource_type, " +
                "a.event_time AS time_a, b.event_time AS time_b, " +
                "abs(dateDiff('second', a.event_time, b.event_time)) AS diff_seconds " +
                "FROM inbound_event_logs a" + finalClause() + " " +
                "JOIN inbound_event_logs b" + finalClause() + " " +
                "  ON a.subject = b.subject " +
                "  AND a.event_type = b.event_type " +
                "  AND abs(dateDiff('second', a.event_time, b.event_time)) <= :win " +
                "WHERE a.source = :srcA AND b.source = :srcB " +
                "AND a.status != 'DUPLICATE' AND b.status != 'DUPLICATE' " +
                "AND a.event_time IS NOT NULL AND b.event_time IS NOT NULL " +
                "AND (:fid = '' OR a.facility_id = :fid) " +
                "AND a.received_at >= parseDateTime64BestEffort(:s) " +
                "AND a.received_at <= parseDateTime64BestEffort(:e) " +
                "ORDER BY a.event_time DESC LIMIT :lim",
                p, (rs, n) -> new Object[]{
                        parseUUID(rs.getString(1)), parseUUID(rs.getString(2)),
                        rs.getString(3), rs.getString(4),
                        toOffsetDateTime(rs, "time_a"), toOffsetDateTime(rs, "time_b"),
                        rs.getDouble(7)});
    }

    @Override
    public List<Object[]> findEventTrends(String interval, String facilityId, String source,
                                           OffsetDateTime startDate, OffsetDateTime endDate) {
        String periodExpr = dateTruncExpr(interval, "received_at");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("src", str(source))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT " + periodExpr + " AS period, resource_type, count() AS event_count " +
                "FROM inbound_event_logs" + finalClause() +
                " WHERE status != 'DUPLICATE' AND resource_type != '' " +
                "AND (:fid = '' OR facility_id = :fid) " +
                "AND (:src = '' OR source = :src) " +
                "AND event_time >= parseDateTime64BestEffort(:s) " +
                "AND event_time <= parseDateTime64BestEffort(:e) " +
                "GROUP BY period, resource_type ORDER BY period",
                p, (rs, n) -> new Object[]{rs.getObject(1), rs.getString(2), rs.getLong(3)});
    }

    @Override
    public List<Object[]> countBySource(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fid", str(facilityId))
                .addValue("s", dtStart(startDate))
                .addValue("e", dtEnd(endDate));
        return jdbc.query(
                "SELECT source, count() AS cnt " +
                "FROM inbound_event_logs" + finalClause() +
                " WHERE source != '' " +
                "AND (:fid = '' OR facility_id = :fid) " +
                "AND received_at >= parseDateTime64BestEffort(:s) " +
                "AND received_at <= parseDateTime64BestEffort(:e) " +
                "GROUP BY source ORDER BY cnt DESC",
                p, (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)});
    }
}
