package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.entity.CycleAiAnalysis;
import com.example.likelionhackathon.domain.cycle.repository.CycleAiAnalysisRepository;
import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort.IssueStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 사이클 AI 분석 실행부.
 *
 * <p>현재는 이슈 집계에서 진행률과 요약 문장을 계산하는 결정적 구현이다.
 * 근거(evidences)와 확인 필요 항목(checkNeeded)은 협업 도구 연동 데이터가 있어야 채울 수 있어
 * 비워 둔다. LLM 호출로 바꾸려면 인수인계 도메인의 OpenAiHandoverClient 와 연동 방식을 맞춰야 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CycleAnalysisRunner {

    private final CycleAiAnalysisRepository cycleAiAnalysisRepository;
    private final CycleActivityService cycleActivityService;
    private final CycleIssuePort cycleIssuePort;

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

            analysis.complete(
                    progressRate,
                    buildSummary(stats),
                    List.of(),
                    List.of(),
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
