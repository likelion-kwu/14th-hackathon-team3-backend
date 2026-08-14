package com.example.likelionhackathon.domain.workspace.dto;

import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.AssignableWorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.InvitationType;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.MemberActionType;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class WorkspaceRequest {

    private WorkspaceRequest() {
    }

    public record Create(
            String name,
            String companyName,
            String companyCountryCode,
            List<String> collaboratingCompanyNames,
            List<String> inviteeEmails
    ) {
        public List<String> safeCollaboratingCompanyNames() {
            return collaboratingCompanyNames == null ? List.of() : collaboratingCompanyNames;
        }

        public List<String> safeInviteeEmails() {
            return inviteeEmails == null ? List.of() : inviteeEmails;
        }
    }

    public record Update(
            String name,
            String companyName,
            String companyCountryCode,
            List<String> collaboratingCompanyNames,
            WorkspaceStatus status,
            Long version
    ) {
        public List<String> safeCollaboratingCompanyNames() {
            return collaboratingCompanyNames == null ? List.of() : collaboratingCompanyNames;
        }
    }

    public record ManageMembers(
            @NotEmpty List<@Valid MemberAction> actions
    ) {
    }

    public record MemberAction(
            @NotNull MemberActionType action,
            @NotNull Long memberId,
            AssignableWorkspaceRole role,
            @Size(max = 100) String teamName,
            @Size(max = 100) String jobTitle
    ) {
    }

    public record CreateInvitation(
            InvitationType type,
            List<String> emails,
            AssignableWorkspaceRole role,
            Integer expiresInHours
    ) {
        public List<String> safeEmails() {
            return emails == null ? List.of() : emails;
        }
    }

    public record JoinInvitation(
            @NotBlank String inviteToken,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String companyName,
            @NotBlank @Size(max = 100) String teamName,
            @Size(max = 100) String jobTitle
    ) {
    }
}
