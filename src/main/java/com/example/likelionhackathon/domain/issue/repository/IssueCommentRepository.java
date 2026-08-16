package com.example.likelionhackathon.domain.issue.repository;

import com.example.likelionhackathon.domain.issue.entity.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

    /**
     * 오래된 댓글부터 반환한다.
     * 이슈를 연관관계로 들고 있어 파생 쿼리 이름으로는 경로가 잡히지 않으므로 직접 적는다.
     */
    @Query("""
            select c from IssueComment c
            where c.issue.id = :issueId
            order by c.createdAt asc, c.id asc
            """)
    List<IssueComment> findByIssueIdOrderByCreatedAtAscIdAsc(@Param("issueId") Long issueId);

    /**
     * 이슈 목록의 댓글 수 배지를 한 번에 채운다. 이슈마다 세면 목록 크기만큼 쿼리가 나간다.
     */
    @Query("""
            select c.issue.id as issueId, count(c) as count
            from IssueComment c
            where c.issue.id in :issueIds
            group by c.issue.id
            """)
    List<CommentCount> countGroupByIssueId(@Param("issueIds") Collection<Long> issueIds);

    /**
     * 이슈를 지우기 전에 먼저 비운다.
     * 이슈에 컬렉션으로 매달면 삭제할 때 댓글을 전부 메모리로 읽어야 해 한 번에 지운다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from IssueComment c where c.issue.id = :issueId")
    void deleteByIssueId(@Param("issueId") Long issueId);

    interface CommentCount {
        Long getIssueId();

        long getCount();
    }
}
