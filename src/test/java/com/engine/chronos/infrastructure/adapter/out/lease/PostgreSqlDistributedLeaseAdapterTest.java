package com.engine.chronos.infrastructure.adapter.out.lease;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PostgreSqlDistributedLeaseAdapter.class)
class PostgreSqlDistributedLeaseAdapterTest {

    @Autowired
    private PostgreSqlDistributedLeaseAdapter leaseAdapter;

    @Test
    @DisplayName("Acquiring an unallocated partition succeeds")
    void shouldAcquireFreePartition() {
        boolean acquired = leaseAdapter.acquirePartition(10, "node-alpha", Duration.ofSeconds(10));
        assertThat(acquired).isTrue();

        List<Integer> owned = leaseAdapter.getOwnedPartitions("node-alpha");
        assertThat(owned).contains(10);
    }

    @Test
    @DisplayName("Acquiring a partition owned by another active node fails")
    void shouldFailWhenPartitionOwnedByAnotherNode() {
        leaseAdapter.acquirePartition(20, "node-alpha", Duration.ofSeconds(30));

        boolean acquiredByBeta = leaseAdapter.acquirePartition(20, "node-beta", Duration.ofSeconds(30));
        assertThat(acquiredByBeta).isFalse();
    }

    @Test
    @DisplayName("Releasing a partition makes it available for another node")
    void shouldReleasePartition() {
        leaseAdapter.acquirePartition(30, "node-alpha", Duration.ofSeconds(30));
        leaseAdapter.releasePartition(30, "node-alpha");

        boolean acquiredByBeta = leaseAdapter.acquirePartition(30, "node-beta", Duration.ofSeconds(30));
        assertThat(acquiredByBeta).isTrue();
    }
}
