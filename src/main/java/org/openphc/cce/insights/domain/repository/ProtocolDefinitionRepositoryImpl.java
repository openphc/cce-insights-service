package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import static org.openphc.cce.insights.jooq.Tables.PROTOCOL_DEFINITIONS;

@Repository
public class ProtocolDefinitionRepositoryImpl
        extends AbstractClickHouseRepository<ProtocolDefinition, UUID>
        implements ProtocolDefinitionRepository {

    public ProtocolDefinitionRepositoryImpl(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected String getTableName() {
        return PROTOCOL_DEFINITIONS.getName();
    }

    @Override
    protected ProtocolDefinition fromRecord(Record r) {
        return ProtocolDefinition.builder()
                .id(r.get(PROTOCOL_DEFINITIONS.ID.getName(), UUID.class))
                .url(r.get(PROTOCOL_DEFINITIONS.URL.getName(), String.class))
                .version(r.get(PROTOCOL_DEFINITIONS.VERSION.getName(), String.class))
                .status(r.get(PROTOCOL_DEFINITIONS.STATUS.getName(), String.class))
                .definition(r.get(PROTOCOL_DEFINITIONS.DEFINITION.getName(), String.class))
                .loadedAt(recordDateTime(r, PROTOCOL_DEFINITIONS.LOADED_AT.getName()))
                .build();
    }
}
