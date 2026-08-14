package com.example.likelionhackathon.domain.project.repository;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndPrincipalKey(Long projectId, String principalKey);

    Optional<ProjectMember> findByIdAndProjectId(Long id, Long projectId);

    List<ProjectMember> findAllByProjectIdOrderByIdAsc(Long projectId);

    long countByProjectIdAndStatus(Long projectId, ProjectMemberStatus status);

    long countByProjectIdAndRoleAndStatus(
            Long projectId,
            ProjectMemberRole role,
            ProjectMemberStatus status
    );
}
