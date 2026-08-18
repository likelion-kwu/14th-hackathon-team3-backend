package com.example.likelionhackathon.domain.cycle.entity;

import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CycleTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 1);
    private static final LocalDate END = LocalDate.of(2026, 8, 11);

    @Test
    void statusFollowsWhereTodayFallsInThePeriod() {
        Cycle cycle = cycle(START, END);

        assertThat(cycle.statusOn(START.minusDays(1))).isEqualTo(CycleStatus.PLANNED);
        assertThat(cycle.statusOn(START)).isEqualTo(CycleStatus.IN_PROGRESS);
        assertThat(cycle.statusOn(END)).isEqualTo(CycleStatus.IN_PROGRESS);
        assertThat(cycle.statusOn(END.plusDays(1))).isEqualTo(CycleStatus.COMPLETED);
    }

    @Test
    void catchUpMovesPlannedCycleForwardAsDatesPass() {
        Cycle cycle = cycle(START, END);

        assertThat(cycle.catchUpTo(START.minusDays(1))).isFalse();
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.PLANNED);

        assertThat(cycle.catchUpTo(START)).isTrue();
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.IN_PROGRESS);

        assertThat(cycle.catchUpTo(END.plusDays(1))).isTrue();
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.COMPLETED);
    }

    @Test
    void catchUpSkipsStraightToCompletedForALongGoneCycle() {
        Cycle cycle = cycle(START, END);

        assertThat(cycle.catchUpTo(END.plusMonths(3))).isTrue();
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.COMPLETED);
    }

    @Test
    void catchUpNeverPullsAnAlreadyFinishedCycleBack() {
        // 사람이 마감일 전에 미리 완료시킨 사이클을, 기간이 남았다는 이유로 되돌리면 안 된다.
        Cycle cycle = cycle(START, END);
        cycle.changeStatus(CycleStatus.IN_PROGRESS);
        cycle.changeStatus(CycleStatus.COMPLETED);

        assertThat(cycle.catchUpTo(START.plusDays(1))).isFalse();
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.COMPLETED);
    }

    @Test
    void plannedProgressIsZeroBeforeAndOnStartDate() {
        Cycle cycle = cycle(START, END);

        assertThat(cycle.plannedProgressRate(START.minusDays(3))).isZero();
        assertThat(cycle.plannedProgressRate(START)).isZero();
    }

    @Test
    void plannedProgressIsHundredOnAndAfterEndDate() {
        Cycle cycle = cycle(START, END);

        assertThat(cycle.plannedProgressRate(END)).isEqualTo(100);
        assertThat(cycle.plannedProgressRate(END.plusDays(5))).isEqualTo(100);
    }

    @Test
    void plannedProgressFollowsElapsedDayRatio() {
        Cycle cycle = cycle(START, END); // 전체 10일

        assertThat(cycle.plannedProgressRate(START.plusDays(1))).isEqualTo(10);
        assertThat(cycle.plannedProgressRate(START.plusDays(5))).isEqualTo(50);
        assertThat(cycle.plannedProgressRate(START.plusDays(9))).isEqualTo(90);
    }

    @Test
    void singleDayCycleIsZeroBeforeAndHundredOnThatDay() {
        Cycle cycle = cycle(START, START);

        assertThat(cycle.plannedProgressRate(START.minusDays(1))).isZero();
        assertThat(cycle.plannedProgressRate(START)).isEqualTo(100);
    }

    private Cycle cycle(LocalDate startDate, LocalDate endDate) {
        return Cycle.create(1L, "Cycle 3", startDate, endDate, null);
    }
}
