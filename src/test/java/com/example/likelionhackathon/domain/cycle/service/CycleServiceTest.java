package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.dto.CycleRequest;
import com.example.likelionhackathon.domain.cycle.dto.CycleResponse;
import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.AnalysisStatus;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import com.example.likelionhackathon.domain.cycle.repository.CycleAiAnalysisRepository;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort.IssueStats;
import com.example.likelionhackathon.domain.project.service.ProjectAccessService;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long CYCLE_ID = 3L;

    @Mock
    private CycleRepository cycleRepository;

    @Mock
    private CycleAiAnalysisRepository cycleAiAnalysisRepository;

    @Mock
    private CycleIssuePort cycleIssuePort;

    @Mock
    private ProjectAccessService projectAccessService;

    private CycleService cycleService;

    @BeforeEach
    void setUp() {
        cycleService = new CycleService(
                cycleRepository, cycleAiAnalysisRepository, cycleIssuePort, projectAccessService);
    }

    @Test
    void getCyclesRejectsNonProjectMember() {
        doThrow(new CustomException(ErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireAccess(PROJECT_ID);

        assertThatThrownBy(() -> cycleService.getCycles(PROJECT_ID, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);

        verify(cycleRepository, never()).findByProjectIdOrderByStartDateAsc(any());
    }

    @Test
    void deleteRejectsNonProjectMember() {
        Cycle cycle = cycle(CYCLE_ID, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 12));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        doThrow(new CustomException(ErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireAccess(PROJECT_ID);

        assertThatThrownBy(() -> cycleService.delete(CYCLE_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);

        verify(cycleRepository, never()).delete(any());
    }

    @Test
    void changeStatusRejectsTransferToAnotherProjectCycle() {
        Cycle cycle = cycle(CYCLE_ID, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 12));
        cycle.changeStatus(CycleStatus.IN_PROGRESS);
        Cycle foreign = Cycle.create(99L, "남의 Cycle", LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 27), null);
        ReflectionTestUtils.setField(foreign, "id", 4L);

        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(cycleRepository.findById(4L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> cycleService.changeStatus(
                CYCLE_ID, new CycleRequest.ChangeStatus(CycleStatus.COMPLETED, true, 4L)))
                .isInstanceOf(CustomException.class)
                .hasMessage("다른 프로젝트의 사이클로는 이관할 수 없습니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_INVALID_INPUT);

        verify(cycleIssuePort, never()).moveUnfinishedIssues(any(), any());
    }

    @Test
    void createRejectsStartDateAfterEndDate() {
        CycleRequest.Create request = new CycleRequest.Create(
                "Cycle 4", LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 13), null);

        assertThatThrownBy(() -> cycleService.create(PROJECT_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessage("시작 일자가 마감 일자보다 늦을 수 없습니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_INVALID_INPUT);

        verify(cycleRepository, never()).save(any());
    }

    @Test
    void createRejectsOverlappingPeriod() {
        CycleRequest.Create request = new CycleRequest.Create(
                "Cycle 4", LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 27), null);
        when(cycleRepository.existsByProjectIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                PROJECT_ID, request.endDate(), request.startDate())).thenReturn(true);

        assertThatThrownBy(() -> cycleService.create(PROJECT_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessage("기존 사이클과 기간이 중복됩니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_CONFLICT);
    }

    @Test
    void createReturnsGeneratedId() {
        CycleRequest.Create request = new CycleRequest.Create(
                "Cycle 4", LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 27), "결제 모듈 안정화");
        when(cycleRepository.existsByProjectIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                PROJECT_ID, request.endDate(), request.startDate())).thenReturn(false);
        when(cycleRepository.save(any(Cycle.class))).thenAnswer(invocation -> {
            Cycle saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 4L);
            return saved;
        });

        CycleResponse.Created response = cycleService.create(PROJECT_ID, request);

        assertThat(response.cycleId()).isEqualTo(4L);
    }

    @Test
    void getDetailAggregatesIssueStatsAndNextCycle() {
        Cycle cycle = cycle(CYCLE_ID, LocalDate.now().minusDays(10), LocalDate.now().plusDays(4));
        Cycle next = cycle(4L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(19));

        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(cycleIssuePort.statsOf(CYCLE_ID)).thenReturn(new IssueStats(23, 19, 5, 3, 1));
        when(cycleRepository.findFirstByProjectIdAndStartDateGreaterThanOrderByStartDateAsc(
                PROJECT_ID, cycle.getStartDate())).thenReturn(Optional.of(next));
        when(cycleAiAnalysisRepository.findFirstByCycleIdAndStatusOrderByAnalyzedAtDesc(
                CYCLE_ID, AnalysisStatus.COMPLETED)).thenReturn(Optional.empty());

        CycleResponse.Detail detail = cycleService.getDetail(CYCLE_ID);

        assertThat(detail.dDay()).isEqualTo(4);
        assertThat(detail.progressRate()).isEqualTo(83); // 19 / 23
        assertThat(detail.summary().doneCount()).isEqualTo(19);
        assertThat(detail.summary().canceledCount()).isEqualTo(1);
        assertThat(detail.nextCycle().cycleId()).isEqualTo(4L);
        assertThat(detail.lastAnalyzedAt()).isNull();
    }

    @Test
    void getDetailReturns404WhenCycleMissing() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cycleService.getDetail(CYCLE_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_NOT_FOUND);
    }

    @Test
    void updateRejectsCompletedCycle() {
        Cycle cycle = cycle(CYCLE_ID, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 12));
        cycle.changeStatus(CycleStatus.COMPLETED);
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));

        CycleRequest.Update request = new CycleRequest.Update(
                "Cycle 3 (연장)", LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 15), null);

        assertThatThrownBy(() -> cycleService.update(CYCLE_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessage("완료된 사이클은 수정할 수 없습니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_CONFLICT);
    }

    @Test
    void changeStatusRejectsSkippingInProgress() {
        Cycle cycle = cycle(CYCLE_ID, LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 27));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));

        CycleRequest.ChangeStatus request =
                new CycleRequest.ChangeStatus(CycleStatus.COMPLETED, null, null);

        assertThatThrownBy(() -> cycleService.changeStatus(CYCLE_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessage("허용되지 않은 상태 변경입니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_CONFLICT);
    }

    @Test
    void changeStatusRequiresTargetCycleWhenMovingIssues() {
        Cycle cycle = cycle(CYCLE_ID, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 12));
        cycle.changeStatus(CycleStatus.IN_PROGRESS);
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));

        CycleRequest.ChangeStatus request =
                new CycleRequest.ChangeStatus(CycleStatus.COMPLETED, true, null);

        assertThatThrownBy(() -> cycleService.changeStatus(CYCLE_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessage("이관 대상 사이클을 지정해주세요.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_INVALID_INPUT);

        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.IN_PROGRESS);
    }

    @Test
    void changeStatusMovesUnfinishedIssues() {
        Cycle cycle = cycle(CYCLE_ID, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 12));
        cycle.changeStatus(CycleStatus.IN_PROGRESS);
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(cycleRepository.findById(4L)).thenReturn(Optional.of(cycle(4L,
                LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 27))));
        when(cycleIssuePort.moveUnfinishedIssues(CYCLE_ID, 4L)).thenReturn(4);

        CycleResponse.StatusChanged response = cycleService.changeStatus(
                CYCLE_ID, new CycleRequest.ChangeStatus(CycleStatus.COMPLETED, true, 4L));

        assertThat(response.status()).isEqualTo(CycleStatus.COMPLETED);
        assertThat(response.movedIssueCount()).isEqualTo(4);
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.COMPLETED);
    }

    @Test
    void deleteRejectsCycleWithIssues() {
        Cycle cycle = cycle(CYCLE_ID, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 12));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));
        when(cycleIssuePort.hasAnyIssue(CYCLE_ID)).thenReturn(true);

        assertThatThrownBy(() -> cycleService.delete(CYCLE_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage("소속된 이슈가 있어 삭제할 수 없습니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_CONFLICT);

        verify(cycleRepository, never()).delete(any());
    }

    @Test
    void getCyclesMapsProgressRateFromIssueStats() {
        Cycle cycle = cycle(CYCLE_ID, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 12));
        when(cycleRepository.findByProjectIdOrderByStartDateAsc(PROJECT_ID)).thenReturn(List.of(cycle));
        when(cycleIssuePort.statsOf(anyLong())).thenReturn(new IssueStats(4, 3, 1, 0, 0));

        List<CycleResponse.Summary> summaries = cycleService.getCycles(PROJECT_ID, null);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).progressRate()).isEqualTo(75);
        assertThat(summaries.get(0).issueCount()).isEqualTo(4);
    }

    private Cycle cycle(Long id, LocalDate startDate, LocalDate endDate) {
        Cycle cycle = Cycle.create(PROJECT_ID, "Cycle " + id, startDate, endDate, null);
        ReflectionTestUtils.setField(cycle, "id", id);
        return cycle;
    }
}
