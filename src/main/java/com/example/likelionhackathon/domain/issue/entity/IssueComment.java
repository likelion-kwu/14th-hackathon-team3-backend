package com.example.likelionhackathon.domain.issue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 이슈에 달리는 댓글.
 *
 * <p>대댓글은 두지 않는다. 디자인에 답글 UI 가 없고, 필요해지면 부모 식별자만 더하면 된다.</p>
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueComment {

    /** 활동 기록에 인용할 때 잘라 넣는 길이. */
    public static final int EXCERPT_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long issueId;

    /** 작성자. 회원이 아니라 프로젝트 멤버 식별자다. */
    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 2000)
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정한 적이 있을 때만 채운다.
     * 생성 시각과 비교하는 방식은 두 시각이 나노초 차이로 갈려 갓 쓴 댓글도 수정됨으로 보인다.
     */
    private LocalDateTime updatedAt;

    public static IssueComment write(Long issueId, Long authorId, String content) {
        IssueComment comment = new IssueComment();
        comment.issueId = issueId;
        comment.authorId = authorId;
        comment.content = content;
        return comment;
    }

    public void edit(String content) {
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEdited() {
        return updatedAt != null;
    }

    public boolean isWrittenBy(Long memberId) {
        return authorId.equals(memberId);
    }

    /**
     * 활동 기록에 인용할 짧은 본문. 긴 댓글은 잘라서 말줄임표를 붙인다.
     */
    public String excerpt() {
        return content.length() <= EXCERPT_LENGTH
                ? content
                : content.substring(0, EXCERPT_LENGTH) + "…";
    }
}
