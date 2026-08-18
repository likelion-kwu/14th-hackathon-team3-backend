package com.example.likelionhackathon.domain.issue.entity;

import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long cycleId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuePriority priority;

    // 회원 도메인이 아직 없어 식별자만 보관한다.
    @Column(nullable = false)
    private Long assigneeId;

    @Column(nullable = false)
    private LocalDate dueDate;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<IssueChecklistItem> checklist = new ArrayList<>();

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<IssueAttachment> attachments = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public static Issue create(
            Long cycleId,
            String title,
            String description,
            IssuePriority priority,
            Long assigneeId,
            LocalDate dueDate
    ) {
        Issue issue = new Issue();
        issue.cycleId = cycleId;
        issue.title = title;
        issue.description = description;
        issue.priority = priority;
        issue.assigneeId = assigneeId;
        issue.dueDate = dueDate;
        issue.status = IssueStatus.TODO;
        return issue;
    }

    public void update(
            Long cycleId,
            String title,
            String description,
            IssuePriority priority,
            Long assigneeId,
            LocalDate dueDate
    ) {
        this.cycleId = cycleId;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.assigneeId = assigneeId;
        this.dueDate = dueDate;
    }

    public void changeStatus(IssueStatus next) {
        this.status = next;
    }

    /**
     * 마감일이 지났는데 아직 끝나지 않았으면 지연으로 표시한다.
     *
     * <p>이슈를 지연으로 바꾸는 화면이 없어서, 아무도 손대지 않으면 마감일이 한참 지나도
     * 계속 '할 일' 로 남는다. 날짜가 지나면 스스로 넘어간다.</p>
     *
     * <p>확인 필요는 건드리지 않는다. 사람의 답을 기다리는 상태라 지연으로 덮으면
     * 무엇을 기다리는 중이었는지가 사라진다.</p>
     *
     * @return 상태가 바뀌었으면 true
     */
    public boolean markDelayedIfOverdue(LocalDate today) {
        if (status != IssueStatus.TODO && status != IssueStatus.IN_PROGRESS) {
            return false;
        }
        if (dueDate == null || !dueDate.isBefore(today)) {
            return false;
        }
        this.status = IssueStatus.DELAYED;
        return true;
    }

    public void moveToCycle(Long targetCycleId) {
        this.cycleId = targetCycleId;
    }

    public void addChecklistItem(IssueChecklistItem item) {
        item.attachTo(this);
        checklist.add(item);
    }

    public void removeChecklistItemsNotIn(Set<Long> retainedItemIds) {
        checklist.removeIf(item -> item.getId() != null && !retainedItemIds.contains(item.getId()));
    }

    public Optional<IssueChecklistItem> findChecklistItem(Long itemId) {
        return checklist.stream()
                .filter(item -> item.getId() != null && item.getId().equals(itemId))
                .findFirst();
    }

    public void replaceAttachments(List<IssueAttachment> replacement) {
        attachments.clear();
        replacement.forEach(attachment -> {
            attachment.attachTo(this);
            attachments.add(attachment);
        });
    }

    public int checklistTotalCount() {
        return checklist.size();
    }

    public int checklistDoneCount() {
        return (int) checklist.stream().filter(IssueChecklistItem::isDone).count();
    }

    public boolean isAllChecklistDone() {
        return checklist.stream().allMatch(IssueChecklistItem::isDone);
    }

    public boolean isClosed() {
        return status.isClosed();
    }
}
