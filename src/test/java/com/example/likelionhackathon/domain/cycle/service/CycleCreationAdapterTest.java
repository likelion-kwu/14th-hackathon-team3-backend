package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.global.config.CycleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CycleCreationAdapterTest {

    @Mock
    private CycleRepository cycleRepository;

    private CycleProperties cycleProperties;
    private CycleCreationAdapter cycleCreationAdapter;

    @BeforeEach
    void setUp() {
        cycleProperties = new CycleProperties();
        cycleCreationAdapter = new CycleCreationAdapter(cycleRepository, cycleProperties);
    }

    @Test
    void createInitialCyclesSplitsProjectPeriodIntoTwoWeekCycles() {
        cycleCreationAdapter.createInitialCycles(
                7L, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 8, 30), "결제 시스템 연동");

        assertThat(capturedCycles())
                .extracting(Cycle::getName, Cycle::getStartDate, Cycle::getEndDate)
                .containsExactly(
                        tuple("Cycle 1", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 28)),
                        tuple("Cycle 2", LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 12)),
                        tuple("Cycle 3", LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 26)),
                        tuple("Cycle 4", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 9)),
                        tuple("Cycle 5", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 23)),
                        tuple("Cycle 6", LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 30)));
    }

    @Test
    void createInitialCyclesLeavesNoGapOrOverlapBetweenCycles() {
        cycleCreationAdapter.createInitialCycles(
                7L, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 8, 30), null);

        List<Cycle> cycles = capturedCycles();
        assertThat(cycles.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(cycles.get(cycles.size() - 1).getEndDate()).isEqualTo(LocalDate.of(2026, 8, 30));
        for (int i = 1; i < cycles.size(); i++) {
            assertThat(cycles.get(i).getStartDate())
                    .isEqualTo(cycles.get(i - 1).getEndDate().plusDays(1));
        }
    }

    @Test
    void createInitialCyclesAbsorbsTailShorterThanAWeek() {
        // 19일짜리 프로젝트. 2주로 자르면 5일이 남는데, 5일짜리 사이클 대신 하나로 합친다.
        cycleCreationAdapter.createInitialCycles(
                7L, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 24), null);

        assertThat(capturedCycles()).singleElement()
                .extracting(Cycle::getStartDate, Cycle::getEndDate)
                .containsExactly(LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 24));
    }

    @Test
    void createInitialCyclesKeepsTailOfAWeekOrLonger() {
        // 21일짜리 프로젝트. 남는 7일은 그대로 사이클 하나가 된다.
        cycleCreationAdapter.createInitialCycles(
                7L, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 23), null);

        assertThat(capturedCycles())
                .extracting(Cycle::getStartDate, Cycle::getEndDate)
                .containsExactly(
                        tuple(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 16)),
                        tuple(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23)));
    }

    @Test
    void createInitialCyclesShortProjectBecomesSingleCycle() {
        cycleCreationAdapter.createInitialCycles(
                7L, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 6), null);

        assertThat(capturedCycles()).singleElement()
                .extracting(Cycle::getName, Cycle::getStartDate, Cycle::getEndDate)
                .containsExactly("Cycle 1", LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 6));
    }

    @Test
    void createInitialCyclesStartsOnlyTheCycleHoldingToday() {
        LocalDate today = LocalDate.now();

        cycleCreationAdapter.createInitialCycles(7L, today.minusDays(3), today.plusDays(30), null);

        List<Cycle> cycles = capturedCycles();
        assertThat(cycles.get(0).getStatus()).isEqualTo(CycleStatus.IN_PROGRESS);
        assertThat(cycles.subList(1, cycles.size()))
                .extracting(Cycle::getStatus)
                .containsOnly(CycleStatus.PLANNED);
    }

    @Test
    void createInitialCyclesCompletesCyclesAlreadyOver() {
        // 이미 진행 중이던 프로젝트를 뒤늦게 등록하면, 지난 사이클은 완료로 깔린다.
        LocalDate today = LocalDate.now();

        cycleCreationAdapter.createInitialCycles(7L, today.minusDays(30), today.plusDays(10), null);

        List<Cycle> cycles = capturedCycles();
        assertThat(cycles.get(0).getStatus()).isEqualTo(CycleStatus.COMPLETED);
        assertThat(cycles.get(cycles.size() - 1).getStatus()).isEqualTo(CycleStatus.IN_PROGRESS);
    }

    @Test
    void createInitialCyclesFollowsConfiguredCycleLength() {
        cycleProperties.setInitialLengthDays(7);
        cycleProperties.setMinTailDays(3);

        cycleCreationAdapter.createInitialCycles(
                7L, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 23), null);

        assertThat(capturedCycles())
                .extracting(Cycle::getName, Cycle::getStartDate, Cycle::getEndDate)
                .containsExactly(
                        tuple("Cycle 1", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9)),
                        tuple("Cycle 2", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 16)),
                        tuple("Cycle 3", LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23)));
    }

    @Test
    void createInitialCyclesKeepsEveryTailWhenMinTailIsZero() {
        cycleProperties.setMinTailDays(0);

        cycleCreationAdapter.createInitialCycles(
                7L, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 24), null);

        assertThat(capturedCycles())
                .extracting(Cycle::getStartDate, Cycle::getEndDate)
                .containsExactly(
                        tuple(LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 19)),
                        tuple(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 24)));
    }

    @Test
    void createInitialCyclesStillSplitsWhenMinTailExceedsCycleLength() {
        // 자투리 기준이 사이클 길이보다 크면 무한정 합쳐질 수 있어 길이까지만 본다.
        cycleProperties.setInitialLengthDays(7);
        cycleProperties.setMinTailDays(30);

        cycleCreationAdapter.createInitialCycles(
                7L, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 23), null);

        assertThat(capturedCycles()).hasSize(3);
    }

    @Test
    void createInitialCyclesPlansEveryCycleWhenProjectStartsLater() {
        LocalDate today = LocalDate.now();

        cycleCreationAdapter.createInitialCycles(7L, today.plusDays(1), today.plusDays(40), null);

        assertThat(capturedCycles())
                .extracting(Cycle::getStatus)
                .containsOnly(CycleStatus.PLANNED);
    }

    @Test
    void createInitialCyclesCutsGoalLongerThanColumnLength() {
        String objective = "가".repeat(2000);

        cycleCreationAdapter.createInitialCycles(
                7L, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 24), objective);

        assertThat(capturedCycles()).singleElement()
                .extracting(Cycle::getGoal, org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .hasSize(1000);
    }

    @Test
    void createInitialCyclesShareProjectIdAndGoal() {
        cycleCreationAdapter.createInitialCycles(
                7L, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 8, 30), "결제 시스템 연동");

        assertThat(capturedCycles())
                .extracting(Cycle::getProjectId, Cycle::getGoal)
                .containsOnly(tuple(7L, "결제 시스템 연동"));
    }

    @SuppressWarnings("unchecked")
    private List<Cycle> capturedCycles() {
        ArgumentCaptor<List<Cycle>> captor = ArgumentCaptor.forClass(List.class);
        verify(cycleRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
