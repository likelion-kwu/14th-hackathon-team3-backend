package com.example.likelionhackathon.domain.project.repository;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.domain.project.entity.ProjectOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectOAuthStateRepository extends JpaRepository<ProjectOAuthState, Long> {

    Optional<ProjectOAuthState> findByStateHashAndProjectIdAndProvider(
            String stateHash,
            Long projectId,
            IntegrationProvider provider
    );
}
