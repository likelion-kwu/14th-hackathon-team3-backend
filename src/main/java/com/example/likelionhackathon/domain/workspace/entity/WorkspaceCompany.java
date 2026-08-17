package com.example.likelionhackathon.domain.workspace.entity;

import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceCompanyRole;
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
        name = "workspace_companies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_workspace_company_name",
                columnNames = {"workspace_id", "name"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkspaceCompanyRole role;

    public static WorkspaceCompany host(String name, String countryCode) {
        return create(name, countryCode, WorkspaceCompanyRole.HOST);
    }

    public static WorkspaceCompany partner(String name, String countryCode) {
        return create(name, countryCode, WorkspaceCompanyRole.PARTNER);
    }

    private static WorkspaceCompany create(
            String name,
            String countryCode,
            WorkspaceCompanyRole role
    ) {
        WorkspaceCompany company = new WorkspaceCompany();
        company.name = name;
        company.countryCode = countryCode;
        company.role = role;
        return company;
    }

    void attachTo(Workspace workspace) {
        this.workspace = workspace;
    }

    void update(String name, String countryCode) {
        this.name = name;
        this.countryCode = countryCode;
    }
}
