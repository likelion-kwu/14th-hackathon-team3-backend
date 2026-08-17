package com.example.likelionhackathon.domain.issue.dto;

import com.example.likelionhackathon.domain.issue.entity.IssueComment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public class IssueCommentResponse {

    @Schema(description = "댓글 작성 결과")
    public record Created(
            @Schema(description = "생성된 댓글 식별자", example = "1")
            Long commentId
    ) {
    }

    @Schema(description = "댓글")
    public record Item(
            @Schema(description = "댓글 식별자", example = "1")
            Long commentId,
            @Schema(description = "작성자 식별자", example = "3")
            Long authorId,
            @Schema(description = "작성자 이름", example = "김호균")
            String authorName,
            @Schema(description = "댓글 내용", example = "내가 전달 받은 뒤 다시 공유해줄래?")
            String content,
            @Schema(description = "작성 일시")
            LocalDateTime createdAt,
            @Schema(description = "마지막 수정 일시")
            LocalDateTime updatedAt,
            @Schema(description = "수정된 적이 있는지", example = "false")
            boolean edited,
            @Schema(description = "요청자가 이 댓글을 수정·삭제할 수 있는지", example = "true")
            boolean editable
    ) {
        public static Item of(IssueComment comment, String authorName, boolean editable) {
            return new Item(
                    comment.getId(),
                    comment.getAuthorId(),
                    authorName,
                    comment.getContent(),
                    comment.getCreatedAt(),
                    comment.getUpdatedAt(),
                    comment.isEdited(),
                    editable
            );
        }
    }
}
