package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.project.service.ProjectCycleCreator;
import com.example.likelionhackathon.global.config.CycleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 프로젝트 생성 시 프로젝트 기간을 잘라 사이클을 만든다.
 *
 * <p>기간은 프로젝트 기간을 그대로 덮는다. 프로젝트가 시작 일자 &le; 마감 일자를 이미 검증하고,
 * 새 프로젝트에는 겹칠 사이클도 없어서 {@code CycleService.create} 의 검증을 다시 거칠 필요가 없다.</p>
 *
 * <p>사이클 길이는 {@link CycleProperties} 에서 읽는다. 기본값은 디자인 목업에 맞춘 2주다.</p>
 */
@Component
@RequiredArgsConstructor
public class CycleCreationAdapter implements ProjectCycleCreator {

    /** 사이클 목표 컬럼 길이. 프로젝트 목표는 2000자까지 허용돼서 그대로 넣으면 저장에서 터진다. */
    private static final int GOAL_MAX_LENGTH = 1000;

    private final CycleRepository cycleRepository;
    private final CycleProperties cycleProperties;

    @Override
    public void createInitialCycles(Long projectId, LocalDate startDate, LocalDate endDate, String goal) {
        LocalDate today = LocalDate.now();
        String cycleGoal = cutToColumnLength(goal);
        List<Cycle> cycles = new ArrayList<>();

        int order = 1;
        for (Period period : split(startDate, endDate)) {
            Cycle cycle = Cycle.create(projectId, "Cycle " + order++, period.start(), period.end(), cycleGoal);
            // 사이클을 시작시키는 화면이 없어서 기간을 보고 상태를 맞춰 둔다. 이미 지난 기간이면 완료,
            // 오늘이 들어간 사이클이면 진행 중이다. 이후는 CycleStatusScheduler 가 매일 따라잡는다.
            cycle.catchUpTo(today);
            cycles.add(cycle);
        }

        cycleRepository.saveAll(cycles);
    }

    /**
     * 프로젝트 기간을 앞에서부터 설정된 길이만큼 끊는다. 사이클끼리 하루도 겹치지 않고 빈 날도 없다.
     */
    private List<Period> split(LocalDate startDate, LocalDate endDate) {
        List<Period> periods = new ArrayList<>();

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            LocalDate last = cursor.plusDays(cycleProperties.getInitialLengthDays() - 1L);
            periods.add(new Period(cursor, last.isAfter(endDate) ? endDate : last));
            cursor = periods.get(periods.size() - 1).end().plusDays(1);
        }

        // 자투리 기준이 사이클 길이보다 크면 조각이 하나도 남지 못해 계속 합쳐진다. 길이까지만 본다.
        int minTailDays = Math.min(cycleProperties.getMinTailDays(), cycleProperties.getInitialLengthDays());
        Period tail = periods.get(periods.size() - 1);
        if (periods.size() > 1 && tail.lengthInDays() < minTailDays) {
            periods.remove(periods.size() - 1);
            Period previous = periods.remove(periods.size() - 1);
            periods.add(new Period(previous.start(), tail.end()));
        }

        return periods;
    }

    private String cutToColumnLength(String goal) {
        if (goal == null || goal.length() <= GOAL_MAX_LENGTH) {
            return goal;
        }
        return goal.substring(0, GOAL_MAX_LENGTH);
    }

    private record Period(LocalDate start, LocalDate end) {

        long lengthInDays() {
            return ChronoUnit.DAYS.between(start, end) + 1;
        }
    }
}
