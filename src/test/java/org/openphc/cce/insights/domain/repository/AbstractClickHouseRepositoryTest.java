package org.openphc.cce.insights.domain.repository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AbstractClickHouseRepository#chunkIds}, the helper that keeps
 * {@code protocol_instance_id IN (...)} clauses under ClickHouse's max_query_size limit.
 * Wide date-range filters (e.g. a quarter of Patient Compliance data) can produce id lists
 * with thousands of UUIDs; chunkIds splits them so each query stays within a safe bound.
 */
class AbstractClickHouseRepositoryTest {

    private static List<Integer> ids(int count) {
        return IntStream.range(0, count).boxed().collect(Collectors.toList());
    }

    @Test
    void chunkIds_returnsEmptyList_whenInputIsEmpty() {
        assertThat(AbstractClickHouseRepository.chunkIds(List.<Integer>of())).isEmpty();
    }

    @Test
    void chunkIds_returnsSingleChunk_whenUnderLimit() {
        List<List<Integer>> chunks = AbstractClickHouseRepository.chunkIds(ids(5));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).containsExactlyElementsOf(ids(5));
    }

    @Test
    void chunkIds_returnsSingleChunk_whenExactlyAtLimit() {
        List<List<Integer>> chunks = AbstractClickHouseRepository.chunkIds(ids(AbstractClickHouseRepository.MAX_IN_CLAUSE_SIZE));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).hasSize(AbstractClickHouseRepository.MAX_IN_CLAUSE_SIZE);
    }

    @Test
    void chunkIds_splitsIntoMultipleChunks_whenOverLimit() {
        // Mirrors the reported incident: thousands of protocol_instance ids for a wide date range.
        int total = AbstractClickHouseRepository.MAX_IN_CLAUSE_SIZE * 2 + 500;
        List<List<Integer>> chunks = AbstractClickHouseRepository.chunkIds(ids(total));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(AbstractClickHouseRepository.MAX_IN_CLAUSE_SIZE);
        assertThat(chunks.get(1)).hasSize(AbstractClickHouseRepository.MAX_IN_CLAUSE_SIZE);
        assertThat(chunks.get(2)).hasSize(500);
    }

    @Test
    void chunkIds_preservesOrderAndEveryElementExactlyOnce() {
        int total = AbstractClickHouseRepository.MAX_IN_CLAUSE_SIZE + 1;
        List<List<Integer>> chunks = AbstractClickHouseRepository.chunkIds(ids(total));

        List<Integer> flattened = chunks.stream().flatMap(List::stream).collect(Collectors.toList());
        assertThat(flattened).containsExactlyElementsOf(ids(total));
    }
}
