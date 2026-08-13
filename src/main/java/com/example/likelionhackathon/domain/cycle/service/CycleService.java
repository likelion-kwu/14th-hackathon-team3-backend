package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.dto.CycleRequest;
import com.example.likelionhackathon.domain.cycle.dto.CycleResponse;
import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleAiAnalysis;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.AnalysisStatus;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import com.example.likelionhackathon.domain.cycle.repository.CycleAiAnalysisRepository;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort.IssueStats;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CycleService {

    private final CycleRepository cycleRepository;
    private final CycleAiAnalysisRepository cycleAiAnalysisRepository;
    private final CycleIssuePort cycleIssuePort;

    public List<CycleResponse.Summary> getCycles(Long projectId, CycleStatus status) {
        List<Cycle> cycles = (status == null)
                ? cycleRepository.findByProjectIdOrderByStartDateAsc(projectId)
                : cycleRepository.findByProjectIdAndStatusOrderByStartDateAsc(projectId, status);

        return cycles.stream()
                .map(cycle -> CycleResponse.Summary.of(cycle, cycleIssuePort.statsOf(cycle.getId())))
                .toList();
    }

    @Transactional
    public CycleResponse.Created create(Long projectId, CycleRequest.Create request) {
        validatePeriod(request.startDate(), request.endDate());

        boolean overlapped = cycleRepository
                .existsByProjectIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        projectId, request.endDate(), request.startDate());
        if (overlapped) {
            throw new CustomException(ErrorCode.CYCLE_CONFLICT, "기존 사이클과 기간이 중복됩니다.");
        }

        Cycle saved = cycleRepository.save(
                Cycle.create(projectId, request.name(), request.startDate(), request.endDate(), request.goal()));

        return new CycleResponse.Created(saved.getId());
    }

    public CycleResponse.Detail getDetail(Long cycleId) {
        Cycle cycle = findCycle(cycleId);
        IssueStats stats = cycleIssuePort.statsOf(cycleId);

        CycleResponse.NextCycle nextCycle = cycleRepository
                .findFirstByProjectIdAndStartDateGreaterThanOrderByStartDateAsc(
                        cycle.getProjectId(), cycle.getStartDate())
                .map(CycleResponse.NextCycle::of)
                .orElse(null);

        return new CycleResponse.Detail(
                cycle.getId(),
                cycle.getName(),
                cycle.getStatus(),
                cycle.getStartDate(),
                cycle.getEndDate(),
                ChronoUnit.DAYS.between(LocalDate.now(), cycle.getEndDate()),
                stats.progressRate(),
                CycleResponse.IssueSummary.of(stats),
                nextCycle,
                lastAnalyzedAt(cycleId)
        );
    }

    @Transactional
    public CycleResponse.Updated update(Long cycleId, CycleRequest.Update request) {
        Cycle cycle = findCycle(cycleId);
        if (cycle.isCompleted()) {
            throw new CustomException(ErrorCode.CYCLE_CONFLICT, "완료된 사이클은 수정할 수 없습니다.");
        }

        validatePeriod(request.startDate(), request.endDate());

        boolean overlapped = cycleRepository
                .existsByProjectIdAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        cycle.getProjectId(), cycleId, request.endDate(), request.startDate());
        if (overlapped) {
            throw new CustomException(ErrorCode.CYCLE_CONFLICT, "기존 사이클과 기간이 중복됩니다.");
        }

        cycle.update(request.name(), request.startDate(), request.endDate(), request.goal());

        return CycleResponse.Updated.of(cycle);
    }

    @Transactional
    public CycleResponse.StatusChanged changeStatus(Long cycleId, CycleRequest.ChangeStatus request) {
        Cycle cycle = findCycle(cycleId);

        if (!cycle.getStatus().canTransitionTo(request.status())) {
            throw new CustomException(ErrorCode.CYCLE_CONFLICT, "허용되지 않은 상태 변경입니다.");
        }

        int movedIssueCount = 0;
        if (request.shouldMoveUnfinishedIssues()) {
            movedIssueCount = moveUnfinishedIssues(cycleId, request.targetCycleId());
        }

        cycle.changeStatus(request.status());

        return new CycleResponse.StatusChanged(cycle.getId(), cycle.getStatus(), movedIssueCount);
    }

    @Transactional
    public void delete(Long cycleId) {
        Cycle cycle = findCycle(cycleId);

        if (cycleIssuePort.hasAnyIssue(cycleId)) {
            throw new CustomException(ErrorCode.CYCLE_CONFLICT, "소속된 이슈가 있어 삭제할 수 없습니다.");
        }

        cycleRepository.delete(cycle);
    }

    private int moveUnfinishedIssues(Long cycleId, Long targetCycleId) {
        if (targetCycleId == null) {
            throw new CustomException(ErrorCode.CYCLE_INVALID_INPUT, "이관 대상 사이클을 지정해주세요.");
        }
        if (targetCycleId.equals(cycleId)) {
            throw new CustomException(ErrorCode.CYCLE_INVALID_INPUT, "이관 대상 사이클이 현재 사이클과 같습니다.");
        }
        if (!cycleRepository.existsById(targetCycleId)) {
            throw new CustomException(ErrorCode.CYCLE_NOT_FOUND, "이관 대상 사이클을 찾을 수 없습니다.");
        }

        return cycleIssuePort.moveUnfinishedIssues(cycleId, targetCycleId);
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.CYCLE_INVALID_INPUT, "시작 일자가 마감 일자보다 늦을 수 없습니다.");
        }
    }

    private LocalDateTime lastAnalyzedAt(Long cycleId) {
        return cycleAiAnalysisRepository
                .findFirstByCycleIdAndStatusOrderByAnalyzedAtDesc(cycleId, AnalysisStatus.COMPLETED)
                .map(CycleAiAnalysis::getAnalyzedAt)
                .orElse(null);
    }

    private Cycle findCycle(Long cycleId) {
        return cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CustomException(ErrorCode.CYCLE_NOT_FOUND));
    }
}
