package com.example.likelionhackathon.domain.cycle.repository;

import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.ActivityType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CycleActivityRepository extends JpaRepository<CycleActivity, Long> {

    // 같은 시각의 활동이 페이지 경계에서 중복·누락되지 않도록 id 를 보조 정렬로 둔다.
    List<CycleActivity> findByCycleIdOrderByOccurredAtDescIdDesc(Long cycleId, Pageable pageable);

    List<CycleActivity> findByCycleIdAndTypeOrderByOccurredAtDescIdDesc(
            Long cycleId, ActivityType type, Pageable pageable);

    /**
     * 파생 삭제는 행을 하나씩 읽어 지운다. 활동 기록은 사이클마다 많이 쌓일 수 있고
     * 컬렉션 필드도 없어 벌크 삭제로 한 번에 처리한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CycleActivity a where a.cycleId = :cycleId")
    void deleteByCycleId(@Param("cycleId") Long cycleId);
}
