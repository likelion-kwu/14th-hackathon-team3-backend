package com.example.likelionhackathon.domain.handover.repository;

import com.example.likelionhackathon.domain.handover.entity.CollaborationActivity;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface CollaborationActivityRepository extends JpaRepository<CollaborationActivity, Long> {

    boolean existsByProjectIdAndCycleIdAndOccurredAtBetweenAndProviderIn(
            Long projectId,
            Long cycleId,
            OffsetDateTime from,
            OffsetDateTime to,
            Collection<Provider> providers
    );

    List<CollaborationActivity> findByProjectIdAndCycleIdAndOccurredAtBetweenAndProviderInOrderByOccurredAtAsc(
            Long projectId,
            Long cycleId,
            OffsetDateTime from,
            OffsetDateTime to,
            Collection<Provider> providers
    );
}
