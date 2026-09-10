package com.engine.chronos.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataOutboxRepository extends JpaRepository<OutboxTaskJpaEntity, UUID> {

    @Query("SELECT o FROM OutboxTaskJpaEntity o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxTaskJpaEntity> findPending(Pageable pageable);

    long countByStatus(String status);
}
