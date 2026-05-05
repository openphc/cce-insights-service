package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.DestinationAdaptorMapping;

import java.util.List;
import java.util.UUID;

public interface DestinationAdaptorMappingRepository extends ReadOnlyRepository<DestinationAdaptorMapping, UUID> {

    List<DestinationAdaptorMapping> findAll();
}
