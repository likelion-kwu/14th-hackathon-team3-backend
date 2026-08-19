package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.Project;
import com.example.likelionhackathon.domain.project.entity.ProjectCompany;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ParticipatingCompanyRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberActionType;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectMember;
import com.example.likelionhackathon.domain.project.entity.ProjectTeam;
import com.example.likelionhackathon.domain.project.repository.ProjectMemberRepository;
import com.example.likelionhackathon.domain.project.repository.ProjectTeamRepository;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import com.example.likelionhackathon.global.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectMemberRepository memberRepository;
    @Mock
    private ProjectTeamRepository teamRepository;
    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private ProjectMemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new ProjectMemberService(
                projectAccessService,
                memberRepository,
                teamRepository,
                workspaceMemberRepository,
                currentUserProvider
        );
    }

    @Test
    void joinAddsCurrentWorkspaceMemberAsProjectAdmin() {
        Fixture fixture = fixture();
        when(projectAccessService.findProject(10L)).thenReturn(fixture.project());
        when(currentUserProvider.currentPrincipalKey()).thenReturn("member@example.com");
        WorkspaceMember workspaceMember = WorkspaceMember.createInvitedMember(
                fixture.project().getWorkspace(),
                "member@example.com",
                "Member",
                "member@example.com",
                "RelAI",
                "General",
                null,
                WorkspaceRole.MEMBER
        );
        ReflectionTestUtils.setField(workspaceMember, "id", 40L);
        when(workspaceMemberRepository.findByWorkspaceIdAndPrincipalKey(1L, "member@example.com"))
                .thenReturn(Optional.of(workspaceMember));
        when(memberRepository.save(org.mockito.ArgumentMatchers.any(ProjectMember.class)))
                .thenAnswer(invocation -> {
                    ProjectMember member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "id", 50L);
                    return member;
                });

        ProjectResponse.Joined response = memberService.join(10L);

        assertThat(response.projectId()).isEqualTo(10L);
        assertThat(response.memberId()).isEqualTo(50L);
        assertThat(response.role()).isEqualTo(ProjectMemberRole.PROJECT_ADMIN);
        verify(memberRepository).save(org.mockito.ArgumentMatchers.any(ProjectMember.class));
    }

    @Test
    void joinRejectsExistingProjectMember() {
        when(projectAccessService.findProject(10L)).thenReturn(fixture().project());
        when(currentUserProvider.currentPrincipalKey()).thenReturn("owner@example.com");
        when(memberRepository.findByProjectIdAndPrincipalKey(10L, "owner@example.com"))
                .thenReturn(Optional.of(fixture().admin()));

        assertThatThrownBy(() -> memberService.join(10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_PROJECT_MEMBER);
    }

    @Test
    void joinCreatesMissingDefaultTeamForParticipatingCompany() {
        Fixture fixture = fixture();
        fixture.project().addCompany(new ProjectCompany(2L, "Partner", ParticipatingCompanyRole.PARTNER));
        when(projectAccessService.findProject(10L)).thenReturn(fixture.project());
        when(currentUserProvider.currentPrincipalKey()).thenReturn("partner@example.com");
        WorkspaceMember workspaceMember = WorkspaceMember.createInvitedMember(
                fixture.project().getWorkspace(),
                "partner@example.com",
                "Partner Member",
                "partner@example.com",
                "Partner",
                "General",
                null,
                WorkspaceRole.MEMBER
        );
        ReflectionTestUtils.setField(workspaceMember, "id", 41L);
        when(workspaceMemberRepository.findByWorkspaceIdAndPrincipalKey(1L, "partner@example.com"))
                .thenReturn(Optional.of(workspaceMember));
        when(teamRepository.save(org.mockito.ArgumentMatchers.any(ProjectTeam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.save(org.mockito.ArgumentMatchers.any(ProjectMember.class)))
                .thenAnswer(invocation -> {
                    ProjectMember member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "id", 51L);
                    return member;
                });

        memberService.join(10L);

        assertThat(fixture.project().getTeams())
                .extracting(ProjectTeam::getCompanyId)
                .containsExactlyInAnyOrder(1L, 2L);
        verify(teamRepository).save(org.mockito.ArgumentMatchers.any(ProjectTeam.class));
    }

    @Test
    void cannotRemoveLastProjectAdmin() {
        Fixture fixture = fixture();
        when(projectAccessService.findProject(10L)).thenReturn(fixture.project());
        when(memberRepository.findByIdAndProjectId(30L, 10L)).thenReturn(Optional.of(fixture.admin()));
        when(memberRepository.countByProjectIdAndRoleAndStatus(
                10L,
                ProjectMemberRole.PROJECT_ADMIN,
                ProjectMemberStatus.ACTIVE
        )).thenReturn(1L);

        assertThatThrownBy(() -> memberService.manageMembers(
                10L,
                new ProjectRequest.ManageMembers(List.of(new ProjectRequest.MemberAction(
                        ProjectMemberActionType.REMOVE,
                        30L,
                        null,
                        null,
                        null
                )))
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LAST_PROJECT_ADMIN_CANNOT_CHANGE);
    }

    private Fixture fixture() {
        Workspace workspace = Workspace.create(
                "Workspace", "RELAI-KR-ABC123", "RelAI", "KR", List.of()
        );
        ReflectionTestUtils.setField(workspace, "id", 1L);
        Project project = Project.create(
                workspace,
                "Project",
                "Objective",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );
        ReflectionTestUtils.setField(project, "id", 10L);
        project.addCompany(new ProjectCompany(1L, "RelAI", ParticipatingCompanyRole.HOST));
        ProjectTeam team = ProjectTeam.createDefault(1L, "KR", "Asia/Seoul", "ko");
        ReflectionTestUtils.setField(team, "id", 20L);
        project.addTeam(team);
        ProjectMember admin = ProjectMember.createAdmin(
                2L, "owner@example.com", "Owner", 1L, "RelAI", team
        );
        ReflectionTestUtils.setField(admin, "id", 30L);
        project.addMember(admin);
        return new Fixture(project, team, admin);
    }

    private record Fixture(Project project, ProjectTeam team, ProjectMember admin) {
    }
}
