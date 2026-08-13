package com.example.likelionhackathon.domain.cycle.entity;

import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        return !startDate.isAfter(otherEnd) && !endDate.isBefore(otherStart);
    }
}
