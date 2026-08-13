package com.example.likelionhackathon.domain.issue.controller;

import com.example.likelionhackathon.domain.issue.dto.IssueRequest;
import com.example.likelionhackathon.domain.issue.dto.IssueResponse;
import com.example.likelionhackathon.domain.issue.service.IssueFileService;
import com.example.likelionhackathon.domain.issue.service.IssueService;
import com.example.likelionhackathon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "이슈")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class IssueController {

    private final IssueService issueService;
    private final IssueFileService issueFileService;

    @Operation(summary = "이슈 리스트 조회")
    @GetMapping("/cycles/{cycleId}/issues")
    public ApiResponse<List<IssueResponse.Summary>> getIssues(
            @PathVariable Long cycleId,
            // 잘못된 값일 때 400ISSUE 로 응답하기 위해 문자열로 받아 서비스에서 변환한다.
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                issueService.getIssues(cycleId, status, priority, assigneeId, keyword, sort, page, size));
    }

    @Operation(summary = "이슈 생성")
    @PostMapping("/issues")
    public ResponseEntity<ApiResponse<IssueResponse.Created>> create(
            @RequestBody IssueRequest.Create request
    ) {
        IssueResponse.Created response = issueService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("리소스 생성이 완료되었습니다.", response));
    }

    @Operation(summary = "이슈 상세 조회")
    @GetMapping("/issues/{issueId}")
    public ApiResponse<IssueResponse.Detail> getDetail(@PathVariable Long issueId) {
        return ApiResponse.success(issueService.getDetail(issueId));
    }

    @Operation(summary = "이슈 수정")
    @PutMapping("/issues/{issueId}")
    public ApiResponse<IssueResponse.Updated> update(
            @PathVariable Long issueId,
            @RequestBody IssueRequest.Update request
    ) {
        return ApiResponse.success(issueService.update(issueId, request));
    }

    @Operation(summary = "이슈 상태 변경")
    @PutMapping("/issues/{issueId}/status")
    public ApiResponse<IssueResponse.StatusChanged> changeStatus(
            @PathVariable Long issueId,
            @RequestBody IssueRequest.ChangeStatus request
    ) {
        return ApiResponse.success(issueService.changeStatus(issueId, request));
    }

    @Operation(summary = "완료 조건 체크 변경")
    @PutMapping("/issues/{issueId}/checklist/{itemId}")
    public ApiResponse<IssueResponse.ChecklistChecked> checkItem(
            @PathVariable Long issueId,
            @PathVariable Long itemId,
            @RequestBody IssueRequest.CheckItem request
    ) {
        return ApiResponse.success(issueService.checkItem(issueId, itemId, request));
    }

    @Operation(summary = "S3 파일 업로드")
    @PostMapping(value = "/issues/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<IssueResponse.UploadedFile>>> uploadFiles(
            @RequestPart("files") List<MultipartFile> files
    ) {
        List<IssueResponse.UploadedFile> response = issueFileService.upload(files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("리소스 생성이 완료되었습니다.", response));
    }

    @Operation(summary = "이슈 삭제")
    @DeleteMapping("/issues/{issueId}")
    public ApiResponse<Void> delete(@PathVariable Long issueId) {
        issueService.delete(issueId);
        return ApiResponse.success(null);
    }
}
