package com.example.likelionhackathon.domain.cycle.repository;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CycleRepository extends JpaRepository<Cycle, Long> {

    List<Cycle> findByProjectIdOrderByStartDateAsc(Long projectId);

    List<Cycle> findByProjectIdAndStatusOrderByStartDateAsc(Long projectId, CycleStatus status);

    boolean existsByProjectId(Long projectId);

    // 기간이 겹치는 사이클이 있는지 확인한다. (기존.시작 <= 신규.마감 && 기존.마감 >= 신규.시작)
    boolean existsByProjectIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long projectId,
            LocalDate newEndDate,
            LocalDate newStartDate
    );

    boolean existsByProjectIdAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long projectId,
            Long excludedCycleId,
            LocalDate newEndDate,
            LocalDate newStartDate
    );

    // 다음 사이클 = 같은 프로젝트에서 시작일이 기준 사이클보다 늦은 것 중 가장 빠른 것
    Optional<Cycle> findFirstByProjectIdAndStartDateGreaterThanOrderByStartDateAsc(
            Long projectId,
            LocalDate startDate
    );
}
