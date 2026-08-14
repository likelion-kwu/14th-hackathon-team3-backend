package com.example.likelionhackathon.domain.cycle.repository;

import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.ActivityType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CycleActivityRepository extends JpaRepository<CycleActivity, Long> {

    // 같은 시각의 활동이 페이지 경계에서 중복·누락되지 않도록 id 를 보조 정렬로 둔다.
    List<CycleActivity> findByCycleIdOrderByOccurredAtDescIdDesc(Long cycleId, Pageable pageable);

    List<CycleActivity> findByCycleIdAndTypeOrderByOccurredAtDescIdDesc(
            Long cycleId, ActivityType type, Pageable pageable);

    void deleteByCycleId(Long cycleId);
}
