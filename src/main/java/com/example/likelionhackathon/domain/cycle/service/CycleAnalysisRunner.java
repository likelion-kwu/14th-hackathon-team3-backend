package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.entity.AnalysisCheckNeeded;
import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.entity.CycleAiAnalysis;
import com.example.likelionhackathon.domain.cycle.repository.CycleActivityRepository;
import com.example.likelionhackathon.domain.cycle.repository.CycleAiAnalysisRepository;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort.IssueStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 사이클 AI 분석 실행부.
 *
 * <p>{@code @Async} 동작은 {@code OpenAiConfig} 의 {@code @EnableAsync} 에 의존한다.
 * 그 설정이 사라지면 이 메서드가 동기로 돌아 요청 스레드를 붙잡는다.</p>
 *
 * <p>요약 문장과 확인 필요 항목은 {@link CycleAnalysisPort} 가 만든다.
 * 분석기가 답하지 못하면 집계로 만든 문장으로 대신해 화면이 비지 않게 한다.</p>
 *
 * <p>진행률은 분석기에 맡기지 않고 이슈 집계로 계산한다.
 * 같은 값을 사이클 상세·목록 응답도 같은 식으로 계산해 내보내기 때문에,
 * 여기서만 다른 방식으로 정하면 한 사이클의 진행률이 화면마다 달라진다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CycleAnalysisRunner {

    /** 판단에 쓸 최근 활동 기록 개수. */
    private static final int RECENT_ACTIVITY_SIZE = 40;

    private final CycleAiAnalysisRepository cycleAiAnalysisRepository;
    private final CycleActivityRepository cycleActivityRepository;
    private final CycleRepository cycleRepository;
    private final CycleActivityService cycleActivityService;
    private final CycleIssuePort cycleIssuePort;
    private final CycleAnalysisPort cycleAnalysisPort;

    @Async
    // 커밋 이후에 도는 리스너라 새 트랜잭션을 열어야 변경 내용이 저장된다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CycleAnalysisRequestedEvent event) {
        CycleAiAnalysis analysis = cycleAiAnalysisRepository.findById(event.analysisId()).orElse(null);
        if (analysis == null) {
            log.warn("분석 대상을 찾을 수 없습니다. analysisId={}", event.analysisId());
            return;
        }

        try {
            analysis.markRunning();

            IssueStats stats = cycleIssuePort.statsOf(analysis.getCycleId());
            int progressRate = stats.progressRate();
            int previousProgressRate = analysis.getPreviousProgressRate();
            // 명세의 응답 예시가 초 단위라 소수점 이하를 버린다.
            LocalDateTime analyzedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

            // 분석이 실패해도 화면은 채워야 하므로 집계 문장으로 대신한다.
            Optional<CycleAnalysisPort.Result> analyzed = analyze(analysis.getCycleId(), stats, progressRate);

            analysis.complete(
                    progressRate,
                    analyzed.map(CycleAnalysisPort.Result::summary).orElseGet(() -> buildSummary(stats)),
                    // 근거(evidences)의 출처는 Slack·Notion 같은 외부 도구다.
                    // 사이클에는 그 데이터가 없어 지어내지 않고 비워 둔다.
                    List.of(),
                    analyzed.map(this::toCheckNeeded).orElseGet(List::of),
                    analyzedAt
            );

            if (progressRate != previousProgressRate) {
                cycleActivityService.record(CycleActivity.aiProgressUpdated(
                        analysis.getCycleId(),
                        analyzedAt,
                        previousProgressRate,
                        progressRate,
                        "완료된 업무 집계가 갱신되었습니다."
                ));
            }
        } catch (Exception e) {
            log.error("사이클 AI 분석에 실패했습니다. analysisId={}", event.analysisId(), e);
            analysis.fail();
        }
    }

    /**
     * 분석기에 넘길 재료를 모은다. 집계만으로는 "무엇이 언제까지인데 멈춰 있는지" 를 알 수 없어
     * 이슈 목록과 최근 활동 기록을 함께 넘긴다.
     */
    private Optional<CycleAnalysisPort.Result> analyze(Long cycleId, IssueStats stats, int progressRate) {
        Cycle cycle = cycleRepository.findById(cycleId).orElse(null);
        if (cycle == null) {
            return Optional.empty();
        }

        LocalDate today = LocalDate.now();
        List<CycleAnalysisPort.ActivityBrief> activities = cycleActivityRepository
                .findByCycleIdOrderByOccurredAtDescIdDesc(cycleId, PageRequest.of(0, RECENT_ACTIVITY_SIZE))
                .stream()
                .map(activity -> new CycleAnalysisPort.ActivityBrief(
                        activity.getType().name(),
                        activity.getOccurredAt(),
                        activity.getActorName(),
                        activity.getIssueTitle(),
                        activity.getBeforeValue(),
                        activity.getAfterValue(),
                        activity.getReason()
                ))
                .toList();

        return cycleAnalysisPort.analyze(new CycleAnalysisPort.Input(
                cycle.getName(),
                cycle.getStartDate(),
                cycle.getEndDate(),
                today,
                cycle.getGoal(),
                progressRate,
                cycle.plannedProgressRate(today),
                stats,
                cycleIssuePort.briefsOf(cycleId),
                activities
        ));
    }

    private List<AnalysisCheckNeeded> toCheckNeeded(CycleAnalysisPort.Result result) {
        return result.checkNeeded().stream()
                .map(item -> new AnalysisCheckNeeded(item.type(), item.message(), item.issueId()))
                .toList();
    }

    private String buildSummary(IssueStats stats) {
        if (stats.totalCount() == 0) {
            return "아직 사이클에 등록된 이슈가 없습니다.";
        }

        return "전체 %d개 업무 중 %d개가 완료되었고, %d개가 진행 중입니다. 확인이 필요한 업무는 %d개입니다."
                .formatted(
                        stats.totalCount(),
                        stats.doneCount(),
                        stats.inProgressCount(),
                        stats.needsReviewCount()
                );
    }
}
