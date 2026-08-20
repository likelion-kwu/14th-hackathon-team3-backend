package com.example.likelionhackathon.domain.handover.service;

import com.example.likelionhackathon.domain.handover.dto.OpenAiHandoverResult;
import com.example.likelionhackathon.domain.handover.entity.CollaborationActivity;
import com.example.likelionhackathon.domain.handover.entity.Handover;
import com.example.likelionhackathon.domain.handover.entity.HandoverEvidence;
import com.example.likelionhackathon.domain.handover.entity.HandoverItem;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ReviewStatus;
import com.example.likelionhackathon.domain.handover.repository.HandoverRepository;
import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandoverGenerationService {

    private static final int EVIDENCE_SNIPPET_LENGTH = 500;

    private final HandoverRepository handoverRepository;
    private final IssueRepository issueRepository;
    private final OpenAiHandoverClient openAiHandoverClient;

    @Async
    @Transactional
    public void generate(Long handoverId, boolean preserveManualEdits) {
        Handover handover = handoverRepository.findOneById(handoverId).orElse(null);
        if (handover == null) {
            return;
        }

        try {
            handover.markGenerationRunning();
            List<CollaborationActivity> activities = issueRepository
                    .findByCycleIdOrderByDueDateAscIdAsc(handover.getCycleId())
                    .stream()
                    .map(issue -> toCycleActivity(handover, issue))
                    .toList();

            if (activities.isEmpty()) {
                // 이슈가 없는 사이클은 빈 초안을 열어 사용자가 직접 작성한다.
                // 새 인수인계는 빈 목록으로 완료되고, 재반영은 기존 초안을 유지한다.
                handover.completeGeneration(List.copyOf(handover.getItems()), OffsetDateTime.now());
                return;
            }

            OpenAiHandoverResult result = openAiHandoverClient.generate(activities);
            List<HandoverItem> generatedItems = toEntities(result, activities);

            if (preserveManualEdits) {
                handover.getItems().stream()
                        .filter(HandoverItem::isManuallyEdited)
                        .map(HandoverItem::copyForRefresh)
                        .forEach(generatedItems::add);
            }

            handover.completeGeneration(generatedItems, OffsetDateTime.now());
        } catch (Exception e) {
            log.warn("AI 인수인계 비동기 생성 실패: handoverId={}, reason={}", handoverId, e.getMessage());
            handover.failGeneration();
        }
    }

    private List<HandoverItem> toEntities(
            OpenAiHandoverResult result,
            List<CollaborationActivity> activities
    ) {
        List<HandoverItem> items = new ArrayList<>();
        for (OpenAiHandoverResult.GeneratedItem generated : result.items()) {
            List<HandoverEvidence> evidences = safeIndexes(generated.evidenceIndexes()).stream()
                    .filter(index -> index >= 0 && index < activities.size())
                    .distinct()
                    .map(activities::get)
                    .map(this::toEvidence)
                    .toList();

            ReviewStatus reviewStatus = generated.reviewStatus();
            if (evidences.isEmpty() && reviewStatus == ReviewStatus.VERIFIED) {
                reviewStatus = ReviewStatus.NEEDS_REVIEW;
            }

            items.add(new HandoverItem(
                    generated.category(),
                    generated.title(),
                    generated.description(),
                    generated.assigneeMemberId(),
                    reviewStatus,
                    false,
                    evidences
            ));
        }
        return items;
    }

    private CollaborationActivity toCycleActivity(Handover handover, Issue issue) {
        String checklist = issue.getChecklist().isEmpty()
                ? "없음"
                : issue.getChecklist().stream()
                .map(item -> "- [" + (item.isDone() ? "x" : " ") + "] " + item.getContent())
                .collect(java.util.stream.Collectors.joining("\n"));
        String content = """
                이슈 상태: %s
                우선순위: %s
                담당자 ID: %d
                마감일: %s
                설명: %s
                완료 조건:
                %s
                """.formatted(
                issue.getStatus(),
                issue.getPriority(),
                issue.getAssigneeId(),
                issue.getDueDate(),
                issue.getDescription(),
                checklist
        );
        OffsetDateTime occurredAt = issue.getUpdatedAt() == null
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : issue.getUpdatedAt().atOffset(ZoneOffset.UTC);
        return new CollaborationActivity(
                handover.getProjectId(),
                handover.getCycleId(),
                com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider.CYCLE,
                "이슈 #" + issue.getId() + ": " + issue.getTitle(),
                content,
                "/issues/" + issue.getId(),
                occurredAt
        );
    }

    private List<Integer> safeIndexes(List<Integer> indexes) {
        return indexes == null ? List.of() : indexes;
    }

    private HandoverEvidence toEvidence(CollaborationActivity activity) {
        String content = activity.getContent();
        String snippet = content.length() <= EVIDENCE_SNIPPET_LENGTH
                ? content
                : content.substring(0, EVIDENCE_SNIPPET_LENGTH);
        return new HandoverEvidence(
                activity.getProvider(),
                activity.getSourceName(),
                snippet,
                activity.getSourceUrl()
        );
    }
}
