package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.Project;
import com.example.likelionhackathon.domain.project.entity.ProjectCompany;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.AccessScope;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberActionType;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberViewStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectMember;
import com.example.likelionhackathon.domain.project.entity.ProjectTeam;
import com.example.likelionhackathon.domain.project.repository.ProjectMemberRepository;
import com.example.likelionhackathon.domain.project.repository.ProjectTeamRepository;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceMemberStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import com.example.likelionhackathon.global.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectAccessService projectAccessService;
    private final ProjectMemberRepository memberRepository;
    private final ProjectTeamRepository teamRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public ProjectResponse.MemberDirectory getMembers(
            Long projectId,
            Long companyId,
            ProjectMemberViewStatus status
    ) {
        projectAccessService.findProject(projectId);
        projectAccessService.requireAccess(projectId);

        boolean includeMembers = status == null || status != ProjectMemberViewStatus.INVITED;
        List<ProjectResponse.Member> members = includeMembers
                ? memberRepository.findAllByProjectIdOrderByIdAsc(projectId).stream()
                .filter(member -> companyId == null || companyId.equals(member.getCompanyId()))
                .filter(member -> matchesStatus(member, status))
                .map(member -> new ProjectResponse.Member(
                        member.getId(),
                        member.getName(),
                        member.getCompanyName(),
                        member.getTeam().getTeamName(),
                        member.getRole(),
                        member.getAccessScope(),
                        member.getStatus()
                ))
                .toList()
                : List.of();
        List<ProjectResponse.PendingInvitation> invitations = List.of();
        return new ProjectResponse.MemberDirectory(members, invitations);
    }

    @Transactional
    public ProjectResponse.Joined join(Long projectId) {
        Project project = projectAccessService.findProject(projectId);
        String principalKey = currentUserProvider.currentPrincipalKey();
        if (memberRepository.findByProjectIdAndPrincipalKey(projectId, principalKey).isPresent()) {
            throw new CustomException(ErrorCode.ALREADY_PROJECT_MEMBER);
        }

        WorkspaceMember workspaceMember = workspaceMemberRepository
                .findByWorkspaceIdAndPrincipalKey(project.getWorkspace().getId(), principalKey)
                .filter(member -> member.getStatus() == WorkspaceMemberStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSPACE_ACCESS_DENIED));
        ProjectCompany company = project.getParticipatingCompanies().stream()
                .filter(candidate -> candidate.getName().equalsIgnoreCase(workspaceMember.getCompanyName()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_ACCESS_DENIED));
        ProjectTeam team = project.getTeams().stream()
                .filter(candidate -> candidate.getCompanyId().equals(company.getCompanyId()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        ProjectMember member = ProjectMember.createAdmin(
                workspaceMember.getId(),
                principalKey,
                workspaceMember.getName(),
                company.getCompanyId(),
                company.getName(),
                team
        );
        project.addMember(member);
        ProjectMember saved = memberRepository.save(member);
        return new ProjectResponse.Joined(
                projectId,
                saved.getId(),
                ProjectMemberRole.PROJECT_ADMIN,
                AccessScope.FULL
        );
    }

    @Transactional
    public ProjectResponse.MembersManaged manageMembers(
            Long projectId,
            ProjectRequest.ManageMembers request
    ) {
        Project project = projectAccessService.findProject(projectId);
        projectAccessService.requireAdmin(projectId);
        if (request == null || request.actions() == null || request.actions().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_MEMBER_ACTION);
        }

        for (ProjectRequest.MemberAction action : request.actions()) {
            validateAction(action);
            updateExistingMember(project, action);
        }
        return new ProjectResponse.MembersManaged(request.actions().size(), List.of());
    }

    private void updateExistingMember(Project project, ProjectRequest.MemberAction action) {
        ProjectMember member = memberRepository.findByIdAndProjectId(action.memberId(), project.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_OR_TEAM_NOT_FOUND));
        protectLastAdmin(project.getId(), member, action);

        switch (action.type()) {
            case UPDATE -> {
                ProjectTeam team = action.teamId() == null
                        ? null
                        : teamRepository.findByIdAndProjectId(action.teamId(), project.getId())
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_OR_TEAM_NOT_FOUND));
                String companyName = team == null
                        ? member.getCompanyName()
                        : companyName(project, team.getCompanyId());
                member.update(team, companyName, action.role(), action.accessScope());
            }
            case SUSPEND -> member.suspend();
            case REMOVE -> project.removeMember(member);
            default -> throw new CustomException(ErrorCode.INVALID_MEMBER_ACTION);
        }
    }

    private void protectLastAdmin(
            Long projectId,
            ProjectMember member,
            ProjectRequest.MemberAction action
    ) {
        boolean removesAdmin = action.type() == ProjectMemberActionType.SUSPEND
                || action.type() == ProjectMemberActionType.REMOVE
                || (action.type() == ProjectMemberActionType.UPDATE
                && action.role() != null
                && action.role() != ProjectMemberRole.PROJECT_ADMIN);
        if (member.getRole() == ProjectMemberRole.PROJECT_ADMIN
                && member.getStatus() == ProjectMemberStatus.ACTIVE
                && removesAdmin
                && memberRepository.countByProjectIdAndRoleAndStatus(
                projectId,
                ProjectMemberRole.PROJECT_ADMIN,
                ProjectMemberStatus.ACTIVE
        ) <= 1) {
            throw new CustomException(ErrorCode.LAST_PROJECT_ADMIN_CANNOT_CHANGE);
        }
    }

    private void validateAction(ProjectRequest.MemberAction action) {
        if (action == null || action.type() == null) {
            throw new CustomException(ErrorCode.INVALID_MEMBER_ACTION);
        }
        if (action.memberId() == null
                || (action.type() == ProjectMemberActionType.UPDATE
                && action.teamId() == null
                && action.role() == null
                && action.accessScope() == null)) {
            throw new CustomException(ErrorCode.INVALID_MEMBER_ACTION);
        }
    }

    private boolean matchesStatus(ProjectMember member, ProjectMemberViewStatus status) {
        if (status == null) {
            return true;
        }
        return switch (status) {
            case ACTIVE -> member.getStatus() == ProjectMemberStatus.ACTIVE;
            case SUSPENDED -> member.getStatus() == ProjectMemberStatus.SUSPENDED;
            case INVITED -> false;
        };
    }

    private String companyName(Project project, Long companyId) {
        return project.getParticipatingCompanies().stream()
                .filter(company -> company.getCompanyId().equals(companyId))
                .map(ProjectCompany::getName)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_OR_TEAM_NOT_FOUND));
    }
}
