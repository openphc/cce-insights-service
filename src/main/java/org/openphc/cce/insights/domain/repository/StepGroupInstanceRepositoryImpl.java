package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.openphc.cce.insights.domain.entity.StepGroupInstance;
import org.openphc.cce.insights.domain.enums.SlaStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.openphc.cce.insights.jooq.Tables.STEP_GROUP_INSTANCES;

@Repository
public class StepGroupInstanceRepositoryImpl
        extends AbstractClickHouseRepository<StepGroupInstance, UUID>
        implements StepGroupInstanceRepository {

    public StepGroupInstanceRepositoryImpl(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected String getTableName() {
        return STEP_GROUP_INSTANCES.getName();
    }

    @Override
    protected StepGroupInstance fromRecord(Record r) {
        return toStepGroupInstance(r);
    }

    @Override
    public List<StepGroupInstance> findByProtocolInstanceIdIn(List<UUID> protocolInstanceIds) {
        if (protocolInstanceIds == null || protocolInstanceIds.isEmpty()) return List.of();
        List<String> ids = protocolInstanceIds.stream().map(UUID::toString).toList();
        var sgi = finalAs(STEP_GROUP_INSTANCES, "sgi");
        List<StepGroupInstance> result = new ArrayList<>();
        for (List<String> chunk : chunkIds(ids)) {
            result.addAll(dsl.select(DSL.asterisk())
                    .from(sgi)
                    .where(DSL.field("sgi." + STEP_GROUP_INSTANCES.PROTOCOL_INSTANCE_ID.getName()).in(chunk))
                    .fetch()
                    .map(this::toStepGroupInstance));
        }
        return result;
    }

    private StepGroupInstance toStepGroupInstance(Record r) {
        // sla_status is '' until the Step SLA Service reaches a verdict, kept as null here.
        SlaStatus slaStatus = null;
        try {
            String sla = r.get(STEP_GROUP_INSTANCES.SLA_STATUS.getName(), String.class);
            if (sla != null && !sla.isEmpty()) slaStatus = SlaStatus.valueOf(sla);
        } catch (Exception ignored) {}

        Integer cycleIndex = r.get(STEP_GROUP_INSTANCES.CYCLE_INDEX.getName(), Integer.class);
        return StepGroupInstance.builder()
                .id(r.get(STEP_GROUP_INSTANCES.ID.getName(), UUID.class))
                .protocolInstanceId(r.get(STEP_GROUP_INSTANCES.PROTOCOL_INSTANCE_ID.getName(), UUID.class))
                .parentGroupInstanceId(parseUUID(r.get(STEP_GROUP_INSTANCES.PARENT_GROUP_INSTANCE_ID.getName(), String.class)))
                .groupActionId(r.get(STEP_GROUP_INSTANCES.GROUP_ACTION_ID.getName(), String.class))
                .cycleIndex(cycleIndex != null ? cycleIndex : 0)
                .dueDate(recordDateTime(r, STEP_GROUP_INSTANCES.DUE_DATE.getName()))
                .slaStatus(slaStatus)
                .openedAt(recordDateTime(r, STEP_GROUP_INSTANCES.OPENED_AT.getName()))
                .completedAt(recordDateTime(r, STEP_GROUP_INSTANCES.COMPLETED_AT.getName()))
                .closedAt(recordDateTime(r, STEP_GROUP_INSTANCES.CLOSED_AT.getName()))
                .build();
    }
}
