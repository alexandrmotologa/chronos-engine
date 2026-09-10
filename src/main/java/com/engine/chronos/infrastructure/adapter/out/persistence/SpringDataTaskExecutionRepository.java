package com.engine.chronos.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataTaskExecutionRepository extends JpaRepository<TaskExecutionJpaEntity, UUID> {

    List<TaskExecutionJpaEntity> findByTaskIdOrderByExecutedAtDesc(UUID taskId);
}
