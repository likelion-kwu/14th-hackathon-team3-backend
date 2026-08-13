package com.example.likelionhackathon.domain.cycle.repository;

import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.ActivityType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CycleActivityRepository extends JpaRepository<CycleActivity, Long> {

    List<CycleActivity> findByCycleIdOrderByOccurredAtDesc(Long cycleId, Pageable pageable);

    List<CycleActivity> findByCycleIdAndTypeOrderByOccurredAtDesc(Long cycleId, ActivityType type, Pageable pageable);
}
