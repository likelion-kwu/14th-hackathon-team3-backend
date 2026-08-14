package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.dto.CycleRequest;
import com.example.likelionhackathon.domain.cycle.dto.CycleResponse;
import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleAiAnalysis;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.AnalysisStatus;
import com.example.likelionhackathon.domain.cycle.repository.CycleAiAnalysisRepository;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.project.service.ProjectAccessService;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleAiAnalysisServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long CYCLE_ID = 3L;

    @Mock
    private CycleAiAnalysisRepository cycleAiAnalysisRepository;

    @Mock
    private CycleRepository cycleRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CycleAiAnalysisService cycleAiAnalysisService;

    @BeforeEach
    void setUp() {
        cycleAiAnalysisService = new CycleAiAnalysisService(
                cycleAiAnalysisRepository, cycleRepository, projectAccessService, eventPublisher);
    }

    @Test
    void rejectsRerunWhileAnalysisInProgress() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(cycleAiAnalysisRepository.existsByCycleIdAndStatusIn(eq(CYCLE_ID), anyList())).thenReturn(true);

        assertThatThrownBy(() -> cycleAiAnalysisService.runAnalysis(CYCLE_ID, new CycleRequest.RunAnalysis(false)))
                .isInstanceOf(CustomException.class)
                .hasMessage("이미 분석이 진행 중입니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_CONFLICT);

        verify(cycleAiAnalysisRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void forceSkipsInProgressCheck() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(cycleAiAnalysisRepository.findFirstByCycleIdAndStatusOrderByAnalyzedAtDesc(
                CYCLE_ID, AnalysisStatus.COMPLETED)).thenReturn(Optional.empty());
        when(cycleAiAnalysisRepository.save(any(CycleAiAnalysis.class))).thenAnswer(invocation -> {
            CycleAiAnalysis saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 88L);
            return saved;
        });

        CycleResponse.AnalysisJob job =
                cycleAiAnalysisService.runAnalysis(CYCLE_ID, new CycleRequest.RunAnalysis(true));

        assertThat(job.analysisId()).isEqualTo(88L);
        assertThat(job.status()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(job.estimatedSeconds()).isEqualTo(30);
        verify(cycleAiAnalysisRepository, never()).existsByCycleIdAndStatusIn(any(), anyList());
        verify(eventPublisher).publishEvent(any(CycleAnalysisRequestedEvent.class));
    }

    @Test
    void getAnalysisReturns404WhenNoHistory() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(cycleAiAnalysisRepository.findFirstByCycleIdAndStatusOrderByAnalyzedAtDesc(
                CYCLE_ID, AnalysisStatus.COMPLETED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cycleAiAnalysisService.getAnalysis(CYCLE_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage("아직 분석 이력이 없습니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_NOT_FOUND);
    }

    private Cycle cycle() {
        Cycle cycle = Cycle.create(
                PROJECT_ID, "Cycle 3", LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 12), null);
        ReflectionTestUtils.setField(cycle, "id", CYCLE_ID);
        return cycle;
    }
}
