package com.example.likelionhackathon.domain.cycle.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CycleTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 1);
    private static final LocalDate END = LocalDate.of(2026, 8, 11);

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
