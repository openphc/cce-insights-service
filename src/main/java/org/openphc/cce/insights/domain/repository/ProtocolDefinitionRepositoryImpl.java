package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProtocolDefinitionRepositoryImpl
        extends AbstractClickHouseRepository<ProtocolDefinition, UUID>
        implements ProtocolDefinitionRepository {

    public ProtocolDefinitionRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    protected String getTableName() {
        return "protocol_definitions";
    }

    @Override
    protected RowMapper<ProtocolDefinition> rowMapper() {
        return (rs, n) -> ProtocolDefinition.builder()
                .id(UUID.fromString(rs.getString("id")))
                .url(rs.getString("url"))
                .version(rs.getString("version"))
                .status(rs.getString("status"))
                .definition(rs.getString("definition"))
                .loadedAt(toOffsetDateTime(rs, "loaded_at"))
                .build();
    }
}
