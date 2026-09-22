package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
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

    /**
     * protocol_definitions is a ReplacingMergeTree, and the global {@code cce.clickhouse.use-final}
     * toggle defaults to false in application.yml (perf tradeoff for high-volume tables) - which
     * left pre-/post-update rows for the same protocol id both visible until ClickHouse's background
     * merge ran, showing duplicate entries in the UI's protocol filter. This table is small and rarely
     * written, so always force FINAL here regardless of the global flag.
     */
    @Override
    protected Table<?> baseTable() {
        return DSL.table(DSL.sql(getTableName() + " FINAL"));
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
