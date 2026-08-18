package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.Project;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ParticipatingCompanyRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectStatus;
import com.example.likelionhackathon.domain.project.repository.ProjectMemberRepository;
import com.example.likelionhackathon.domain.project.repository.ProjectRepository;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import com.example.likelionhackathon.global.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectCycleCreator projectCycleCreator;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(
                projectRepository,
                projectMemberRepository,
                workspaceRepository,
                workspaceMemberRepository,
                currentUserProvider,
                projectAccessService,
                projectCycleCreator
        );
    }

    @Test
    void createBuildsDefaultTeamAndProjectAdmin() {
        Workspace workspace = workspace();
        WorkspaceMember owner = member(workspace, WorkspaceRole.OWNER);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("owner@example.com");
        when(workspaceMemberRepository.findByWorkspaceIdAndPrincipalKey(1L, "owner@example.com"))
                .thenReturn(Optional.of(owner));
        when(projectRepository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            ReflectionTestUtils.setField(project, "id", 10L);
            ReflectionTestUtils.setField(project, "version", 0L);
            return project;
        });

        ProjectResponse.Created response = projectService.create(1L, createRequest());

        assertThat(response.projectId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(ProjectStatus.DRAFT);
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTeams()).singleElement()
                .extracting("teamName", "timezone")
                .containsExactly("General", "Asia/Seoul");
        assertThat(captor.getValue().getMembers()).singleElement()
                .extracting("name", "role")
                .containsExactly("Owner", com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberRole.PROJECT_ADMIN);
    }

    @Test
    void createAlsoCreatesInitialCyclesCoveringProjectPeriod() {
        Workspace workspace = workspace();
        WorkspaceMember owner = member(workspace, WorkspaceRole.OWNER);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("owner@example.com");
        when(workspaceMemberRepository.findByWorkspaceIdAndPrincipalKey(1L, "owner@example.com"))
                .thenReturn(Optional.of(owner));
        when(projectRepository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            ReflectionTestUtils.setField(project, "id", 10L);
            ReflectionTestUtils.setField(project, "version", 0L);
            return project;
        });

        projectService.create(1L, createRequest());

        verify(projectCycleCreator).createInitialCycles(
                10L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "Integrate global payment systems");
    }

    @Test
    void createDoesNotCreateCyclesWhenProjectSaveFails() {
        Workspace workspace = workspace();
        WorkspaceMember owner = member(workspace, WorkspaceRole.OWNER);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("owner@example.com");
        when(workspaceMemberRepository.findByWorkspaceIdAndPrincipalKey(1L, "owner@example.com"))
                .thenReturn(Optional.of(owner));
        when(projectRepository.saveAndFlush(any(Project.class)))
                .thenThrow(new DataIntegrityViolationException("duplicated"));

        assertThatThrownBy(() -> projectService.create(1L, createRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROJECT_NAME_DUPLICATED);
        verifyNoInteractions(projectCycleCreator);
    }

    @Test
    void createRequiresWorkspaceAdminRole() {
        Workspace workspace = workspace();
        WorkspaceMember member = member(workspace, WorkspaceRole.MEMBER);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("owner@example.com");
        when(workspaceMemberRepository.findByWorkspaceIdAndPrincipalKey(1L, "owner@example.com"))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> projectService.create(1L, createRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROJECT_CREATE_DENIED);
    }

    @Test
    void createRejectsMultipleHostCompanies() {
        ProjectRequest.Create request = new ProjectRequest.Create(
                "Project",
                "Objective",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(
                        new ProjectRequest.ParticipatingCompany(1L, ParticipatingCompanyRole.HOST),
                        new ProjectRequest.ParticipatingCompany(2L, ParticipatingCompanyRole.HOST)
                )
        );

        assertThatThrownBy(() -> projectService.create(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROJECT_INPUT);
    }

    @Test
    void updateRejectsStaleVersion() {
        Project project = Project.create(
                workspace(),
                "Project",
                "Objective",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );
        ReflectionTestUtils.setField(project, "id", 10L);
        ReflectionTestUtils.setField(project, "version", 3L);
        when(projectAccessService.findProject(10L)).thenReturn(project);

        ProjectRequest.Update request = new ProjectRequest.Update(
                "Project",
                "Updated objective",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(new ProjectRequest.ParticipatingCompany(1L, ParticipatingCompanyRole.HOST)),
                ProjectStatus.ACTIVE,
                2L
        );

        assertThatThrownBy(() -> projectService.update(10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROJECT_VERSION_CONFLICT);
    }

    private ProjectRequest.Create createRequest() {
        return new ProjectRequest.Create(
                "Global Payment",
                "Integrate global payment systems",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(
                        new ProjectRequest.ParticipatingCompany(1L, ParticipatingCompanyRole.HOST),
                        new ProjectRequest.ParticipatingCompany(2L, ParticipatingCompanyRole.PARTNER)
                )
        );
    }

    private Workspace workspace() {
        Workspace workspace = Workspace.create(
                "Workspace",
                "RELAI-KR-ABC123",
                "RelAI",
                "KR",
                List.of("Partner")
        );
        ReflectionTestUtils.setField(workspace, "id", 1L);
        return workspace;
    }

    private WorkspaceMember member(Workspace workspace, WorkspaceRole role) {
        WorkspaceMember member = WorkspaceMember.createInvitedMember(
                workspace,
                "owner@example.com",
                "Owner",
                "owner@example.com",
                "RelAI",
                "Engineering",
                "Lead",
                role
        );
        ReflectionTestUtils.setField(member, "id", 2L);
        return member;
    }
}
