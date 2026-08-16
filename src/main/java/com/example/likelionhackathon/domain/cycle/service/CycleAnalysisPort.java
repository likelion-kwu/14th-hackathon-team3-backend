package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CheckNeededType;
import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort.IssueBrief;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 사이클 상황을 문장으로 정리해 주는 분석기.
 *
 * <p>실패해도 화면이 비면 안 되므로 {@link Optional#empty()} 로 돌려주고,
 * 부르는 쪽이 집계 문장으로 대신한다. 예외를 던지지 않는다.</p>
 */
public interface CycleAnalysisPort {

    Optional<Result> analyze(Input input);

    record Input(
            String cycleName,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate today,
            String goal,
            int progressRate,
            int plannedProgressRate,
            CycleIssuePort.IssueStats stats,
            List<IssueBrief> issues,
            List<ActivityBrief> activities
    ) {
    }

    /**
     * 활동 기록 한 줄. 상태가 언제 어떻게 바뀌었고 무슨 말이 오갔는지가 판단 재료다.
     */
    record ActivityBrief(
            String type,
            LocalDateTime occurredAt,
            String actorName,
            String issueTitle,
            String before,
            String after,
            String reason
    ) {
    }

    /**
     * @param summary     사이클 상황 요약. 화면의 AI 분석 문단에 그대로 들어간다.
     * @param checkNeeded 사람이 확인해야 한다고 판단한 이슈들
     */
    record Result(
            String summary,
            List<CheckNeeded> checkNeeded
    ) {
    }

    record CheckNeeded(
            CheckNeededType type,
            String message,
            Long issueId
    ) {
    }
}
