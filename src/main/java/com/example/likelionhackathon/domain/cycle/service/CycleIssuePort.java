package com.example.likelionhackathon.domain.cycle.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
     * AI 분석에 넘길 이슈 목록. 집계만으로는 판단할 수 없는 것들
     * (무엇이 언제까지인데 어떤 상태로 멈춰 있는지)을 보려면 개별 이슈가 필요하다.
     */
    List<IssueBrief> briefsOf(Long cycleId);

    /**
     * 미완료 이슈를 다른 사이클로 옮기고 옮긴 개수를 반환한다.
     */
    int moveUnfinishedIssues(Long fromCycleId, Long toCycleId);

    /**
     * 사이클 화면의 '주요 진행 상황' 에 올릴 이슈. 최근에 움직인 순서로 준다.
     *
     * <p>취소된 이슈는 뺀다. 무엇이 굴러가고 있는지 보는 자리라 취소된 업무는 자리를 차지할 이유가 없다.</p>
     */
    List<IssueProgress> recentProgressOf(Long cycleId, int limit);

    /**
     * 이슈 한 건의 진행 상황. 디자인의 "전체 12개 중 7개 완료 · 58%" 한 줄에 해당한다.
     */
    record IssueProgress(
            Long issueId,
            String title,
            String status,
            String assigneeName,
            LocalDate dueDate,
            int checklistDoneCount,
            int checklistTotalCount,
            LocalDateTime updatedAt
    ) {
        private static final String DONE = "DONE";

        /**
         * 완료된 이슈는 100, 나머지는 완료 조건을 채운 비율이다.
         *
         * <p>사이클 진행률과 같은 규칙으로 내림한다. 반올림하면 11/12 를 채운 이슈가 100% 로 보여
         * 정말 끝난 이슈와 구분되지 않는다. 완료 조건이 없는 이슈는 완료 전까지 0 이다.</p>
         */
        public int progressRate() {
            if (DONE.equals(status)) {
                return 100;
            }
            if (checklistTotalCount == 0) {
                return 0;
            }
            return Math.min((int) Math.floor(checklistDoneCount * 100.0 / checklistTotalCount), 99);
        }
    }

    /**
     * AI 분석에 넘길 이슈 한 건. 본문 대신 판단에 필요한 것만 담는다.
     */
    record IssueBrief(
            Long issueId,
            String title,
            String status,
            String priority,
            LocalDate dueDate,
            int checklistDoneCount,
            int checklistTotalCount
    ) {
    }

    /**
     * @param totalCount      취소된 이슈를 제외한 전체 개수. 진행률의 분모다.
     * @param delayedCount    마감일이 지나도록 끝나지 않은 이슈 개수. 진행률 분모에는 그대로 남는다.
     * @param partialProgress 아직 완료되지 않은 이슈들의 완료 조건 달성 비율 합.
     *                        완료 조건 7/12 를 채운 이슈 하나가 0.58 로 잡힌다.
     */
    record IssueStats(
            int totalCount,
            int doneCount,
            int inProgressCount,
            int needsReviewCount,
            int delayedCount,
            int canceledCount,
            double partialProgress
    ) {
        public static final IssueStats EMPTY = new IssueStats(0, 0, 0, 0, 0, 0, 0);

        /**
         * 완료된 이슈는 1, 진행 중인 이슈는 완료 조건을 채운 만큼 센다.
         *
         * <p>완료 여부만 세면 완료 조건을 12개 중 7개까지 채운 이슈가 0 으로 잡혀,
         * 사이클 내내 일하고도 진행률이 그대로인 것처럼 보인다.
         * 화면이 이슈마다 "전체 12개 중 7개 완료 58%" 를 보여주는 것과도 어긋난다.</p>
         *
         * <p>내림으로 계산한다. 반올림하면 199/200 처럼 아직 남은 업무가 있는데도
         * 100%로 보여 완료된 사이클과 구분되지 않는다.</p>
         */
        public int progressRate() {
            if (totalCount == 0) {
                return 0;
            }

            int rate = (int) Math.floor((doneCount + partialProgress) * 100 / totalCount);
            // 부분 진행만으로는 100%가 될 수 없다. 전부 완료됐을 때만 100 이다.
            return (doneCount == totalCount) ? 100 : Math.min(rate, 99);
        }
    }
}
