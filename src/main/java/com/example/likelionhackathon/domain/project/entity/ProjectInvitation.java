package com.example.likelionhackathon.domain.project.entity;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.AccessScope;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberRole;
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

@Getter
@Entity
@Table(name = "project_invitations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 255)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private ProjectTeam team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProjectMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AccessScope accessScope;

    @Column(nullable = false)
    private boolean pending;

    public static ProjectInvitation create(
            String email,
            ProjectTeam team,
            ProjectMemberRole role,
            AccessScope accessScope
    ) {
        ProjectInvitation invitation = new ProjectInvitation();
        invitation.email = email;
        invitation.team = team;
        invitation.role = role;
        invitation.accessScope = accessScope;
        invitation.pending = true;
        return invitation;
    }

    void attachTo(Project project) {
        this.project = project;
    }
}
