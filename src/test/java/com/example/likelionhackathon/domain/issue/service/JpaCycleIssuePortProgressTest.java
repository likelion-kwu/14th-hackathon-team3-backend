package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort;
import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueChecklistItem;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import com.example.likelionhackathon.domain.issue.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 부분 진행 집계가 실제 DB 에서 나오는지 본다.
 * 단위 테스트는 포트를 목으로 두기 때문에 쿼리가 깨져도 통과한다.
 */
@SpringBootTest
@Transactional
class JpaCycleIssuePortProgressTest {

    private static final AtomicLong CYCLE_SEQUENCE = new AtomicLong(9_000);
    private static final Long ASSIGNEE_ID = 7L;

    @Autowired
    private JpaCycleIssuePort jpaCycleIssuePort;

    @Autowired
    private IssueRepository issueRepository;

    @Test
    void addsUpChecklistRatiosOfUnfinishedIssues() {
        Long cycleId = CYCLE_SEQUENCE.incrementAndGet();
        savedIssue(cycleId, "완료 조건 절반", IssueStatus.IN_PROGRESS, 4, 2);
        savedIssue(cycleId, "완료 조건 없음", IssueStatus.TODO, 0, 0);

        // 0.5 + 0 = 0.5
        assertThat(jpaCycleIssuePort.statsOf(cycleId).partialProgress()).isCloseTo(0.5, within(0.0001));
    }

    @Test
    void leavesOutIssuesThatAreAlreadyDoneOrCanceled() {
        Long cycleId = CYCLE_SEQUENCE.incrementAndGet();
        savedIssue(cycleId, "완료", IssueStatus.DONE, 4, 4);
        savedIssue(cycleId, "취소", IssueStatus.CANCELED, 4, 4);

        // 완료는 1 로 따로 세고 취소는 분모에서 빠지므로 부분 진행에는 잡히지 않는다.
        assertThat(jpaCycleIssuePort.statsOf(cycleId).partialProgress()).isZero();
    }

    @Test
    void countsPartialProgressIntoTheRate() {
        Long cycleId = CYCLE_SEQUENCE.incrementAndGet();
        savedIssue(cycleId, "완료", IssueStatus.DONE, 2, 2);
        savedIssue(cycleId, "절반 진행", IssueStatus.IN_PROGRESS, 2, 1);

        CycleIssuePort.IssueStats stats = jpaCycleIssuePort.statsOf(cycleId);

        assertThat(stats.totalCount()).isEqualTo(2);
        assertThat(stats.doneCount()).isEqualTo(1);
        // (1 + 0.5) / 2 = 75%. 완료 개수만 세면 50% 였다.
        assertThat(stats.progressRate()).isEqualTo(75);
    }

    @Test
    void recentProgressSkipsCanceledIssuesAndKeepsTheLimit() {
        Long cycleId = CYCLE_SEQUENCE.incrementAndGet();
        savedIssue(cycleId, "취소된 업무", IssueStatus.CANCELED, 4, 4);
        savedIssue(cycleId, "진행 중", IssueStatus.IN_PROGRESS, 12, 7);
        savedIssue(cycleId, "확인 필요", IssueStatus.NEEDS_REVIEW, 2, 2);

        List<CycleIssuePort.IssueProgress> progress = jpaCycleIssuePort.recentProgressOf(cycleId, 2);

        assertThat(progress).hasSize(2)
                .extracting(CycleIssuePort.IssueProgress::title)
                .doesNotContain("취소된 업무");
    }

    @Test
    void recentProgressCarriesChecklistCountsFromTheDatabase() {
        Long cycleId = CYCLE_SEQUENCE.incrementAndGet();
        savedIssue(cycleId, "보안 취약점 테스트", IssueStatus.IN_PROGRESS, 12, 7);

        CycleIssuePort.IssueProgress progress = jpaCycleIssuePort.recentProgressOf(cycleId, 5).get(0);

        assertThat(progress.checklistDoneCount()).isEqualTo(7);
        assertThat(progress.checklistTotalCount()).isEqualTo(12);
        assertThat(progress.progressRate()).isEqualTo(58);
        assertThat(progress.updatedAt()).isNotNull();
    }

    private void savedIssue(Long cycleId, String title, IssueStatus status, int checklistTotal, int checklistDone) {
        Issue issue = Issue.create(
                cycleId, title, "설명", IssuePriority.HIGH, ASSIGNEE_ID, LocalDate.of(2026, 8, 20));

        for (int i = 0; i < checklistTotal; i++) {
            issue.addChecklistItem(new IssueChecklistItem("조건 " + i, i < checklistDone, i));
        }
        if (status != IssueStatus.TODO) {
            issue.changeStatus(status);
        }

        issueRepository.save(issue);
    }
}
