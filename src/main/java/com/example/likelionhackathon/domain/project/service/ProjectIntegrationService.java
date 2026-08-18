package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationActionType;
import com.example.likelionhackathon.domain.project.entity.ProjectIntegration;
import com.example.likelionhackathon.domain.project.repository.ProjectIntegrationRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectIntegrationService {

    private static final int MAX_SYNC_INTERVAL_MINUTES = 1_440;

    private final ProjectAccessService projectAccessService;
    private final ProjectIntegrationRepository integrationRepository;

    @Transactional
    public ProjectResponse.IntegrationsManaged manage(
            Long projectId,
            ProjectRequest.ManageIntegrations request
    ) {
        projectAccessService.findProject(projectId);
        projectAccessService.requireAdmin(projectId);
        if (request == null || request.actions() == null || request.actions().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INTEGRATION_ACTION);
        }

        for (ProjectRequest.IntegrationAction action : request.actions()) {
            validateAction(action);
            manageExisting(projectId, action);
        }
        return new ProjectResponse.IntegrationsManaged(request.actions().size(), List.of());
    }

    private void manageExisting(Long projectId, ProjectRequest.IntegrationAction action) {
        ProjectIntegration integration = integrationRepository
                .findByIdAndProjectId(action.integrationId(), projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.INTEGRATION_NOT_FOUND));
        switch (action.type()) {
            case UPDATE -> integration.update(
                    action.resourceIds() == null ? null : normalizeResources(action.resourceIds()),
                    action.syncIntervalMinutes()
            );
            case SYNC -> integration.sync(OffsetDateTime.now());
            case DISCONNECT -> integration.disconnect();
            default -> throw new CustomException(ErrorCode.INVALID_INTEGRATION_ACTION);
        }
    }

    private void validateAction(ProjectRequest.IntegrationAction action) {
        if (action == null || action.type() == null || invalidInterval(action.syncIntervalMinutes())) {
            throw new CustomException(ErrorCode.INVALID_INTEGRATION_ACTION);
        }
        if (action.resourceIds() != null
                && action.resourceIds().stream().anyMatch(resource -> resource == null || resource.isBlank())) {
            throw new CustomException(ErrorCode.INVALID_INTEGRATION_ACTION);
        }
        if (action.integrationId() == null
                || (action.type() == IntegrationActionType.UPDATE
                && action.resourceIds() == null
                && action.syncIntervalMinutes() == null)) {
            throw new CustomException(ErrorCode.INVALID_INTEGRATION_ACTION);
        }
    }

    private boolean invalidInterval(Integer interval) {
        return interval != null && (interval < 1 || interval > MAX_SYNC_INTERVAL_MINUTES);
    }

    private List<String> normalizeResources(List<String> resources) {
        if (resources == null) {
            return List.of();
        }
        return new LinkedHashSet<>(resources.stream().map(String::trim).toList()).stream().toList();
    }
}
