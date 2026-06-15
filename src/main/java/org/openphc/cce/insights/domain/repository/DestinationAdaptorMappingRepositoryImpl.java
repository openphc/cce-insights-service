package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.DestinationAdaptorMapping;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class DestinationAdaptorMappingRepositoryImpl
        extends AbstractClickHouseRepository<DestinationAdaptorMapping, UUID>
        implements DestinationAdaptorMappingRepository {

    public DestinationAdaptorMappingRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    protected String getTableName() {
        return "destination_adaptor_mapping";
    }

    @Override
    protected RowMapper<DestinationAdaptorMapping> rowMapper() {
        return (rs, n) -> DestinationAdaptorMapping.builder()
                .id(UUID.fromString(rs.getString("id")))
                .destination(rs.getString("destination"))
                .receiverAdaptorId(parseUUID(rs.getString("receiver_adaptor_id")))
                .status(rs.getString("status"))
                .createdAt(toOffsetDateTime(rs, "created_at"))
                .updatedAt(toOffsetDateTime(rs, "updated_at"))
                .build();
    }
}
