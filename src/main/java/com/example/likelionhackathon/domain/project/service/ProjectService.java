package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.Project;
import com.example.likelionhackathon.domain.project.entity.ProjectCompany;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ParticipatingCompanyRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectMember;
import com.example.likelionhackathon.domain.project.entity.ProjectTeam;
import com.example.likelionhackathon.domain.project.repository.ProjectMemberRepository;
import com.example.likelionhackathon.domain.project.repository.ProjectRepository;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceMemberStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import com.example.likelionhackathon.global.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ProjectAccessService projectAccessService;
    private final ProjectCycleCreator projectCycleCreator;

    @Transactional
    public ProjectResponse.Created create(Long workspaceId, ProjectRequest.Create request) {
        validateBaseInput(request == null ? null : request.name(), request == null ? null : request.objective(),
                request == null ? null : request.startDate(), request == null ? null : request.endDate(),
                request == null ? null : request.participatingCompanies());
        Workspace workspace = findWorkspace(workspaceId);
        WorkspaceMember creator = requireWorkspaceCreatePermission(workspaceId);
        String name = request.name().trim();
        if (projectRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            throw new CustomException(ErrorCode.PROJECT_NAME_DUPLICATED);
        }

        List<ProjectCompany> companies = resolveCompanies(workspace, request.participatingCompanies());
        ProjectRequest.ParticipatingCompany host = request.participatingCompanies().stream()
                .filter(company -> company.role() == ParticipatingCompanyRole.HOST)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PROJECT_INPUT));

        Project project = Project.create(
                workspace,
                name,
                request.objective().trim(),
                request.startDate(),
                request.endDate()
        );
        companies.forEach(project::addCompany);

        CountryDefaults defaults = countryDefaults(workspace.getCompanyCountryCode());
        ProjectTeam defaultTeam = ProjectTeam.createDefault(
                host.companyId(),
                defaults.countryCode(),
                defaults.timezone(),
                defaults.languageCode()
        );
        project.addTeam(defaultTeam);
        project.addMember(ProjectMember.createAdmin(
                creator.getId(),
                creator.getPrincipalKey(),
                creator.getName(),
                host.companyId(),
                companyName(companies, host.companyId()),
                defaultTeam
        ));

        Project saved;
        try {
            saved = projectRepository.saveAndFlush(project);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.PROJECT_NAME_DUPLICATED);
        }

        // 사이클이 없으면 이슈를 만들 수 없어서 프로젝트 기간을 잘라 사이클을 함께 만든다.
        // 같은 트랜잭션이라 사이클 저장이 실패하면 프로젝트도 남지 않는다.
        projectCycleCreator.createInitialCycles(
                saved.getId(), saved.getStartDate(), saved.getEndDate(), saved.getObjective());

        return new ProjectResponse.Created(saved.getId(), saved.getStatus());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse.Summary> getProjects(
            Long workspaceId,
            ProjectStatus status,
            String keyword
    ) {
        findWorkspace(workspaceId);
        requireWorkspaceAccess(workspaceId, ErrorCode.WORKSPACE_ACCESS_DENIED);
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            normalizedKeyword = normalizedKeyword.toLowerCase(Locale.ROOT);
        }
        final String searchKeyword = normalizedKeyword;

        return projectRepository.findAllByWorkspaceIdOrderByIdAsc(workspaceId).stream()
                .filter(project -> status == null || project.getStatus() == status)
                .filter(project -> searchKeyword == null
                        || project.getName().toLowerCase(Locale.ROOT).contains(searchKeyword))
                .map(project -> new ProjectResponse.Summary(
                        project.getId(),
                        project.getName(),
                        project.getStartDate(),
                        project.getEndDate(),
                        project.getStatus(),
                        projectMemberRepository.countByProjectIdAndStatus(
                                project.getId(),
                                ProjectMemberStatus.ACTIVE
                        )
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse.Detail getDetail(Long projectId) {
        Project project = projectAccessService.findProject(projectId);
        projectAccessService.requireAccess(projectId);
        return new ProjectResponse.Detail(
                project.getId(),
                project.getName(),
                project.getObjective(),
                project.getStartDate(),
                project.getEndDate(),
                project.getStatus(),
                project.getParticipatingCompanies().stream()
                        .map(company -> new ProjectResponse.ParticipatingCompany(
                                company.getCompanyId(), company.getName(), company.getRole()
                        ))
                        .toList(),
                project.getTeams().stream()
                        .map(team -> new ProjectResponse.TeamSchedule(
                                team.getId(),
                                team.getTeamName(),
                                team.getTimezone(),
                                team.getLanguageCode(),
                                team.getWorkStartTime(),
                                team.getWorkEndTime(),
                                team.getWorkDays().stream()
                                        .sorted(Comparator.comparingInt(Enum::ordinal))
                                        .toList()
                        ))
                        .toList(),
                project.getMembers().stream()
                        .map(member -> new ProjectResponse.DetailMember(
                                member.getId(),
                                member.getName(),
                                member.getTeam().getId(),
                                member.getRole(),
                                member.getAccessScope()
                        ))
                        .toList(),
                project.getInvitations().stream()
                        .filter(invitation -> invitation.isPending())
                        .map(invitation -> new ProjectResponse.PendingInvitation(
                                invitation.getId(), invitation.getEmail()
                        ))
                        .toList(),
                project.getIntegrations().stream()
                        .map(integration -> new ProjectResponse.Integration(
                                integration.getId(),
                                integration.getProvider(),
                                integration.getStatus(),
                                integration.getLastSyncedAt()
                        ))
                        .toList(),
                normalizeVersion(project.getVersion())
        );
    }

    @Transactional
    public ProjectResponse.Updated update(Long projectId, ProjectRequest.Update request) {
        validateBaseInput(request == null ? null : request.name(), request == null ? null : request.objective(),
                request == null ? null : request.startDate(), request == null ? null : request.endDate(),
                request == null ? null : request.participatingCompanies());
        if (request.status() == null || request.version() == null) {
            throw new CustomException(ErrorCode.INVALID_PROJECT_INPUT);
        }
        Project project = projectAccessService.findProject(projectId);
        projectAccessService.requireAdmin(projectId);
        if (!Objects.equals(normalizeVersion(project.getVersion()), request.version())) {
            throw new CustomException(ErrorCode.PROJECT_VERSION_CONFLICT);
        }

        String name = request.name().trim();
        if (projectRepository.existsByWorkspaceIdAndNameIgnoreCaseAndIdNot(
                project.getWorkspace().getId(), name, projectId
        )) {
            throw new CustomException(ErrorCode.PROJECT_NAME_DUPLICATED);
        }

        project.update(name, request.objective().trim(), request.startDate(), request.endDate(), request.status());
        project.replaceCompanies(resolveCompanies(project.getWorkspace(), request.participatingCompanies()));
        try {
            Project saved = projectRepository.saveAndFlush(project);
            return new ProjectResponse.Updated(
                    saved.getId(),
                    saved.getStatus(),
                    normalizeVersion(saved.getVersion())
            );
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new CustomException(ErrorCode.PROJECT_VERSION_CONFLICT);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.PROJECT_NAME_DUPLICATED);
        }
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private WorkspaceMember requireWorkspaceCreatePermission(Long workspaceId) {
        WorkspaceMember member = requireWorkspaceAccess(workspaceId, ErrorCode.PROJECT_CREATE_DENIED);
        if (!member.getRole().canManage()) {
            throw new CustomException(ErrorCode.PROJECT_CREATE_DENIED);
        }
        return member;
    }

    private WorkspaceMember requireWorkspaceAccess(Long workspaceId, ErrorCode deniedError) {
        return workspaceMemberRepository.findByWorkspaceIdAndPrincipalKey(
                        workspaceId,
                        currentUserProvider.currentPrincipalKey()
                )
                .filter(member -> member.getStatus() == WorkspaceMemberStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(deniedError));
    }

    private void validateBaseInput(
            String name,
            String objective,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            List<ProjectRequest.ParticipatingCompany> companies
    ) {
        if (isBlankOrTooLong(name, 100)
                || isBlankOrTooLong(objective, 2000)
                || startDate == null
                || endDate == null
                || startDate.isAfter(endDate)
                || companies == null
                || companies.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PROJECT_INPUT);
        }
        Set<Long> companyIds = new HashSet<>();
        long hostCount = 0;
        for (ProjectRequest.ParticipatingCompany company : companies) {
            if (company == null
                    || company.companyId() == null
                    || company.companyId() <= 0
                    || company.role() == null
                    || !companyIds.add(company.companyId())) {
                throw new CustomException(ErrorCode.INVALID_PROJECT_INPUT);
            }
            if (company.role() == ParticipatingCompanyRole.HOST) {
                hostCount++;
            }
        }
        if (hostCount != 1) {
            throw new CustomException(ErrorCode.INVALID_PROJECT_INPUT);
        }
    }

    private List<ProjectCompany> resolveCompanies(
            Workspace workspace,
            List<ProjectRequest.ParticipatingCompany> requestedCompanies
    ) {
        List<String> partnerNames = new ArrayList<>(workspace.getCollaboratingCompanyNames());
        int partnerIndex = 0;
        List<ProjectCompany> companies = new ArrayList<>();
        for (ProjectRequest.ParticipatingCompany requested : requestedCompanies) {
            String name;
            if (requested.role() == ParticipatingCompanyRole.HOST) {
                name = workspace.getCompanyName();
            } else if (partnerIndex < partnerNames.size()) {
                name = partnerNames.get(partnerIndex++);
            } else {
                name = "기업 " + requested.companyId();
            }
            companies.add(new ProjectCompany(requested.companyId(), name, requested.role()));
        }
        return companies;
    }

    private String companyName(List<ProjectCompany> companies, Long companyId) {
        return companies.stream()
                .filter(company -> company.getCompanyId().equals(companyId))
                .map(ProjectCompany::getName)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PROJECT_INPUT));
    }

    private CountryDefaults countryDefaults(String countryCode) {
        String normalized = countryCode.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "KR" -> new CountryDefaults("KR", "Asia/Seoul", "ko");
            case "GB", "UK" -> new CountryDefaults("GB", "Europe/London", "en");
            default -> new CountryDefaults(normalized, "UTC", "en");
        };
    }

    private boolean isBlankOrTooLong(String value, int maxLength) {
        return value == null || value.isBlank() || value.trim().length() > maxLength;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long normalizeVersion(Long version) {
        return version == null ? 0L : version;
    }

    private record CountryDefaults(String countryCode, String timezone, String languageCode) {
    }
}
