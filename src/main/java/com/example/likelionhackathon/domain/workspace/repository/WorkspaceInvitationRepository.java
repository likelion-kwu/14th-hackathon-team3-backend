package com.example.likelionhackathon.domain.workspace.repository;

import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.InvitationStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, Long> {

    Optional<WorkspaceInvitation> findByToken(String token);

    List<WorkspaceInvitation> findAllByWorkspaceIdAndStatusOrderByIdAsc(
            Long workspaceId,
            InvitationStatus status
    );
}
