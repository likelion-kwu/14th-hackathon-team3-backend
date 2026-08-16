package com.example.likelionhackathon.domain.issue.repository;

import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueComment;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 조회 쿼리가 실제 DB 에서 돌아가는지 본다.
 *
 * <p>서비스 테스트는 리포지토리를 목으로 두기 때문에 쿼리 자체가 깨져도 통과한다.
 * 이슈를 연관관계로 바꿨을 때 파생 쿼리 이름이 해석되지 않아 실제로 한 번 깨졌다.</p>
 */
@SpringBootTest
@Transactional
class IssueCommentPersistenceIntegrationTest {

    private static final Long CYCLE_ID = 1L;
    private static final Long AUTHOR_ID = 7L;

    @Autowired
    private IssueCommentRepository issueCommentRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Test
    void findsCommentsOfAnIssueInWrittenOrder() {
        Issue issue = savedIssue("파트너사 데이터 연동 확인");
        issueCommentRepository.save(IssueComment.write(issue, AUTHOR_ID, "첫 번째"));
        issueCommentRepository.save(IssueComment.write(issue, AUTHOR_ID, "두 번째"));

        assertThat(issueCommentRepository.findByIssueIdOrderByCreatedAtAscIdAsc(issue.getId()))
                .extracting(IssueComment::getContent)
                .containsExactly("첫 번째", "두 번째");
    }

    @Test
    void countsCommentsPerIssueInOneQuery() {
        Issue withTwo = savedIssue("댓글 둘");
        Issue withOne = savedIssue("댓글 하나");
        Issue withNone = savedIssue("댓글 없음");

        issueCommentRepository.save(IssueComment.write(withTwo, AUTHOR_ID, "a"));
        issueCommentRepository.save(IssueComment.write(withTwo, AUTHOR_ID, "b"));
        issueCommentRepository.save(IssueComment.write(withOne, AUTHOR_ID, "c"));

        Map<Long, Long> counts = issueCommentRepository
                .countGroupByIssueId(List.of(withTwo.getId(), withOne.getId(), withNone.getId()))
                .stream()
                .collect(Collectors.toMap(
                        IssueCommentRepository.CommentCount::getIssueId,
                        IssueCommentRepository.CommentCount::getCount));

        assertThat(counts).containsEntry(withTwo.getId(), 2L).containsEntry(withOne.getId(), 1L);
        // 댓글이 없는 이슈는 아예 행이 잡히지 않는다. 서비스가 0 으로 채운다.
        assertThat(counts).doesNotContainKey(withNone.getId());
    }

    @Test
    void deletesEveryCommentOfAnIssue() {
        Issue issue = savedIssue("지울 이슈");
        Issue other = savedIssue("남을 이슈");
        issueCommentRepository.save(IssueComment.write(issue, AUTHOR_ID, "지워질 댓글"));
        issueCommentRepository.save(IssueComment.write(other, AUTHOR_ID, "남을 댓글"));

        issueCommentRepository.deleteByIssueId(issue.getId());

        assertThat(issueCommentRepository.findByIssueIdOrderByCreatedAtAscIdAsc(issue.getId())).isEmpty();
        assertThat(issueCommentRepository.findByIssueIdOrderByCreatedAtAscIdAsc(other.getId())).hasSize(1);
    }

    @Test
    void exposesTheOwningIssueId() {
        Issue issue = savedIssue("이슈");
        IssueComment saved = issueCommentRepository.save(IssueComment.write(issue, AUTHOR_ID, "댓글"));

        assertThat(saved.getIssueId()).isEqualTo(issue.getId());
    }

    private Issue savedIssue(String title) {
        return issueRepository.save(Issue.create(
                CYCLE_ID, title, "설명", IssuePriority.HIGH, AUTHOR_ID, LocalDate.of(2026, 8, 20)));
    }
}
