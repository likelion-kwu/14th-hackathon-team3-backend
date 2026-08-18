package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleStatusSchedulerTest {

    @Mock
    private CycleRepository cycleRepository;
    @Mock
    private CycleIssuePort cycleIssuePort;

    private CycleStatusScheduler cycleStatusScheduler;

    @BeforeEach
    void setUp() {
        cycleStatusScheduler = new CycleStatusScheduler(cycleRepository, cycleIssuePort);
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

    @Test
    void catchUpHandsUnfinishedIssuesToTheNextCycle() {
        LocalDate today = LocalDate.now();
        Cycle finished = cycle(today.minusDays(20), today.minusDays(7));
        ReflectionTestUtils.setField(finished, "id", 11L);
        Cycle next = cycle(today.minusDays(6), today.plusDays(7));
        ReflectionTestUtils.setField(next, "id", 12L);

        when(cycleRepository.findByStatusNotAndStartDateLessThanEqual(eq(CycleStatus.COMPLETED), any()))
                .thenReturn(List.of(finished));
        when(cycleRepository.findFirstByProjectIdAndStartDateGreaterThanAndEndDateGreaterThanEqualOrderByStartDateAsc(
                eq(1L), eq(finished.getStartDate()), any()))
                .thenReturn(Optional.of(next));

        cycleStatusScheduler.catchUpStatuses();

        verify(cycleIssuePort).moveUnfinishedIssues(11L, 12L);
    }

    @Test
    void catchUpLeavesIssuesAloneWhenThereIsNoNextCycle() {
        LocalDate today = LocalDate.now();
        Cycle lastOne = cycle(today.minusDays(20), today.minusDays(7));
        ReflectionTestUtils.setField(lastOne, "id", 11L);

        when(cycleRepository.findByStatusNotAndStartDateLessThanEqual(eq(CycleStatus.COMPLETED), any()))
                .thenReturn(List.of(lastOne));
        when(cycleRepository.findFirstByProjectIdAndStartDateGreaterThanAndEndDateGreaterThanEqualOrderByStartDateAsc(
                any(), any(), any()))
                .thenReturn(Optional.empty());

        cycleStatusScheduler.catchUpStatuses();

        assertThat(lastOne.getStatus()).isEqualTo(CycleStatus.COMPLETED);
        Mockito.verifyNoInteractions(cycleIssuePort);
    }

    @Test
    void catchUpDoesNotHandOverWhenCycleOnlyStarts() {
        LocalDate today = LocalDate.now();
        Cycle starting = cycle(today, today.plusDays(13));
        when(cycleRepository.findByStatusNotAndStartDateLessThanEqual(eq(CycleStatus.COMPLETED), any()))
                .thenReturn(List.of(starting));

        cycleStatusScheduler.catchUpStatuses();

        assertThat(starting.getStatus()).isEqualTo(CycleStatus.IN_PROGRESS);
        Mockito.verifyNoInteractions(cycleIssuePort);
    }

    private Cycle cycle(LocalDate startDate, LocalDate endDate) {
        return Cycle.create(1L, "Cycle 1", startDate, endDate, null);
    }
}
