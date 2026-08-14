package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.repository.ProjectRepository;
import com.example.likelionhackathon.domain.workspace.service.WorkspaceProjectCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectCountAdapter implements WorkspaceProjectCounter {

    private final ProjectRepository projectRepository;

    @Override
    public long countByWorkspaceId(Long workspaceId) {
        return projectRepository.countByWorkspaceId(workspaceId);
    }
}
