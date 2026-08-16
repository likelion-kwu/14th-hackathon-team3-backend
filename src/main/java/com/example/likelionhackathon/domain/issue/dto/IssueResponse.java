package com.example.likelionhackathon.domain.issue.dto;

import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueAttachment;
import com.example.likelionhackathon.domain.issue.entity.IssueChecklistItem;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import com.example.likelionhackathon.domain.issue.service.IssueMemberPort.MemberProfile;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public final class IssueResponse {

    private IssueResponse() {
    }

    public record Summary(
            Long issueId,
            String title,
            IssueStatus status,
            IssuePriority priority,
            String assigneeName,
            LocalDate dueDate,
            int checklistDoneCount,
            int checklistTotalCount,
            int attachmentCount,
            long commentCount
    ) {
        public static Summary of(Issue issue, String assigneeName, long commentCount) {
            return new Summary(
                    issue.getId(),
                    issue.getTitle(),
                    issue.getStatus(),
                    issue.getPriority(),
                    assigneeName,
                    issue.getDueDate(),
                    issue.checklistDoneCount(),
                    issue.checklistTotalCount(),
                    issue.getAttachments().size(),
                    commentCount
            );
        }
    }

    public record Created(Long issueId) {
    }

    public record Detail(
            Long issueId,
            Long cycleId,
            String cycleName,
            String title,
            IssueStatus status,
            IssuePriority priority,
            String description,
            Assignee assignee,
            LocalDate dueDate,
            int checklistDoneCount,
            int checklistTotalCount,
            List<ChecklistItem> checklist,
            List<Attachment> attachments
    ) {
    }

    public record Assignee(
            Long userId,
            String name,
            String company,
            String team,
            String position
    ) {
        public static Assignee of(MemberProfile profile) {
            return new Assignee(
                    profile.userId(),
                    profile.name(),
                    profile.company(),
                    profile.team(),
                    profile.position()
            );
        }
    }

    public record ChecklistItem(
            Long itemId,
            String content,
            @JsonProperty("isDone") boolean isDone
    ) {
        public static ChecklistItem of(IssueChecklistItem item) {
            return new ChecklistItem(item.getId(), item.getContent(), item.isDone());
        }
    }

    public record Attachment(
            Long attachmentId,
            String fileName,
            Long fileSize,
            String fileUrl
    ) {
        public static Attachment of(IssueAttachment attachment) {
            return new Attachment(
                    attachment.getId(),
                    attachment.getFileName(),
                    attachment.getFileSize(),
                    attachment.getFileUrl()
            );
        }
    }

    public record Updated(
            Long issueId,
            Long cycleId,
            String title,
            IssueStatus status,
            IssuePriority priority,
            Long assigneeId,
            LocalDate dueDate,
            int checklistDoneCount,
            int checklistTotalCount
    ) {
        public static Updated of(Issue issue) {
            return new Updated(
                    issue.getId(),
                    issue.getCycleId(),
                    issue.getTitle(),
                    issue.getStatus(),
                    issue.getPriority(),
                    issue.getAssigneeId(),
                    issue.getDueDate(),
                    issue.checklistDoneCount(),
                    issue.checklistTotalCount()
            );
        }
    }

    public record StatusChanged(
            Long issueId,
            IssueStatus previousStatus,
            IssueStatus status,
            int cycleProgressRate
    ) {
    }

    public record ChecklistChecked(
            Long itemId,
            @JsonProperty("isDone") boolean isDone,
            int checklistDoneCount,
            int checklistTotalCount
    ) {
    }

    public record UploadedFile(
            String fileName,
            Long fileSize,
            String fileUrl
    ) {
    }
}
