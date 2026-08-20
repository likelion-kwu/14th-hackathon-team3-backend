package com.example.likelionhackathon.domain.workspace.entity;

import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.AssignableWorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceMemberStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "workspace_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_workspace_member_principal",
                columnNames = {"workspace_id", "principal_key"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "principal_key", nullable = false, length = 100)
    private String principalKey;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column
    private Long companyId;

    @Column(length = 100)
    private String teamName;

    @Column(length = 100)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkspaceRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkspaceMemberStatus status;

    public static WorkspaceMember createOwner(
            Workspace workspace,
            String principalKey,
            String name,
            String companyName
    ) {
        return createOwner(workspace, principalKey, name, companyName, null);
    }

    public static WorkspaceMember createOwner(
            Workspace workspace,
            String principalKey,
            String name,
            String companyName,
            Long companyId
    ) {
        return create(workspace, principalKey, name, null, companyName, companyId, null, null, WorkspaceRole.OWNER);
    }

    public static WorkspaceMember createInvitedMember(
            Workspace workspace,
            String principalKey,
            String name,
            String email,
            String companyName,
            String teamName,
            String jobTitle,
            WorkspaceRole role
    ) {
        return createInvitedMember(
                workspace, principalKey, name, email, companyName, null, teamName, jobTitle, role
        );
    }

    public static WorkspaceMember createInvitedMember(
            Workspace workspace,
            String principalKey,
            String name,
            String email,
            String companyName,
            Long companyId,
            String teamName,
            String jobTitle,
            WorkspaceRole role
    ) {
        return create(workspace, principalKey, name, email, companyName, companyId, teamName, jobTitle, role);
    }

    private static WorkspaceMember create(
            Workspace workspace,
            String principalKey,
            String name,
            String email,
            String companyName,
            Long companyId,
            String teamName,
            String jobTitle,
            WorkspaceRole role
    ) {
        WorkspaceMember member = new WorkspaceMember();
        member.workspace = workspace;
        member.principalKey = principalKey;
        member.name = name;
        member.email = email;
        member.companyName = companyName;
        member.companyId = companyId;
        member.teamName = teamName;
        member.jobTitle = jobTitle;
        member.role = role;
        member.status = WorkspaceMemberStatus.ACTIVE;
        return member;
    }

    public void update(AssignableWorkspaceRole role, String teamName, String jobTitle) {
        if (role != null) {
            this.role = role.toWorkspaceRole();
        }
        if (teamName != null) {
            this.teamName = teamName;
        }
        if (jobTitle != null) {
            this.jobTitle = jobTitle;
        }
        this.status = WorkspaceMemberStatus.ACTIVE;
    }

    public void updateProfile(String name, String companyName, String teamName, String jobTitle) {
        if (name != null) {
            this.name = name;
        }
        if (companyName != null) {
            this.companyName = companyName;
        }
        if (teamName != null) {
            this.teamName = teamName;
        }
        this.jobTitle = jobTitle;
    }

    public void assignCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public void suspend() {
        status = WorkspaceMemberStatus.SUSPENDED;
    }
}
