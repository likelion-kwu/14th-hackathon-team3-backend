package com.example.likelionhackathon.domain.cycle.service;

/**
 * 사이클이 이슈 도메인에 물어봐야 하는 것들을 모아둔 연결부.
 *
 * <p>사이클 명세의 진행률 · 상태별 집계 · 미완료 이슈 이관은 모두 이슈 데이터에 의존한다.
 * 두 도메인이 서로를 직접 참조하지 않도록 이 인터페이스로 끊고,
 * 이슈 도메인의 {@code JpaCycleIssuePort} 가 구현한다.</p>
 */
public interface CycleIssuePort {

    IssueStats statsOf(Long cycleId);

    boolean hasAnyIssue(Long cycleId);

    /**
     * 미완료 이슈를 다른 사이클로 옮기고 옮긴 개수를 반환한다.
     */
    int moveUnfinishedIssues(Long fromCycleId, Long toCycleId);

    /**
     * @param totalCount 취소된 이슈를 제외한 전체 개수. 진행률의 분모다.
     */
    record IssueStats(
            int totalCount,
            int doneCount,
            int inProgressCount,
            int needsReviewCount,
            int canceledCount
    ) {
        public static final IssueStats EMPTY = new IssueStats(0, 0, 0, 0, 0);

        /**
         * 내림으로 계산한다. 반올림하면 199/200 처럼 아직 남은 업무가 있는데도
         * 100%로 보여 완료된 사이클과 구분되지 않는다.
         */
        public int progressRate() {
            if (totalCount == 0) {
                return 0;
            }
            return (int) ((long) doneCount * 100 / totalCount);
        }
    }
}
