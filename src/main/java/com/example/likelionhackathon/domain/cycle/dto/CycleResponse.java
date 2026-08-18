package com.example.likelionhackathon.domain.cycle.dto;

import com.example.likelionhackathon.domain.cycle.entity.AnalysisCheckNeeded;
import com.example.likelionhackathon.domain.cycle.entity.AnalysisEvidence;
import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.entity.CycleAiAnalysis;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.ActivityType;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.AnalysisStatus;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CheckNeededType;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.EvidenceSource;
import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort.IssueProgress;
import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort.IssueStats;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CycleResponse {

    private CycleResponse() {
    }

    public record Summary(
            Long cycleId,
            String name,
            CycleStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int progressRate,
            int plannedProgressRate,
            int issueCount
    ) {
        public static Summary of(Cycle cycle, IssueStats stats, LocalDate today) {
            return new Summary(
                    cycle.getId(),
                    cycle.getName(),
                    cycle.getStatus(),
                    cycle.getStartDate(),
                    cycle.getEndDate(),
                    stats.progressRate(),
                    cycle.plannedProgressRate(today),
                    stats.totalCount()
            );
        }
    }

    public record Created(Long cycleId) {
    }

    public record Detail(
            Long cycleId,
            String name,
            CycleStatus status,
            LocalDate startDate,
            LocalDate endDate,
            long dDay,
            int progressRate,
            int plannedProgressRate,
            IssueSummary summary,
            @Schema(description = "주요 진행 상황. 취소된 이슈를 뺀 최근 변경순 5건이다.")
            List<KeyProgress> keyProgress,
            NextCycle nextCycle,
            LocalDateTime lastAnalyzedAt
    ) {
    }

    /**
     * 화면의 '주요 진행 상황' 한 줄. 이슈가 지금 어디까지 왔는지를 그대로 옮긴다.
     */
    @Schema(description = "주요 진행 상황 한 줄")
    public record KeyProgress(
            @Schema(description = "이슈 식별자", example = "31")
            Long issueId,
            @Schema(description = "이슈 제목", example = "결제 API v3 연동 개발")
            String title,
            @Schema(description = "이슈 상태", example = "IN_PROGRESS",
                    allowableValues = {"TODO", "IN_PROGRESS", "NEEDS_REVIEW", "DELAYED", "DONE"})
            String status,
            @Schema(description = "이슈 진행률. 완료 조건을 채운 비율이다. 완료된 이슈만 100 이고, "
                    + "완료 조건을 다 채웠어도 이슈를 닫기 전에는 99 다.", example = "58")
            int progressRate,
            @Schema(description = "달성한 완료 조건 개수", example = "7")
            int checklistDoneCount,
            @Schema(description = "전체 완료 조건 개수", example = "12")
            int checklistTotalCount,
            @Schema(description = "담당자 이름. 프로젝트 멤버에서 찾지 못하면 null 이다.", example = "김호균")
            String assigneeName,
            @Schema(description = "이슈 처리 일자")
            LocalDate dueDate,
            @Schema(description = "이슈가 마지막으로 바뀐 시각. 이 순서로 정렬한다.")
            LocalDateTime updatedAt
    ) {
        public static KeyProgress of(IssueProgress progress) {
            return new KeyProgress(
                    progress.issueId(),
                    progress.title(),
                    progress.status(),
                    progress.progressRate(),
                    progress.checklistDoneCount(),
                    progress.checklistTotalCount(),
                    progress.assigneeName(),
                    progress.dueDate(),
                    progress.updatedAt()
            );
        }
    }

    @Schema(description = "사이클 이슈 집계")
    public record IssueSummary(
            @Schema(description = "완료된 이슈 개수", example = "19")
            int doneCount,
            @Schema(description = "취소된 이슈를 뺀 전체 개수. 진행률의 분모다.", example = "23")
            int totalCount,
            @Schema(description = "진행 중인 이슈 개수", example = "5")
            int inProgressCount,
            @Schema(description = "확인이 필요한 이슈 개수", example = "3")
            int needsReviewCount,
            @Schema(description = "마감일이 지나도록 끝나지 않은 이슈 개수. 매일 자동으로 갱신되며 "
                    + "아직 남은 일이라 진행률 분모에는 그대로 남는다.", example = "2")
            int delayedCount,
            @Schema(description = "취소된 이슈 개수", example = "1")
            int canceledCount
    ) {
        public static IssueSummary of(IssueStats stats) {
            return new IssueSummary(
                    stats.doneCount(),
                    stats.totalCount(),
                    stats.inProgressCount(),
                    stats.needsReviewCount(),
                    stats.delayedCount(),
                    stats.canceledCount()
            );
        }
    }

    public record NextCycle(
            Long cycleId,
            String name,
            LocalDate startDate
    ) {
        public static NextCycle of(Cycle cycle) {
            return new NextCycle(cycle.getId(), cycle.getName(), cycle.getStartDate());
        }
    }

    public record Updated(
            Long cycleId,
            String name,
            CycleStatus status,
            LocalDate startDate,
            LocalDate endDate,
            String goal
    ) {
        public static Updated of(Cycle cycle) {
            return new Updated(
                    cycle.getId(),
                    cycle.getName(),
                    cycle.getStatus(),
                    cycle.getStartDate(),
                    cycle.getEndDate(),
                    cycle.getGoal()
            );
        }
    }

    public record StatusChanged(
            Long cycleId,
            CycleStatus status,
            int movedIssueCount
    ) {
    }

    public record ActivityGroup(
            LocalDate date,
            String dateLabel,
            List<Activity> activities
    ) {
    }

    // 활동 유형마다 채워지는 필드가 달라 값이 없는 항목은 응답에서 제외한다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Activity(
            Long activityId,
            ActivityType type,
            LocalDateTime occurredAt,
            String actorName,
            String title,
            Long issueId,
            String issueTitle,
            String before,
            String after,
            String reason,
            String fileName,
            Long fileSize
    ) {
        public static Activity of(CycleActivity activity) {
            return new Activity(
                    activity.getId(),
                    activity.getType(),
                    activity.getOccurredAt(),
                    activity.getActorName(),
                    activity.getTitle(),
                    activity.getIssueId(),
                    activity.getIssueTitle(),
                    activity.getBeforeValue(),
                    activity.getAfterValue(),
                    activity.getReason(),
                    activity.getFileName(),
                    activity.getFileSize()
            );
        }
    }

    public record Analysis(
            Long analysisId,
            LocalDateTime analyzedAt,
            int progressRate,
            int previousProgressRate,
            String summary,
            List<Evidence> evidences,
            List<CheckNeeded> checkNeeded
    ) {
        public static Analysis of(CycleAiAnalysis analysis) {
            return new Analysis(
                    analysis.getId(),
                    analysis.getAnalyzedAt(),
                    analysis.getProgressRate(),
                    analysis.getPreviousProgressRate(),
                    analysis.getSummary(),
                    analysis.getEvidences().stream().map(Evidence::of).toList(),
                    analysis.getCheckNeeded().stream().map(CheckNeeded::of).toList()
            );
        }
    }

    public record Evidence(
            EvidenceSource source,
            String label,
            int referenceCount
    ) {
        public static Evidence of(AnalysisEvidence evidence) {
            return new Evidence(evidence.getSource(), evidence.getLabel(), evidence.getReferenceCount());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckNeeded(
            CheckNeededType type,
            String message,
            Long issueId
    ) {
        public static CheckNeeded of(AnalysisCheckNeeded checkNeeded) {
            return new CheckNeeded(checkNeeded.getType(), checkNeeded.getMessage(), checkNeeded.getIssueId());
        }
    }

    public record AnalysisJob(
            Long analysisId,
            AnalysisStatus status,
            int estimatedSeconds
    ) {
    }
}
