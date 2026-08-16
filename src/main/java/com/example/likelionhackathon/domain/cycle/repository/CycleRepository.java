package com.example.likelionhackathon.domain.cycle.repository;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 같은 사이클에 동시에 들어온 AI 분석 요청을 줄 세우기 위한 잠금.
     *
     * <p>잠그지 않으면 여러 요청이 "진행 중인 분석이 있는지" 를 서로의 커밋 전에 읽어
     * 전부 통과한다. 그러면 분석이 중복 실행되고 활동 기록에도 같은 줄이 여러 번 쌓인다.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cycle c where c.id = :cycleId")
    Optional<Cycle> findByIdForUpdate(@Param("cycleId") Long cycleId);
}
