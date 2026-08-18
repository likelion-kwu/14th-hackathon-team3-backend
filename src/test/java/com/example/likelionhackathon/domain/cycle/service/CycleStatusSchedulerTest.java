package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleStatusSchedulerTest {

    @Mock
    private CycleRepository cycleRepository;

    private CycleStatusScheduler cycleStatusScheduler;

    @BeforeEach
    void setUp() {
        cycleStatusScheduler = new CycleStatusScheduler(cycleRepository);
    }

    @Test
    void catchUpCompletesFinishedCycleAndStartsCurrentOne() {
        LocalDate today = LocalDate.now();
        Cycle finished = cycle(today.minusDays(20), today.minusDays(7));
        Cycle current = cycle(today.minusDays(6), today.plusDays(7));
        when(cycleRepository.findByStatusNotAndStartDateLessThanEqual(eq(CycleStatus.COMPLETED), any()))
                .thenReturn(List.of(finished, current));

        cycleStatusScheduler.catchUpStatuses();

        assertThat(finished.getStatus()).isEqualTo(CycleStatus.COMPLETED);
        assertThat(current.getStatus()).isEqualTo(CycleStatus.IN_PROGRESS);
    }

    @Test
    void catchUpLeavesCycleAlreadyMatchingItsPeriod() {
        LocalDate today = LocalDate.now();
        Cycle current = cycle(today.minusDays(2), today.plusDays(5));
        current.changeStatus(CycleStatus.IN_PROGRESS);
        when(cycleRepository.findByStatusNotAndStartDateLessThanEqual(eq(CycleStatus.COMPLETED), any()))
                .thenReturn(List.of(current));

        cycleStatusScheduler.catchUpStatuses();

        assertThat(current.getStatus()).isEqualTo(CycleStatus.IN_PROGRESS);
    }

    private Cycle cycle(LocalDate startDate, LocalDate endDate) {
        return Cycle.create(1L, "Cycle 1", startDate, endDate, null);
    }
}
