package com.example.likelionhackathon.domain.cycle.entity;

import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 프로젝트 도메인이 아직 없어 연관관계 대신 식별자만 보관한다. (handover 도메인과 동일한 방식)
    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CycleStatus status;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(length = 1000)
    private String goal;

    // 두 요청이 같은 사이클을 동시에 수정하면 나중 커밋이 앞선 변경을 덮어써서 막는다.
    @Version
    private Long version;

    public static Cycle create(Long projectId, String name, LocalDate startDate, LocalDate endDate, String goal) {
        Cycle cycle = new Cycle();
        cycle.projectId = projectId;
        cycle.name = name;
        cycle.startDate = startDate;
        cycle.endDate = endDate;
        cycle.goal = goal;
        cycle.status = CycleStatus.PLANNED;
        return cycle;
    }

    public void update(String name, LocalDate startDate, LocalDate endDate, String goal) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.goal = goal;
    }

    public void changeStatus(CycleStatus next) {
        this.status = next;
    }

    public boolean isCompleted() {
        return status == CycleStatus.COMPLETED;
    }

    /**
     * 기간이 얼마나 지났는지로 보는 계획 진행률.
     * 실제 진행률과 나란히 두면 일정보다 앞서는지 뒤처지는지 알 수 있다.
     *
     * <p>시작일에는 0, 마감일에는 100 이 되도록 경과일을 전체 기간으로 나눈다.
     * 시작 전이면 0, 마감 후면 100 이다.</p>
     *
     * <p>하루짜리 사이클은 시작일과 마감일이 같아 두 규칙이 부딪힌다.
     * 그날 안에 끝내야 하는 일정이므로 마감일 쪽을 따라 100 으로 본다.</p>
     */
    public int plannedProgressRate(LocalDate today) {
        if (!today.isBefore(endDate)) {
            return 100;
        }
        if (!today.isAfter(startDate)) {
            return 0;
        }

        long total = ChronoUnit.DAYS.between(startDate, endDate);
        long elapsed = ChronoUnit.DAYS.between(startDate, today);
        return (int) (elapsed * 100 / total);
    }

    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        return !startDate.isAfter(otherEnd) && !endDate.isBefore(otherStart);
    }
}
