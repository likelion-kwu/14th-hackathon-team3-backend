package com.example.likelionhackathon.domain.issue.entity;

import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IssueDelayTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    @Test
    void overdueIssueStillInHandBecomesDelayed() {
        Issue todo = issue(IssueStatus.TODO, TODAY.minusDays(1));
        Issue inProgress = issue(IssueStatus.IN_PROGRESS, TODAY.minusDays(10));

        assertThat(todo.markDelayedIfOverdue(TODAY)).isTrue();
        assertThat(inProgress.markDelayedIfOverdue(TODAY)).isTrue();
        assertThat(todo.getStatus()).isEqualTo(IssueStatus.DELAYED);
        assertThat(inProgress.getStatus()).isEqualTo(IssueStatus.DELAYED);
    }

    @Test
    void issueDueTodayIsNotDelayedYet() {
        // 마감일 당일은 아직 시간이 남았다.
        Issue issue = issue(IssueStatus.IN_PROGRESS, TODAY);

        assertThat(issue.markDelayedIfOverdue(TODAY)).isFalse();
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
    }

    @Test
    void issueWaitingForReviewKeepsItsStatus() {
        Issue issue = issue(IssueStatus.NEEDS_REVIEW, TODAY.minusDays(5));

        assertThat(issue.markDelayedIfOverdue(TODAY)).isFalse();
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.NEEDS_REVIEW);
    }

    @Test
    void closedIssueIsNeverPulledBackToDelayed() {
        Issue done = issue(IssueStatus.DONE, TODAY.minusDays(5));
        Issue canceled = issue(IssueStatus.CANCELED, TODAY.minusDays(5));

        assertThat(done.markDelayedIfOverdue(TODAY)).isFalse();
        assertThat(canceled.markDelayedIfOverdue(TODAY)).isFalse();
    }

    @Test
    void alreadyDelayedIssueIsNotTouchedAgain() {
        Issue issue = issue(IssueStatus.DELAYED, TODAY.minusDays(5));

        assertThat(issue.markDelayedIfOverdue(TODAY)).isFalse();
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.DELAYED);
    }

    private Issue issue(IssueStatus status, LocalDate dueDate) {
        Issue issue = Issue.create(1L, "결제 API 연동", "설명", IssuePriority.HIGH, 7L, dueDate);
        if (status != IssueStatus.TODO) {
            issue.changeStatus(status);
        }
        return issue;
    }
}
