package com.example.likelionhackathon.domain.workspace.entity;

import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.InvitationStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.InvitationType;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "workspace_invitations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationType type;

    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkspaceRole role;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationStatus status;

    public static WorkspaceInvitation create(
            Workspace workspace,
            InvitationType type,
            String email,
            WorkspaceRole role,
            String token,
            OffsetDateTime expiresAt
    ) {
        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.workspace = workspace;
        invitation.type = type;
        invitation.email = email;
        invitation.role = role;
        invitation.token = token;
        invitation.expiresAt = expiresAt;
        invitation.status = InvitationStatus.PENDING;
        return invitation;
    }

    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void accept() {
        status = InvitationStatus.ACCEPTED;
    }
}
