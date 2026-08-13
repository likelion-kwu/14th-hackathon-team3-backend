package com.example.likelionhackathon.domain.cycle.controller;

import com.example.likelionhackathon.domain.cycle.dto.CycleRequest;
import com.example.likelionhackathon.domain.cycle.dto.CycleResponse;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import com.example.likelionhackathon.domain.cycle.service.CycleActivityService;
import com.example.likelionhackathon.domain.cycle.service.CycleAiAnalysisService;
import com.example.likelionhackathon.domain.cycle.service.CycleService;
import com.example.likelionhackathon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "사이클")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CycleController {

    private final CycleService cycleService;
    private final CycleActivityService cycleActivityService;
    private final CycleAiAnalysisService cycleAiAnalysisService;

    @Operation(summary = "사이클 리스트 조회")
    @GetMapping("/projects/{projectId}/cycles")
    public ApiResponse<List<CycleResponse.Summary>> getCycles(
            @PathVariable Long projectId,
            @RequestParam(required = false) CycleStatus status
    ) {
        return ApiResponse.success(cycleService.getCycles(projectId, status));
    }

    @Operation(summary = "사이클 생성")
    @PostMapping("/projects/{projectId}/cycles")
    public ResponseEntity<ApiResponse<CycleResponse.Created>> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CycleRequest.Create request
    ) {
        CycleResponse.Created response = cycleService.create(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("리소스 생성이 완료되었습니다.", response));
    }

    @Operation(summary = "사이클 상세 조회")
    @GetMapping("/cycles/{cycleId}")
    public ApiResponse<CycleResponse.Detail> getDetail(@PathVariable Long cycleId) {
        return ApiResponse.success(cycleService.getDetail(cycleId));
    }

    @Operation(summary = "사이클 수정")
    @PutMapping("/cycles/{cycleId}")
    public ApiResponse<CycleResponse.Updated> update(
            @PathVariable Long cycleId,
            @Valid @RequestBody CycleRequest.Update request
    ) {
        return ApiResponse.success(cycleService.update(cycleId, request));
    }

    @Operation(summary = "사이클 상태 변경")
    @PutMapping("/cycles/{cycleId}/status")
    public ApiResponse<CycleResponse.StatusChanged> changeStatus(
            @PathVariable Long cycleId,
            @Valid @RequestBody CycleRequest.ChangeStatus request
    ) {
        return ApiResponse.success(cycleService.changeStatus(cycleId, request));
    }

    @Operation(summary = "사이클 삭제")
    @DeleteMapping("/cycles/{cycleId}")
    public ApiResponse<Void> delete(@PathVariable Long cycleId) {
        cycleService.delete(cycleId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "사이클 활동 기록 조회")
    @GetMapping("/cycles/{cycleId}/activities")
    public ApiResponse<List<CycleResponse.ActivityGroup>> getActivities(
            @PathVariable Long cycleId,
            // 잘못된 값일 때 400CYCLE 로 응답하기 위해 문자열로 받아 서비스에서 변환한다.
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(cycleActivityService.getActivities(cycleId, type, page, size));
    }

    @Operation(summary = "사이클 AI 분석 조회")
    @GetMapping("/cycles/{cycleId}/ai-analysis")
    public ApiResponse<CycleResponse.Analysis> getAnalysis(@PathVariable Long cycleId) {
        return ApiResponse.success(cycleAiAnalysisService.getAnalysis(cycleId));
    }

    @Operation(summary = "사이클 AI 분석 재실행")
    @PostMapping("/cycles/{cycleId}/ai-analysis")
    public ResponseEntity<ApiResponse<CycleResponse.AnalysisJob>> runAnalysis(
            @PathVariable Long cycleId,
            @RequestBody(required = false) CycleRequest.RunAnalysis request
    ) {
        CycleResponse.AnalysisJob response = cycleAiAnalysisService.runAnalysis(cycleId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.accepted("AI 분석 요청이 접수되었습니다.", response));
    }
}
