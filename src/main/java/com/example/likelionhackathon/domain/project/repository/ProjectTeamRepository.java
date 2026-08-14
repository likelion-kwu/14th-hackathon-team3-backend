package com.example.likelionhackathon.domain.project.repository;

import com.example.likelionhackathon.domain.project.entity.ProjectTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, Long> {

    Optional<ProjectTeam> findByIdAndProjectId(Long id, Long projectId);
}
