package org.openphc.cce.insights.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Base read-only repository — no save/delete methods.
 * Plain Java interface; JDBC implementations are registered as Spring beans.
 */
public interface ReadOnlyRepository<T, ID> {

    Optional<T> findById(ID id);

    List<T> findAllById(Iterable<ID> ids);

    List<T> findAll();

    Page<T> findAll(Pageable pageable);

    long count();

    boolean existsById(ID id);
}
