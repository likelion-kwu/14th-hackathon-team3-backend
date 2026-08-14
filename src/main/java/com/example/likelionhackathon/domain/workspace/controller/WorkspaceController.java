package com.example.likelionhackathon.domain.workspace.controller;

import com.example.likelionhackathon.domain.workspace.dto.WorkspaceRequest;
import com.example.likelionhackathon.domain.workspace.dto.WorkspaceResponse;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceMemberViewStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceStatus;
import com.example.likelionhackathon.domain.workspace.service.WorkspaceService;
import com.example.likelionhackathon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "워크스페이스")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @Operation(summary = "워크스페이스 생성")
    @PostMapping("/workspaces")
    public ResponseEntity<ApiResponse<WorkspaceResponse.Created>> create(
            @RequestBody WorkspaceRequest.Create request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("워크스페이스를 생성했습니다.", workspaceService.create(request)));
    }

    @Operation(summary = "워크스페이스 목록 조회")
    @GetMapping("/workspaces")
    public ApiResponse<List<WorkspaceResponse.Summary>> getWorkspaces(
            @RequestParam(required = false) WorkspaceStatus status
    ) {
        return ApiResponse.success("워크스페이스 목록을 조회했습니다.", workspaceService.getWorkspaces(status));
    }

    @Operation(summary = "워크스페이스 상세 조회")
    @GetMapping("/workspaces/{workspaceId}")
    public ApiResponse<WorkspaceResponse.Detail> getDetail(@PathVariable Long workspaceId) {
        return ApiResponse.success("워크스페이스 정보를 조회했습니다.", workspaceService.getDetail(workspaceId));
    }

    @Operation(summary = "워크스페이스 수정·보관")
    @PutMapping("/workspaces/{workspaceId}")
    public ApiResponse<WorkspaceResponse.Updated> update(
            @PathVariable Long workspaceId,
            @RequestBody WorkspaceRequest.Update request
    ) {
        return ApiResponse.success("워크스페이스 정보를 수정했습니다.", workspaceService.update(workspaceId, request));
    }

    @Operation(summary = "워크스페이스 멤버 조회")
    @GetMapping("/workspaces/{workspaceId}/members")
    public ApiResponse<WorkspaceResponse.Members> getMembers(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) WorkspaceMemberViewStatus status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(
                "워크스페이스 멤버를 조회했습니다.",
                workspaceService.getMembers(workspaceId, status, keyword)
        );
    }

    @Operation(summary = "내 워크스페이스 프로필 조회")
    @GetMapping("/workspaces/{workspaceId}/members/me/profile")
    public ApiResponse<WorkspaceResponse.Profile> getMyProfile(@PathVariable Long workspaceId) {
        return ApiResponse.success("내 프로필을 조회했습니다.", workspaceService.getMyProfile(workspaceId));
    }

    @Operation(summary = "내 워크스페이스 프로필 수정")
    @PatchMapping("/workspaces/{workspaceId}/members/me/profile")
    public ApiResponse<WorkspaceResponse.Profile> updateMyProfile(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceRequest.UpdateProfile request
    ) {
        return ApiResponse.success("프로필이 수정되었습니다.", workspaceService.updateMyProfile(workspaceId, request));
    }

    @Operation(summary = "워크스페이스 멤버 일괄 관리")
    @PutMapping("/workspaces/{workspaceId}/members")
    public ApiResponse<WorkspaceResponse.MembersManaged> manageMembers(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceRequest.ManageMembers request
    ) {
        return ApiResponse.success(
                "워크스페이스 멤버를 관리했습니다.",
                workspaceService.manageMembers(workspaceId, request)
        );
    }

    @Operation(summary = "워크스페이스 초대 생성")
    @PostMapping("/workspaces/{workspaceId}/invitations")
    public ResponseEntity<ApiResponse<WorkspaceResponse.InvitationCreated>> createInvitation(
            @PathVariable Long workspaceId,
            @RequestBody WorkspaceRequest.CreateInvitation request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        "워크스페이스 초대를 생성했습니다.",
                        workspaceService.createInvitation(workspaceId, request)
                ));
    }

    @Operation(summary = "워크스페이스 초대 참여")
    @PostMapping("/workspace-invitations/join")
    public ResponseEntity<ApiResponse<WorkspaceResponse.Joined>> join(
            @Valid @RequestBody WorkspaceRequest.JoinInvitation request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        "워크스페이스에 참여했습니다.",
                        workspaceService.join(request)
                ));
    }
}
