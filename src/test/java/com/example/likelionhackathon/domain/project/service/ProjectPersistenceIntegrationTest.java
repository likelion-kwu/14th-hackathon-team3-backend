package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ParticipatingCompanyRole;
import com.example.likelionhackathon.domain.workspace.dto.WorkspaceResponse;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceRepository;
import com.example.likelionhackathon.domain.workspace.service.WorkspaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Transactional
class ProjectPersistenceIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private CycleRepository cycleRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPersistsAggregateAndUpdatesWorkspaceProjectCount() {
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.create(
                "Project Integration Workspace",
                "RELAI-KR-PROJECT",
                "RelAI",
                "KR",
                List.of("Partner")
        ));
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.createOwner(
                workspace,
                "project-owner",
                "Project Owner",
                "RelAI"
        ));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("project-owner", "N/A", List.of())
        );

        // 오늘이 들어간 사이클은 진행 중으로 시작하므로, 상태를 단정하려면 기간을 내일부터 잡는다.
        LocalDate projectStart = LocalDate.now().plusDays(1);
        LocalDate projectEnd = projectStart.plusDays(39);

        ProjectResponse.Created created = projectService.create(
                workspace.getId(),
                new ProjectRequest.Create(
                        "Global Payment Integration",
                        "Connect payment work across companies",
                        projectStart,
                        projectEnd,
                        List.of(
                                new ProjectRequest.ParticipatingCompany(1L, ParticipatingCompanyRole.HOST),
                                new ProjectRequest.ParticipatingCompany(2L, ParticipatingCompanyRole.PARTNER)
                        )
                )
        );

        ProjectResponse.Detail detail = projectService.getDetail(created.projectId());
        WorkspaceResponse.Detail workspaceDetail = workspaceService.getDetail(workspace.getId());

        assertThat(detail.participatingCompanies()).hasSize(2);
        assertThat(detail.teamSchedules()).singleElement()
                .extracting("teamName")
                .isEqualTo("General");
        assertThat(detail.members()).singleElement()
                .extracting("name")
                .isEqualTo("Project Owner");
        assertThat(workspaceDetail.projectCount()).isEqualTo(1);

        List<Cycle> cycles = cycleRepository.findByProjectIdOrderByStartDateAsc(created.projectId());
        assertThat(cycles).extracting(Cycle::getName, Cycle::getStartDate, Cycle::getEndDate)
                .containsExactly(
                        tuple("Cycle 1", projectStart, projectStart.plusDays(13)),
                        tuple("Cycle 2", projectStart.plusDays(14), projectStart.plusDays(27)),
                        tuple("Cycle 3", projectStart.plusDays(28), projectEnd));
        assertThat(cycles).extracting(Cycle::getStatus).containsOnly(CycleStatus.PLANNED);
    }
}
