package com.example.likelionhackathon.domain.workspace.dto;

import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceMemberStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceStatus;

import java.time.OffsetDateTime;
import java.util.List;

public final class WorkspaceResponse {

    private WorkspaceResponse() {
    }

    public record Created(
            Long workspaceId,
            String organizationCode,
            WorkspaceStatus status
    ) {
    }

    public record Summary(
            Long workspaceId,
            String name,
            String companyName,
            WorkspaceRole role,
            long memberCount,
            WorkspaceStatus status
    ) {
    }

    public record Detail(
            Long workspaceId,
            String name,
            String organizationCode,
            Company company,
            List<String> collaboratingCompanies,
            WorkspaceRole myRole,
            long memberCount,
            long projectCount,
            WorkspaceStatus status
    ) {
    }

    public record Company(String name, String countryCode) {
    }

    public record Updated(
            Long workspaceId,
            WorkspaceStatus status,
            Long version
    ) {
    }

    public record Members(
            List<Member> members,
            List<PendingInvitation> pendingInvitations
    ) {
    }

    public record Member(
            Long memberId,
            String name,
            String companyName,
            String teamName,
            WorkspaceRole role,
            WorkspaceMemberStatus status
    ) {
    }

    public record PendingInvitation(
            Long invitationId,
            String email,
            WorkspaceRole role
    ) {
    }

    public record MembersManaged(
            int processedCount,
            List<FailedAction> failedActions
    ) {
    }

    public record FailedAction(
            Long memberId,
            String code,
            String message
    ) {
    }

    public record InvitationCreated(
            Long invitationId,
            String inviteUrl,
            int sentCount,
            OffsetDateTime expiresAt
    ) {
    }

    public record Joined(
            Long workspaceId,
            Long memberId,
            WorkspaceRole role
    ) {
    }

    public record Profile(
            Long userId,
            Long workspaceId,
            String name,
            String companyName,
            String teamName,
            String jobTitle
    ) {
    }
}
