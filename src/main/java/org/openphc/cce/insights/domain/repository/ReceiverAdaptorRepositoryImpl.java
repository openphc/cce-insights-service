package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.openphc.cce.insights.domain.entity.ReceiverAdaptor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.openphc.cce.insights.jooq.Tables.RECEIVER_ADAPTOR;

@Repository
public class ReceiverAdaptorRepositoryImpl
        extends AbstractClickHouseRepository<ReceiverAdaptor, UUID>
        implements ReceiverAdaptorRepository {

    public ReceiverAdaptorRepositoryImpl(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected String getTableName() {
        return RECEIVER_ADAPTOR.getName();
    }

    @Override
    protected ReceiverAdaptor fromRecord(Record r) {
        String endpointUrl = null;
        try { endpointUrl = r.get(RECEIVER_ADAPTOR.ENDPOINT_URL.getName(), String.class); } catch (Exception ignored) {}
        return ReceiverAdaptor.builder()
                .id(r.get(RECEIVER_ADAPTOR.ID.getName(), UUID.class))
                .name(r.get(RECEIVER_ADAPTOR.NAME.getName(), String.class))
                .definition(r.get(RECEIVER_ADAPTOR.DEFINITION.getName(), String.class))
                .config(r.get(RECEIVER_ADAPTOR.CONFIG.getName(), String.class))
                .endpointUrl(endpointUrl)
                .status(r.get(RECEIVER_ADAPTOR.STATUS.getName(), String.class))
                .createdAt(recordDateTime(r, RECEIVER_ADAPTOR.CREATED_AT.getName()))
                .updatedAt(recordDateTime(r, RECEIVER_ADAPTOR.UPDATED_AT.getName()))
                .build();
    }

    // endpoint_url is a MATERIALIZED column excluded from SELECT *; must be selected explicitly.
    @Override
    public List<ReceiverAdaptor> findAll() {
        return dsl.select(
                    DSL.field(RECEIVER_ADAPTOR.ID.getName()),
                    DSL.field(RECEIVER_ADAPTOR.NAME.getName()),
                    DSL.field(RECEIVER_ADAPTOR.DEFINITION.getName()),
                    DSL.field(RECEIVER_ADAPTOR.CONFIG.getName()),
                    DSL.field(RECEIVER_ADAPTOR.STATUS.getName()),
                    DSL.field(RECEIVER_ADAPTOR.CREATED_AT.getName()),
                    DSL.field(RECEIVER_ADAPTOR.UPDATED_AT.getName()),
                    DSL.field(RECEIVER_ADAPTOR.ENDPOINT_URL.getName()))
                  .from(DSL.table(DSL.sql(getTableName() + finalClause())))
                  .fetch()
                  .map(this::fromRecord);
    }

    @Override
    public Optional<ReceiverAdaptor> findById(UUID id) {
        return dsl.select(
                    DSL.field(RECEIVER_ADAPTOR.ID.getName()),
                    DSL.field(RECEIVER_ADAPTOR.NAME.getName()),
                    DSL.field(RECEIVER_ADAPTOR.DEFINITION.getName()),
                    DSL.field(RECEIVER_ADAPTOR.CONFIG.getName()),
                    DSL.field(RECEIVER_ADAPTOR.STATUS.getName()),
                    DSL.field(RECEIVER_ADAPTOR.CREATED_AT.getName()),
                    DSL.field(RECEIVER_ADAPTOR.UPDATED_AT.getName()),
                    DSL.field(RECEIVER_ADAPTOR.ENDPOINT_URL.getName()))
                  .from(DSL.table(DSL.sql(getTableName() + finalClause())))
                  .where(DSL.field(RECEIVER_ADAPTOR.ID.getName()).eq(id.toString()))
                  .limit(1)
                  .fetch()
                  .map(this::fromRecord)
                  .stream().findFirst();
    }
}
