package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort.IssueStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 진행률은 완료 개수만이 아니라 진행 중인 이슈가 완료 조건을 채운 만큼도 센다.
 */
class IssueStatsProgressTest {

    @Test
    void countsFinishedIssuesFully() {
        assertThat(new IssueStats(4, 2, 2, 0, 0, 0).progressRate()).isEqualTo(50);
    }

    @Test
    void countsPartiallyCheckedIssues() {
        // 완료 1건 + 완료 조건 7/12(0.58)를 채운 진행 중 1건 → (1 + 0.58) / 4
        IssueStats stats = new IssueStats(4, 1, 3, 0, 0, 7.0 / 12);

        assertThat(stats.progressRate()).isEqualTo(39);
    }

    @Test
    void ignoresCanceledIssuesInTheDenominator() {
        // 전체 5건 중 1건 취소 → 분모는 4
        assertThat(new IssueStats(4, 2, 2, 0, 1, 0).progressRate()).isEqualTo(50);
    }

    @Test
    void reachesHundredOnlyWhenEveryIssueIsDone() {
        assertThat(new IssueStats(3, 3, 0, 0, 0, 0).progressRate()).isEqualTo(100);
    }

    @Test
    void stopsAtNinetyNineWhileAnythingIsUnfinished() {
        // 완료 조건을 모두 채웠어도 이슈가 완료 처리되기 전에는 100 이 아니다.
        IssueStats stats = new IssueStats(3, 2, 1, 0, 0, 1.0);

        assertThat(stats.progressRate()).isEqualTo(99);
    }

    @Test
    void isZeroWhenThereAreNoIssues() {
        assertThat(IssueStats.EMPTY.progressRate()).isZero();
    }

    @Test
    void roundsDownSoRemainingWorkNeverLooksComplete() {
        // 199/200 은 99.5% 지만 남은 업무가 있으므로 99 로 보여야 한다.
        assertThat(new IssueStats(200, 199, 1, 0, 0, 0).progressRate()).isEqualTo(99);
    }
}
