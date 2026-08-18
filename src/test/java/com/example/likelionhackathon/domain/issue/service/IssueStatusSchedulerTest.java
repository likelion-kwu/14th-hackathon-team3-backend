package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import com.example.likelionhackathon.domain.issue.repository.IssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueStatusSchedulerTest {

    @Mock
    private IssueRepository issueRepository;

    private IssueStatusScheduler issueStatusScheduler;

    @BeforeEach
    void setUp() {
        issueStatusScheduler = new IssueStatusScheduler(issueRepository);
    }

    @Test
    void marksOverdueIssuesDelayed() {
        LocalDate today = LocalDate.now();
        Issue overdue = issue(IssueStatus.IN_PROGRESS, today.minusDays(2));
        when(issueRepository.findByStatusInAndDueDateBefore(anyCollection(), any()))
                .thenReturn(List.of(overdue));

        issueStatusScheduler.markOverdueIssuesDelayed();

        assertThat(overdue.getStatus()).isEqualTo(IssueStatus.DELAYED);
    }

    @Test
    void leavesIssueThatIsStillWithinItsDueDate() {
        LocalDate today = LocalDate.now();
        // 쿼리가 넓게 잡아 와도 엔티티가 한 번 더 거른다.
        Issue onTime = issue(IssueStatus.TODO, today.plusDays(1));
        when(issueRepository.findByStatusInAndDueDateBefore(anyCollection(), any()))
                .thenReturn(List.of(onTime));

        issueStatusScheduler.markOverdueIssuesDelayed();

        assertThat(onTime.getStatus()).isEqualTo(IssueStatus.TODO);
    }

    private Issue issue(IssueStatus status, LocalDate dueDate) {
        Issue issue = Issue.create(1L, "결제 API 연동", "설명", IssuePriority.HIGH, 7L, dueDate);
        if (status != IssueStatus.TODO) {
            issue.changeStatus(status);
        }
        return issue;
    }
}
