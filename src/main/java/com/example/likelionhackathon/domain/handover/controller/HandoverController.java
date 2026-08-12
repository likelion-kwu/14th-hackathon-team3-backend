package com.example.likelionhackathon.domain.handover.controller;

import com.example.likelionhackathon.domain.handover.dto.HandoverRequest;
import com.example.likelionhackathon.domain.handover.dto.HandoverResponse;
import com.example.likelionhackathon.domain.handover.service.HandoverService;
import com.example.likelionhackathon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 인수인계")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class HandoverController {

    private final HandoverService handoverService;

    @Operation(summary = "AI 인수인계 초안 생성")
    @PostMapping("/projects/{projectId}/cycles/{cycleId}/handovers")
    public ResponseEntity<ApiResponse<HandoverResponse.GenerationJob>> generateDraft(
            @PathVariable Long projectId,
            @PathVariable Long cycleId,
            @Valid @RequestBody HandoverRequest.GenerateDraft request
    ) {
        HandoverResponse.GenerationJob response = handoverService.generateDraft(projectId, cycleId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.accepted("AI 인수인계 초안 생성을 시작했습니다.", response));
    }

    @Operation(summary = "AI 인수인계 전체 조회")
    @GetMapping("/handovers/{handoverId}")
    public ApiResponse<HandoverResponse.Detail> getDetail(@PathVariable Long handoverId) {
        return ApiResponse.success(handoverService.getDetail(handoverId));
    }

    @Operation(summary = "AI 최신 활동 재반영")
    @PostMapping("/handovers/{handoverId}/refresh")
    public ResponseEntity<ApiResponse<HandoverResponse.GenerationJob>> refresh(
            @PathVariable Long handoverId,
            @RequestBody(required = false) HandoverRequest.Refresh request
    ) {
        HandoverResponse.GenerationJob response = handoverService.refresh(handoverId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.accepted("최신 활동 반영을 시작했습니다.", response));
    }

    @Operation(summary = "인수인계 초안 일괄 저장")
    @PutMapping("/handovers/{handoverId}/draft")
    public ApiResponse<HandoverResponse.DraftSaved> saveDraft(
            @PathVariable Long handoverId,
            @Valid @RequestBody HandoverRequest.SaveDraft request
    ) {
        return ApiResponse.success("인수인계 초안을 저장했습니다.", handoverService.saveDraft(handoverId, request));
    }

    @Operation(summary = "인수인계 전달")
    @PostMapping("/handovers/{handoverId}/deliver")
    public ResponseEntity<ApiResponse<HandoverResponse.Delivered>> deliver(
            @PathVariable Long handoverId,
            @Valid @RequestBody HandoverRequest.Deliver request
    ) {
        HandoverResponse.Delivered response = handoverService.deliver(handoverId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.accepted("인수인계 전달이 예약되었습니다.", response));
    }
}
