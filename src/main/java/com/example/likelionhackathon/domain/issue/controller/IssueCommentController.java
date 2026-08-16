package com.example.likelionhackathon.domain.issue.controller;

import com.example.likelionhackathon.domain.issue.dto.IssueCommentRequest;
import com.example.likelionhackathon.domain.issue.dto.IssueCommentResponse;
import com.example.likelionhackathon.domain.issue.service.IssueCommentService;
import com.example.likelionhackathon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "이슈 댓글")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class IssueCommentController {

    private final IssueCommentService issueCommentService;

    @Operation(summary = "댓글 목록 조회")
    @GetMapping("/issues/{issueId}/comments")
    public ApiResponse<List<IssueCommentResponse.Item>> getComments(@PathVariable Long issueId) {
        return ApiResponse.success(issueCommentService.getComments(issueId));
    }

    @Operation(summary = "댓글 작성")
    @PostMapping("/issues/{issueId}/comments")
    public ResponseEntity<ApiResponse<IssueCommentResponse.Created>> write(
            @PathVariable Long issueId,
            @RequestBody IssueCommentRequest.Write request
    ) {
        IssueCommentResponse.Created response = issueCommentService.write(issueId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("리소스 생성이 완료되었습니다.", response));
    }

    @Operation(summary = "댓글 수정")
    @PutMapping("/comments/{commentId}")
    public ApiResponse<IssueCommentResponse.Item> edit(
            @PathVariable Long commentId,
            @RequestBody IssueCommentRequest.Edit request
    ) {
        return ApiResponse.success(issueCommentService.edit(commentId, request));
    }

    @Operation(summary = "댓글 삭제")
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> delete(@PathVariable Long commentId) {
        issueCommentService.delete(commentId);
        return ApiResponse.success(null);
    }
}
