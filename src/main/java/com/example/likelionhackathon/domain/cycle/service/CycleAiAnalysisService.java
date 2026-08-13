package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.dto.CycleRequest;
import com.example.likelionhackathon.domain.cycle.dto.CycleResponse;
import com.example.likelionhackathon.domain.cycle.entity.CycleAiAnalysis;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.AnalysisStatus;
import com.example.likelionhackathon.domain.cycle.repository.CycleAiAnalysisRepository;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CycleAiAnalysisService {

    private static final int ESTIMATED_SECONDS = 30;
    private static final List<AnalysisStatus> RUNNING_STATUSES =
            List.of(AnalysisStatus.PENDING, AnalysisStatus.RUNNING);

    private final CycleAiAnalysisRepository cycleAiAnalysisRepository;
    private final CycleRepository cycleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CycleResponse.Analysis getAnalysis(Long cycleId) {
        if (!cycleRepository.existsById(cycleId)) {
            throw new CustomException(ErrorCode.CYCLE_NOT_FOUND);
        }

        CycleAiAnalysis analysis = cycleAiAnalysisRepository
                .findFirstByCycleIdAndStatusOrderByAnalyzedAtDesc(cycleId, AnalysisStatus.COMPLETED)
                .orElseThrow(() -> new CustomException(ErrorCode.CYCLE_NOT_FOUND, "아직 분석 이력이 없습니다."));

        return CycleResponse.Analysis.of(analysis);
    }

    @Transactional
    public CycleResponse.AnalysisJob runAnalysis(Long cycleId, CycleRequest.RunAnalysis request) {
        if (!cycleRepository.existsById(cycleId)) {
            throw new CustomException(ErrorCode.CYCLE_NOT_FOUND);
        }

        boolean forced = request != null && request.isForced();
        if (!forced && cycleAiAnalysisRepository.existsByCycleIdAndStatusIn(cycleId, RUNNING_STATUSES)) {
            throw new CustomException(ErrorCode.CYCLE_CONFLICT, "이미 분석이 진행 중입니다.");
        }

        int previousProgressRate = cycleAiAnalysisRepository
                .findFirstByCycleIdAndStatusOrderByAnalyzedAtDesc(cycleId, AnalysisStatus.COMPLETED)
                .map(CycleAiAnalysis::getProgressRate)
                .orElse(0);

        CycleAiAnalysis analysis = cycleAiAnalysisRepository.save(
                CycleAiAnalysis.pending(cycleId, previousProgressRate));

        // 커밋 이후에 비동기로 실행해야 분석 대상 행을 다른 스레드에서 읽을 수 있다.
        eventPublisher.publishEvent(new CycleAnalysisRequestedEvent(analysis.getId()));

        return new CycleResponse.AnalysisJob(analysis.getId(), analysis.getStatus(), ESTIMATED_SECONDS);
    }
}
