package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort.IssueProgress;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IssueProgressRateTest {

    @Test
    void progressFollowsCompletedChecklistRatio() {
        // 디자인의 "전체 12개 중 7개 완료 · 58%"
        assertThat(progress("IN_PROGRESS", 7, 12).progressRate()).isEqualTo(58);
    }

    @Test
    void finishedIssueIsAlwaysHundred() {
        assertThat(progress("DONE", 0, 0).progressRate()).isEqualTo(100);
        assertThat(progress("DONE", 3, 12).progressRate()).isEqualTo(100);
    }

    @Test
    void unfinishedIssueNeverReachesHundred() {
        // 완료 조건을 다 채워도 이슈를 닫기 전까지는 100 이 아니다.
        assertThat(progress("NEEDS_REVIEW", 12, 12).progressRate()).isEqualTo(99);
    }

    @Test
    void issueWithoutChecklistStaysZeroUntilDone() {
        assertThat(progress("TODO", 0, 0).progressRate()).isZero();
        assertThat(progress("DELAYED", 0, 0).progressRate()).isZero();
    }

    private IssueProgress progress(String status, int doneCount, int totalCount) {
        return new IssueProgress(
                1L, "결제 API v3 연동", status, "홍길동",
                LocalDate.of(2026, 8, 12), doneCount, totalCount, LocalDateTime.now());
    }
}
