package com.example.likelionhackathon.domain.issue.repository;

import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {

    boolean existsByCycleId(Long cycleId);

    List<Issue> findByCycleIdAndStatusNotIn(Long cycleId, Collection<IssueStatus> statuses);

    List<Issue> findByCycleIdOrderByDueDateAscIdAsc(Long cycleId);

    @Query("""
            select i.status as status, count(i) as count
            from Issue i
            where i.cycleId = :cycleId
            group by i.status
            """)
    List<StatusCount> countGroupByStatus(Long cycleId);

    /**
     * 아직 완료되지 않은 이슈의 완료 조건 달성 현황.
     *
     * <p>진행률에 부분 진행을 반영하려면 이슈마다의 비율이 필요해 이슈 단위로 묶는다.
     * 완료 조건이 하나도 없는 이슈는 조인에서 빠지며, 진행률에 0으로 잡힌다.</p>
     */
    @Query("""
            select i.id as issueId,
                   sum(case when c.done = true then 1L else 0L end) as doneCount,
                   count(c) as totalCount
            from Issue i join i.checklist c
            where i.cycleId = :cycleId and i.status not in :excludedStatuses
            group by i.id
            """)
    List<ChecklistProgress> checklistProgressOfUnfinished(
            @Param("cycleId") Long cycleId,
            @Param("excludedStatuses") Collection<IssueStatus> excludedStatuses
    );

    interface StatusCount {
        IssueStatus getStatus();

        long getCount();
    }

    interface ChecklistProgress {
        Long getIssueId();

        long getDoneCount();

        long getTotalCount();
    }
}
