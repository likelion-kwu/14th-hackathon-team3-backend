package com.example.likelionhackathon.domain.project.repository;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectIntegrationRepository extends JpaRepository<ProjectIntegration, Long> {

    Optional<ProjectIntegration> findByIdAndProjectId(Long id, Long projectId);

    boolean existsByProjectIdAndProviderAndStatus(
            Long projectId,
            IntegrationProvider provider,
            IntegrationStatus status
    );
}
