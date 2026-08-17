package com.example.likelionhackathon.domain.workspace.repository;

import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceMemberStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    List<WorkspaceMember> findAllByPrincipalKeyAndStatusOrderByIdAsc(
            String principalKey,
            WorkspaceMemberStatus status
    );

    List<WorkspaceMember> findAllByWorkspaceIdOrderByIdAsc(Long workspaceId);

    List<WorkspaceMember> findAllByWorkspaceIdAndStatusOrderByIdAsc(
            Long workspaceId,
            WorkspaceMemberStatus status
    );

    Optional<WorkspaceMember> findByWorkspaceIdAndPrincipalKey(Long workspaceId, String principalKey);

    Optional<WorkspaceMember> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndPrincipalKey(Long workspaceId, String principalKey);

    boolean existsByWorkspaceIdAndEmailIgnoreCaseAndStatus(
            Long workspaceId,
            String email,
            WorkspaceMemberStatus status
    );

    long countByWorkspaceIdAndStatus(Long workspaceId, WorkspaceMemberStatus status);

    long countByWorkspaceIdAndRoleAndStatus(
            Long workspaceId,
            WorkspaceRole role,
            WorkspaceMemberStatus status
    );
}
