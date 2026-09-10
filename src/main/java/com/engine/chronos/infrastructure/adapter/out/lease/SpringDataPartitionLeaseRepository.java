package com.engine.chronos.infrastructure.adapter.out.lease;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SpringDataPartitionLeaseRepository extends JpaRepository<PartitionLeaseJpaEntity, Integer> {

    List<PartitionLeaseJpaEntity> findByNodeOwnerAndExpiresAtGreaterThanEqual(String nodeOwner, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PartitionLeaseJpaEntity p SET p.expiresAt = :newExpiresAt WHERE p.partitionBucket = :partitionBucket AND p.nodeOwner = :nodeOwner")
    int renewLease(
            @Param("partitionBucket") int partitionBucket,
            @Param("nodeOwner") String nodeOwner,
            @Param("newExpiresAt") Instant newExpiresAt
    );

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PartitionLeaseJpaEntity p WHERE p.partitionBucket = :partitionBucket AND p.nodeOwner = :nodeOwner")
    int releaseLease(
            @Param("partitionBucket") int partitionBucket,
            @Param("nodeOwner") String nodeOwner
    );
}
