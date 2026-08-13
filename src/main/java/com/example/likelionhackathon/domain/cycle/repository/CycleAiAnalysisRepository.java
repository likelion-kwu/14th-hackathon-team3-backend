package com.example.likelionhackathon.domain.cycle.repository;

import com.example.likelionhackathon.domain.cycle.entity.CycleAiAnalysis;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CycleAiAnalysisRepository extends JpaRepository<CycleAiAnalysis, Long> {

    Optional<CycleAiAnalysis> findFirstByCycleIdAndStatusOrderByAnalyzedAtDesc(Long cycleId, AnalysisStatus status);

    boolean existsByCycleIdAndStatusIn(Long cycleId, Collection<AnalysisStatus> statuses);
}
