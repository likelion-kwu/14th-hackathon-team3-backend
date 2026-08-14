package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.Project;
import com.example.likelionhackathon.domain.project.entity.ProjectCompany;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.AccessScope;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ParticipatingCompanyRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberActionType;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectInvitation;
import com.example.likelionhackathon.domain.project.entity.ProjectMember;
import com.example.likelionhackathon.domain.project.entity.ProjectTeam;
import com.example.likelionhackathon.domain.project.repository.ProjectInvitationRepository;
import com.example.likelionhackathon.domain.project.repository.ProjectMemberRepository;
import com.example.likelionhackathon.domain.project.repository.ProjectTeamRepository;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectMemberRepository memberRepository;
    @Mock
    private ProjectInvitationRepository invitationRepository;
    @Mock
    private ProjectTeamRepository teamRepository;

    private ProjectMemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new ProjectMemberService(
                projectAccessService,
                memberRepository,
                invitationRepository,
                teamRepository
        );
    }

    @Test
    void inviteAddsPendingInvitation() {
        Fixture fixture = fixture();
        when(projectAccessService.findProject(10L)).thenReturn(fixture.project());
        when(teamRepository.findByIdAndProjectId(20L, 10L)).thenReturn(Optional.of(fixture.team()));

        ProjectResponse.MembersManaged response = memberService.manageMembers(
                10L,
                new ProjectRequest.ManageMembers(List.of(new ProjectRequest.MemberAction(
                        ProjectMemberActionType.INVITE,
                        null,
                        "Emily@Example.com",
                        20L,
                        null,
                        null
                )))
        );

        assertThat(response.processedCount()).isEqualTo(1);
        ArgumentCaptor<ProjectInvitation> captor = ArgumentCaptor.forClass(ProjectInvitation.class);
        verify(invitationRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("emily@example.com");
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectMemberRole.MEMBER);
        assertThat(captor.getValue().getAccessScope()).isEqualTo(AccessScope.TEAM_ONLY);
    }

    @Test
    void manageRejectsInvalidInviteEmail() {
        when(projectAccessService.findProject(10L)).thenReturn(fixture().project());

        assertThatThrownBy(() -> memberService.manageMembers(
                10L,
                new ProjectRequest.ManageMembers(List.of(new ProjectRequest.MemberAction(
                        ProjectMemberActionType.INVITE,
                        null,
                        "invalid-email",
                        20L,
                        null,
                        null
                )))
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_MEMBER_ACTION);
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
