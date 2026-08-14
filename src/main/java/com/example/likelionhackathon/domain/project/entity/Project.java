package com.example.likelionhackathon.domain.project.entity;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectStatus;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(
        name = "projects",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_workspace_name",
                columnNames = {"workspace_id", "name"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 2000)
    private String objective;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectStatus status;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProjectCompany> participatingCompanies = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProjectTeam> teams = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProjectMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProjectInvitation> invitations = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProjectIntegration> integrations = new ArrayList<>();

    @Version
    private Long version;

    public static Project create(
            Workspace workspace,
            String name,
            String objective,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Project project = new Project();
        project.workspace = workspace;
        project.name = name;
        project.objective = objective;
        project.startDate = startDate;
        project.endDate = endDate;
        project.status = ProjectStatus.DRAFT;
        return project;
    }

    public void update(
            String name,
            String objective,
            LocalDate startDate,
            LocalDate endDate,
            ProjectStatus status
    ) {
        this.name = name;
        this.objective = objective;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    public void replaceCompanies(List<ProjectCompany> companies) {
        participatingCompanies.removeIf(existing -> companies.stream()
                .noneMatch(replacement -> replacement.getCompanyId().equals(existing.getCompanyId())));
        for (ProjectCompany replacement : companies) {
            participatingCompanies.stream()
                    .filter(existing -> existing.getCompanyId().equals(replacement.getCompanyId()))
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> existing.update(replacement.getName(), replacement.getRole()),
                            () -> addCompany(replacement)
                    );
        }
    }

    public void addCompany(ProjectCompany company) {
        company.attachTo(this);
        participatingCompanies.add(company);
    }

    public void addTeam(ProjectTeam team) {
        team.attachTo(this);
        teams.add(team);
    }

    public void addMember(ProjectMember member) {
        member.attachTo(this);
        members.add(member);
    }

    public void addInvitation(ProjectInvitation invitation) {
        invitation.attachTo(this);
        invitations.add(invitation);
    }

    public void removeMember(ProjectMember member) {
        members.remove(member);
    }

    public void addIntegration(ProjectIntegration integration) {
        integration.attachTo(this);
        integrations.add(integration);
    }
}
