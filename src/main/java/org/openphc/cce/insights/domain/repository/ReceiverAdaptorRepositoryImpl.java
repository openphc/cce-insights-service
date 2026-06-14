package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.ReceiverAdaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReceiverAdaptorRepositoryImpl
        extends AbstractClickHouseRepository<ReceiverAdaptor, UUID>
        implements ReceiverAdaptorRepository {

    // endpoint_url is a MATERIALIZED column excluded from SELECT *; must be named explicitly.
    private static final String SELECT_COLS =
            "id, name, definition, config, status, created_at, updated_at, endpoint_url";

    public ReceiverAdaptorRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    protected String getTableName() {
        return "receiver_adaptor";
    }

    @Override
    protected RowMapper<ReceiverAdaptor> rowMapper() {
        return (rs, n) -> ReceiverAdaptor.builder()
                .id(UUID.fromString(rs.getString("id")))
                .name(rs.getString("name"))
                .definition(rs.getString("definition"))
                .config(rs.getString("config"))
                .endpointUrl(tryGetString(rs, "endpoint_url"))
                .status(rs.getString("status"))
                .createdAt(toOffsetDateTime(rs, "created_at"))
                .updatedAt(toOffsetDateTime(rs, "updated_at"))
                .build();
    }

    @Override
    public List<ReceiverAdaptor> findAll() {
        return jdbc.query(
                "SELECT " + SELECT_COLS + " FROM receiver_adaptor" + finalClause(),
                new MapSqlParameterSource(), rowMapper());
    }

    @Override
    public Optional<ReceiverAdaptor> findById(UUID id) {
        List<ReceiverAdaptor> r = jdbc.query(
                "SELECT " + SELECT_COLS + " FROM receiver_adaptor" + finalClause() + " WHERE id = :id LIMIT 1",
                Map.of("id", id.toString()), rowMapper());
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }
}
