package com.example.likelionhackathon.domain.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class IssueCommentRequest {

    @Schema(description = "댓글 작성")
    public record Write(
            @Schema(description = "댓글 내용", example = "내가 전달 받은 뒤 다시 공유해줄래?")
            String content
    ) {
    }

    @Schema(description = "댓글 수정")
    public record Edit(
            @Schema(description = "댓글 내용", example = "확인 후 공유하겠습니다.")
            String content
    ) {
    }
}
