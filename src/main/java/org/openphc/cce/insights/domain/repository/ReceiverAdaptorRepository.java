package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.ReceiverAdaptor;

import java.util.List;
import java.util.UUID;

public interface ReceiverAdaptorRepository extends ReadOnlyRepository<ReceiverAdaptor, UUID> {

    List<ReceiverAdaptor> findAll();
}
