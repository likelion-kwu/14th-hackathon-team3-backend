package com.example.likelionhackathon.domain.issue.repository;

import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {

    boolean existsByCycleId(Long cycleId);

    List<Issue> findByCycleIdAndStatusNotIn(Long cycleId, Collection<IssueStatus> statuses);

    @Query("""
            select i.status as status, count(i) as count
            from Issue i
            where i.cycleId = :cycleId
            group by i.status
            """)
    List<StatusCount> countGroupByStatus(Long cycleId);

    interface StatusCount {
        IssueStatus getStatus();

        long getCount();
    }
}
