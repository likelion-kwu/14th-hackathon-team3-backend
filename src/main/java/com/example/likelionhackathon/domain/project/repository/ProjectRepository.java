package com.example.likelionhackathon.domain.project.repository;

import com.example.likelionhackathon.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByWorkspaceIdAndNameIgnoreCase(Long workspaceId, String name);

    boolean existsByWorkspaceIdAndNameIgnoreCaseAndIdNot(Long workspaceId, String name, Long id);

    List<Project> findAllByWorkspaceIdOrderByIdAsc(Long workspaceId);

    long countByWorkspaceId(Long workspaceId);
}
