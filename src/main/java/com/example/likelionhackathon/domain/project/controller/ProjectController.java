package com.example.likelionhackathon.domain.project.controller;

import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberViewStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.domain.project.service.ProjectIntegrationService;
import com.example.likelionhackathon.domain.project.service.ProjectMemberService;
import com.example.likelionhackathon.domain.project.service.ProjectOAuthService;
import com.example.likelionhackathon.domain.project.service.ProjectService;
import com.example.likelionhackathon.domain.project.service.ProjectTeamService;
import com.example.likelionhackathon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "프로젝트")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final ProjectTeamService projectTeamService;
    private final ProjectIntegrationService projectIntegrationService;
    private final ProjectOAuthService projectOAuthService;

    @Operation(summary = "프로젝트 생성")
    @PostMapping("/workspaces/{workspaceId}/projects")
    public ResponseEntity<ApiResponse<ProjectResponse.Created>> create(
            @PathVariable Long workspaceId,
            @RequestBody ProjectRequest.Create request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        "프로젝트를 생성했습니다.",
                        projectService.create(workspaceId, request)
                ));
    }

    @Operation(summary = "프로젝트 목록 조회")
    @GetMapping("/workspaces/{workspaceId}/projects")
    public ApiResponse<List<ProjectResponse.Summary>> getProjects(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(
                "프로젝트 목록을 조회했습니다.",
                projectService.getProjects(workspaceId, status, keyword)
        );
    }

    @Operation(summary = "프로젝트 상세·전체 설정 조회")
    @GetMapping("/projects/{projectId}")
    public ApiResponse<ProjectResponse.Detail> getDetail(@PathVariable Long projectId) {
        return ApiResponse.success(
                "프로젝트 정보를 조회했습니다.",
                projectService.getDetail(projectId)
        );
    }

    @Operation(summary = "프로젝트 수정·종료")
    @PutMapping("/projects/{projectId}")
    public ApiResponse<ProjectResponse.Updated> update(
            @PathVariable Long projectId,
            @RequestBody ProjectRequest.Update request
    ) {
        return ApiResponse.success(
                "프로젝트를 수정했습니다.",
                projectService.update(projectId, request)
        );
    }

    @Operation(summary = "프로젝트 멤버 조회")
    @GetMapping("/projects/{projectId}/members")
    public ApiResponse<ProjectResponse.MemberDirectory> getMembers(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) ProjectMemberViewStatus status
    ) {
        return ApiResponse.success(
                "프로젝트 멤버를 조회했습니다.",
                projectMemberService.getMembers(projectId, companyId, status)
        );
    }

    @Operation(summary = "프로젝트 멤버 일괄 관리")
    @PutMapping("/projects/{projectId}/members")
    public ApiResponse<ProjectResponse.MembersManaged> manageMembers(
            @PathVariable Long projectId,
            @RequestBody ProjectRequest.ManageMembers request
    ) {
        return ApiResponse.success(
                "프로젝트 멤버를 관리했습니다.",
                projectMemberService.manageMembers(projectId, request)
        );
    }

    @Operation(summary = "프로젝트 팀 설정 일괄 저장")
    @PutMapping("/projects/{projectId}/team-settings")
    public ApiResponse<ProjectResponse.TeamSettingsSaved> saveTeamSettings(
            @PathVariable Long projectId,
            @RequestBody ProjectRequest.SaveTeamSettings request
    ) {
        return ApiResponse.success(
                "프로젝트 팀 설정을 저장했습니다.",
                projectTeamService.saveSettings(projectId, request)
        );
    }

    @Operation(summary = "프로젝트 외부 연동 일괄 관리")
    @PutMapping("/projects/{projectId}/integrations")
    public ApiResponse<ProjectResponse.IntegrationsManaged> manageIntegrations(
            @PathVariable Long projectId,
            @RequestBody ProjectRequest.ManageIntegrations request
    ) {
        return ApiResponse.success(
                "프로젝트 외부 연동을 관리했습니다.",
                projectIntegrationService.manage(projectId, request)
        );
    }

    @Operation(summary = "프로젝트 외부 연동 OAuth 시작")
    @PostMapping("/projects/{projectId}/integrations/{provider}/oauth/start")
    public ApiResponse<ProjectResponse.OAuthStarted> startIntegrationOAuth(
            @PathVariable Long projectId,
            @PathVariable IntegrationProvider provider
    ) {
        return ApiResponse.success(
                "외부 연동 인증 URL을 생성했습니다.",
                projectOAuthService.start(projectId, provider)
        );
    }

    @Operation(summary = "프로젝트 외부 연동 OAuth 완료")
    @PostMapping("/projects/{projectId}/integrations/{provider}/oauth/complete")
    public ApiResponse<ProjectResponse.OAuthConnected> completeIntegrationOAuth(
            @PathVariable Long projectId,
            @PathVariable IntegrationProvider provider,
            @RequestBody ProjectRequest.CompleteIntegrationOAuth request
    ) {
        return ApiResponse.success(
                "외부 서비스를 연결했습니다.",
                projectOAuthService.complete(projectId, provider, request)
        );
    }
}
