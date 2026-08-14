package com.example.likelionhackathon.domain.project.entity;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.AccessScope;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberStatus;
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
        name = "project_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_member_principal",
                columnNames = {"project_id", "principal_key"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "workspace_member_id")
    private Long workspaceMemberId;

    @Column(name = "principal_key", nullable = false, length = 100)
    private String principalKey;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String companyName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private ProjectTeam team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProjectMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AccessScope accessScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectMemberStatus status;

    public static ProjectMember createAdmin(
            Long workspaceMemberId,
            String principalKey,
            String name,
            Long companyId,
            String companyName,
            ProjectTeam team
    ) {
        ProjectMember member = new ProjectMember();
        member.workspaceMemberId = workspaceMemberId;
        member.principalKey = principalKey;
        member.name = name;
        member.companyId = companyId;
        member.companyName = companyName;
        member.team = team;
        member.role = ProjectMemberRole.PROJECT_ADMIN;
        member.accessScope = AccessScope.FULL;
        member.status = ProjectMemberStatus.ACTIVE;
        return member;
    }

    public void update(
            ProjectTeam team,
            String companyName,
            ProjectMemberRole role,
            AccessScope accessScope
    ) {
        if (team != null) {
            this.team = team;
            this.companyId = team.getCompanyId();
            this.companyName = companyName;
        }
        if (role != null) {
            this.role = role;
        }
        if (accessScope != null) {
            this.accessScope = accessScope;
        }
        status = ProjectMemberStatus.ACTIVE;
    }

    public void suspend() {
        status = ProjectMemberStatus.SUSPENDED;
    }

    void attachTo(Project project) {
        this.project = project;
    }
}
