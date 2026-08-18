package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.Project;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationActionType;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.WorkDay;
import com.example.likelionhackathon.domain.project.entity.ProjectIntegration;
import com.example.likelionhackathon.domain.project.entity.ProjectTeam;
import com.example.likelionhackathon.domain.project.repository.ProjectIntegrationRepository;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSettingsServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectTeamRepository teamRepository;
    @Mock
    private ProjectIntegrationRepository integrationRepository;

    private ProjectTeamService teamService;
    private ProjectIntegrationService integrationService;

    @BeforeEach
    void setUp() {
        teamService = new ProjectTeamService(projectAccessService, teamRepository);
        integrationService = new ProjectIntegrationService(projectAccessService, integrationRepository);
    }

    @Test
    void saveTeamSettingsUpdatesSchedule() {
        ProjectTeam team = ProjectTeam.createDefault(1L, "KR", "Asia/Seoul", "ko");
        ReflectionTestUtils.setField(team, "id", 20L);
        when(teamRepository.findByIdAndProjectId(20L, 10L)).thenReturn(Optional.of(team));

        ProjectResponse.TeamSettingsSaved response = teamService.saveSettings(
                10L,
                new ProjectRequest.SaveTeamSettings(List.of(new ProjectRequest.TeamSetting(
                        20L,
                        "GB",
                        "Europe/London",
                        "en",
                        LocalTime.of(8, 30),
                        LocalTime.of(17, 30),
                        List.of(WorkDay.MON, WorkDay.TUE, WorkDay.WED, WorkDay.THU),
                        false
                )))
        );

        assertThat(response.updatedTeamCount()).isEqualTo(1);
        assertThat(team.getTimezone()).isEqualTo("Europe/London");
        assertThat(team.getWorkDays()).containsExactlyInAnyOrder(
                WorkDay.MON, WorkDay.TUE, WorkDay.WED, WorkDay.THU
        );
    }

    @Test
    void saveTeamSettingsRejectsUnknownTimezone() {
        assertThatThrownBy(() -> teamService.saveSettings(
                10L,
                new ProjectRequest.SaveTeamSettings(List.of(new ProjectRequest.TeamSetting(
                        20L,
                        "KR",
                        "Mars/Olympus",
                        "ko",
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0),
                        List.of(WorkDay.MON),
                        true
                )))
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TIMEZONE);
    }

    @Test
    void updateIntegrationChangesResourcesAndInterval() {
        Project project = project();
        when(projectAccessService.findProject(10L)).thenReturn(project);
        ProjectIntegration integration = ProjectIntegration.connect(
                IntegrationProvider.SLACK,
                List.of("general"),
                30,
                "encrypted-access-token",
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(integration, "id", 99L);
        project.addIntegration(integration);
        when(integrationRepository.findByIdAndProjectId(99L, 10L))
                .thenReturn(Optional.of(integration));

        ProjectResponse.IntegrationsManaged response = integrationService.manage(
                10L,
                new ProjectRequest.ManageIntegrations(List.of(new ProjectRequest.IntegrationAction(
                        IntegrationActionType.UPDATE,
                        99L,
                        List.of("handover"),
                        60
                )))
        );

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(integration.getResourceIds()).containsExactly("handover");
        assertThat(integration.getSyncIntervalMinutes()).isEqualTo(60);
    }

    @Test
    void updateIntegrationRequiresExistingIntegration() {
        when(projectAccessService.findProject(10L)).thenReturn(project());

        assertThatThrownBy(() -> integrationService.manage(
                10L,
                new ProjectRequest.ManageIntegrations(List.of(new ProjectRequest.IntegrationAction(
                        IntegrationActionType.SYNC,
                        99L,
                        null,
                        null
                )))
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTEGRATION_NOT_FOUND);
    }

    private Project project() {
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
        return project;
    }
}
