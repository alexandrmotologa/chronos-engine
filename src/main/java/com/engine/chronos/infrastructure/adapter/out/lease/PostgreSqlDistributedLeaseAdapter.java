package com.engine.chronos.infrastructure.adapter.out.lease;

import com.engine.chronos.domain.port.out.DistributedLeasePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class PostgreSqlDistributedLeaseAdapter implements DistributedLeasePort {

    private final SpringDataPartitionLeaseRepository leaseRepository;

    public PostgreSqlDistributedLeaseAdapter(SpringDataPartitionLeaseRepository leaseRepository) {
        this.leaseRepository = leaseRepository;
    }

    @Override
    @Transactional
    public boolean acquirePartition(int partitionBucket, String nodeOwner, Duration duration) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(duration);

        Optional<PartitionLeaseJpaEntity> existing = leaseRepository.findById(partitionBucket);

        if (existing.isEmpty()) {
            PartitionLeaseJpaEntity entity = new PartitionLeaseJpaEntity(partitionBucket, nodeOwner, now, expiresAt);
            leaseRepository.saveAndFlush(entity);
            return true;
        }

        PartitionLeaseJpaEntity lease = existing.get();
        if (lease.getExpiresAt().isBefore(now) || lease.getNodeOwner().equals(nodeOwner)) {
            lease.setNodeOwner(nodeOwner);
            lease.setAcquiredAt(now);
            lease.setExpiresAt(expiresAt);
            leaseRepository.saveAndFlush(lease);
            return true;
        }

        return false;
    }

    @Override
    @Transactional
    public void renewPartition(int partitionBucket, String nodeOwner, Duration duration) {
        Instant newExpiresAt = Instant.now().plus(duration);
        leaseRepository.renewLease(partitionBucket, nodeOwner, newExpiresAt);
    }

    @Override
    @Transactional
    public void releasePartition(int partitionBucket, String nodeOwner) {
        leaseRepository.releaseLease(partitionBucket, nodeOwner);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> getOwnedPartitions(String nodeOwner) {
        return leaseRepository.findByNodeOwnerAndExpiresAtGreaterThanEqual(nodeOwner, Instant.now())
                .stream()
                .map(PartitionLeaseJpaEntity::getPartitionBucket)
                .toList();
    }
}
