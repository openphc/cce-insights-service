package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.openphc.cce.insights.domain.entity.DestinationAdaptorMapping;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import static org.openphc.cce.insights.jooq.Tables.DESTINATION_ADAPTOR_MAPPING;

@Repository
public class DestinationAdaptorMappingRepositoryImpl
        extends AbstractClickHouseRepository<DestinationAdaptorMapping, UUID>
        implements DestinationAdaptorMappingRepository {

    public DestinationAdaptorMappingRepositoryImpl(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected String getTableName() {
        return DESTINATION_ADAPTOR_MAPPING.getName();
    }

    @Override
    protected DestinationAdaptorMapping fromRecord(Record r) {
        return DestinationAdaptorMapping.builder()
                .id(r.get(DESTINATION_ADAPTOR_MAPPING.ID.getName(), UUID.class))
                .destination(r.get(DESTINATION_ADAPTOR_MAPPING.DESTINATION.getName(), String.class))
                .receiverAdaptorId(parseUUID(r.get(DESTINATION_ADAPTOR_MAPPING.RECEIVER_ADAPTOR_ID.getName(), String.class)))
                .status(r.get(DESTINATION_ADAPTOR_MAPPING.STATUS.getName(), String.class))
                .createdAt(recordDateTime(r, DESTINATION_ADAPTOR_MAPPING.CREATED_AT.getName()))
                .updatedAt(recordDateTime(r, DESTINATION_ADAPTOR_MAPPING.UPDATED_AT.getName()))
                .build();
    }
}
