package com.example.likelionhackathon.domain.issue.repository;

import com.example.likelionhackathon.domain.issue.entity.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

    List<IssueComment> findByIssueIdOrderByCreatedAtAscIdAsc(Long issueId);

    /**
     * 이슈 목록의 댓글 수 배지를 한 번에 채운다. 이슈마다 세면 목록 크기만큼 쿼리가 나간다.
     */
    @Query("""
            select c.issueId as issueId, count(c) as count
            from IssueComment c
            where c.issueId in :issueIds
            group by c.issueId
            """)
    List<CommentCount> countGroupByIssueId(@Param("issueIds") Collection<Long> issueIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from IssueComment c where c.issueId = :issueId")
    void deleteByIssueId(@Param("issueId") Long issueId);

    interface CommentCount {
        Long getIssueId();

        long getCount();
    }
}
