package com.engine.chronos.infrastructure.adapter.out.persistence;

import com.engine.chronos.domain.model.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataTaskRepository extends JpaRepository<TaskJpaEntity, UUID> {

    Optional<TaskJpaEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM TaskJpaEntity t WHERE t.partitionBucket = :partitionBucket AND t.status IN ('SCHEDULED', 'RETRY_PENDING') AND t.scheduledTimeUtc <= :cutoff ORDER BY t.scheduledTimeUtc ASC")
    List<TaskJpaEntity> findDueTasks(
            @Param("partitionBucket") int partitionBucket,
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TaskJpaEntity t SET t.status = com.engine.chronos.domain.model.TaskStatus.SCHEDULED, t.leaseOwner = null, t.leaseExpiresAt = null, t.updatedAt = :now WHERE t.status IN (com.engine.chronos.domain.model.TaskStatus.ACQUIRED, com.engine.chronos.domain.model.TaskStatus.EXECUTING) AND t.leaseExpiresAt < :now")
    int resetExpiredLeases(@Param("now") Instant now);

    Page<TaskJpaEntity> findByStatusOrderByUpdatedAtDesc(TaskStatus status, Pageable pageable);

    long countByStatus(TaskStatus status);

    List<TaskJpaEntity> findByScheduledTimeUtcBetweenOrderByScheduledTimeUtcAsc(
            Instant from,
            Instant to,
            Pageable pageable
    );
}
