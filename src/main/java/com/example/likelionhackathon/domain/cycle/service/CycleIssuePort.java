package com.example.likelionhackathon.domain.cycle.service;

/**
 * 사이클이 이슈 도메인에 물어봐야 하는 것들을 모아둔 연결부.
 *
 * <p>사이클 명세의 진행률 · 상태별 집계 · 미완료 이슈 이관은 모두 이슈 데이터에 의존하는데,
 * 이슈 도메인은 별도 이슈(#8)에서 구현한다. 그때까지는 {@link EmptyCycleIssuePort} 가
 * 0을 반환하며, #8에서 JPA 구현체로 교체한다.</p>
 */
public interface CycleIssuePort {

    IssueStats statsOf(Long cycleId);

    boolean hasAnyIssue(Long cycleId);

    /**
     * 미완료 이슈를 다른 사이클로 옮기고 옮긴 개수를 반환한다.
     */
    int moveUnfinishedIssues(Long fromCycleId, Long toCycleId);

    record IssueStats(
            int totalCount,
            int doneCount,
            int inProgressCount,
            int needsReviewCount,
            int canceledCount
    ) {
        public static final IssueStats EMPTY = new IssueStats(0, 0, 0, 0, 0);

        public int progressRate() {
            if (totalCount == 0) {
                return 0;
            }
            return Math.round((float) doneCount * 100 / totalCount);
        }
    }
}
