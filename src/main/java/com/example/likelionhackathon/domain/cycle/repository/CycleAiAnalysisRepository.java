package com.example.likelionhackathon.domain.cycle.repository;

import com.example.likelionhackathon.domain.cycle.entity.CycleAiAnalysis;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CycleAiAnalysisRepository extends JpaRepository<CycleAiAnalysis, Long> {

    Optional<CycleAiAnalysis> findFirstByCycleIdAndStatusOrderByAnalyzedAtDesc(Long cycleId, AnalysisStatus status);

    boolean existsByCycleIdAndStatusIn(Long cycleId, Collection<AnalysisStatus> statuses);

    /**
     * 여기는 벌크 삭제를 쓰지 않는다. CycleAiAnalysis 는 @ElementCollection 으로
     * 근거·확인 필요 항목 테이블을 갖고 있어, 벌크 삭제하면 그 행들이 남는다.
     */
    void deleteByCycleId(Long cycleId);
}
