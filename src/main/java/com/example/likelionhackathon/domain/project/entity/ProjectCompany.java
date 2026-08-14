package com.example.likelionhackathon.domain.project.entity;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ParticipatingCompanyRole;
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
        name = "project_companies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_company",
                columnNames = {"project_id", "company_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParticipatingCompanyRole role;

    public ProjectCompany(Long companyId, String name, ParticipatingCompanyRole role) {
        this.companyId = companyId;
        this.name = name;
        this.role = role;
    }

    public void update(String name, ParticipatingCompanyRole role) {
        this.name = name;
        this.role = role;
    }

    void attachTo(Project project) {
        this.project = project;
    }
}
