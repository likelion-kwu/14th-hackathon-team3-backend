package com.example.likelionhackathon.domain.project.repository;

import com.example.likelionhackathon.domain.project.entity.ProjectInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {

    List<ProjectInvitation> findAllByProjectIdAndPendingTrueOrderByIdAsc(Long projectId);

    boolean existsByProjectIdAndEmailIgnoreCaseAndPendingTrue(Long projectId, String email);
}
