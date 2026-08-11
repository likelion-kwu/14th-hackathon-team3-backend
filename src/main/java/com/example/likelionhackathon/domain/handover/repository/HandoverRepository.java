package com.example.likelionhackathon.domain.handover.repository;

import com.example.likelionhackathon.domain.handover.entity.Handover;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.HandoverStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface HandoverRepository extends JpaRepository<Handover, Long> {

    boolean existsByProjectIdAndCycleIdAndStatusIn(
            Long projectId,
            Long cycleId,
            Collection<HandoverStatus> statuses
    );

    @EntityGraph(attributePaths = {"items", "sourceTypes"})
    Optional<Handover> findOneById(Long id);
}
