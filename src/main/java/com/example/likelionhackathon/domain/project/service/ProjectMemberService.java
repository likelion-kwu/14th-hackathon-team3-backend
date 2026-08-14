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
import com.example.likelionhackathon.domain.project.entity.ProjectInvitation;
import com.example.likelionhackathon.domain.project.entity.ProjectMember;
import com.example.likelionhackathon.domain.project.entity.ProjectTeam;
import com.example.likelionhackathon.domain.project.repository.ProjectInvitationRepository;
import com.example.likelionhackathon.domain.project.repository.ProjectMemberRepository;
import com.example.likelionhackathon.domain.project.repository.ProjectTeamRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final ProjectAccessService projectAccessService;
    private final ProjectMemberRepository memberRepository;
    private final ProjectInvitationRepository invitationRepository;
    private final ProjectTeamRepository teamRepository;

    @Transactional(readOnly = true)
    public ProjectResponse.MemberDirectory getMembers(
            Long projectId,
            Long companyId,
            ProjectMemberViewStatus status
    ) {
        projectAccessService.findProject(projectId);
        projectAccessService.requireAccess(projectId);

        boolean includeMembers = status == null || status != ProjectMemberViewStatus.INVITED;
        boolean includeInvitations = status == null || status == ProjectMemberViewStatus.INVITED;
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
        List<ProjectResponse.PendingInvitation> invitations = includeInvitations
                ? invitationRepository.findAllByProjectIdAndPendingTrueOrderByIdAsc(projectId).stream()
                .filter(invitation -> companyId == null
                        || companyId.equals(invitation.getTeam().getCompanyId()))
                .map(invitation -> new ProjectResponse.PendingInvitation(
                        invitation.getId(), invitation.getEmail()
                ))
                .toList()
                : List.of();
        return new ProjectResponse.MemberDirectory(members, invitations);
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
            if (action.type() == ProjectMemberActionType.INVITE) {
                invite(project, action);
            } else {
                updateExistingMember(project, action);
            }
        }
        return new ProjectResponse.MembersManaged(request.actions().size(), List.of());
    }

    private void invite(Project project, ProjectRequest.MemberAction action) {
        String email = action.email().trim().toLowerCase(Locale.ROOT);
        if (invitationRepository.existsByProjectIdAndEmailIgnoreCaseAndPendingTrue(
                project.getId(), email
        )) {
            throw new CustomException(ErrorCode.INVALID_MEMBER_ACTION);
        }
        ProjectTeam team = teamRepository.findByIdAndProjectId(action.teamId(), project.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_OR_TEAM_NOT_FOUND));
        ProjectInvitation invitation = ProjectInvitation.create(
                email,
                team,
                action.role() == null ? ProjectMemberRole.MEMBER : action.role(),
                action.accessScope() == null ? AccessScope.TEAM_ONLY : action.accessScope()
        );
        project.addInvitation(invitation);
        invitationRepository.save(invitation);
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
        if (action.type() == ProjectMemberActionType.INVITE) {
            if (action.teamId() == null
                    || action.email() == null
                    || !EMAIL_PATTERN.matcher(action.email().trim()).matches()) {
                throw new CustomException(ErrorCode.INVALID_MEMBER_ACTION);
            }
            return;
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
