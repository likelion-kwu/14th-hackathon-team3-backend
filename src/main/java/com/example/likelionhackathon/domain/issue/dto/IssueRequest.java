package com.example.likelionhackathon.domain.issue.dto;

import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * 명세가 필수값 누락을 400ISSUE 로 응답하도록 정의해서 @Valid 대신
 * IssueService 에서 직접 검증한다. Bean Validation 을 쓰면 공통 400INVALID_INPUT_VALUE 가 나간다.
 */
public final class IssueRequest {

    private IssueRequest() {
    }

    public record Create(
            Long cycleId,
            String title,
            IssuePriority priority,
            String description,
            List<String> checklist,
            Long assigneeId,
            LocalDate dueDate,
            List<String> attachments
    ) {
        public List<String> safeChecklist() {
            return checklist == null ? List.of() : checklist;
        }

        public List<String> safeAttachments() {
            return attachments == null ? List.of() : attachments;
        }
    }

    public record Update(
            Long cycleId,
            String title,
            IssuePriority priority,
            String description,
            List<ChecklistItem> checklist,
            Long assigneeId,
            LocalDate dueDate,
            List<String> attachments
    ) {
        public List<ChecklistItem> safeChecklist() {
            return checklist == null ? List.of() : checklist;
        }

        public List<String> safeAttachments() {
            return attachments == null ? List.of() : attachments;
        }
    }

    /**
     * itemId 가 없으면 신규 추가, 배열에서 빠진 기존 항목은 삭제된다.
     */
    public record ChecklistItem(
            Long itemId,
            String content,
            Boolean isDone
    ) {
        public boolean done() {
            return Boolean.TRUE.equals(isDone);
        }
    }

    public record ChangeStatus(
            IssueStatus status,
            String comment
    ) {
    }

    public record CheckItem(
            Boolean isDone
    ) {
    }
}
