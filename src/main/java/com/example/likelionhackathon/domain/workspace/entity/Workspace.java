package com.example.likelionhackathon.domain.workspace.entity;

import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Entity
@Table(name = "workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 32)
    private String organizationCode;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false, length = 2)
    private String companyCountryCode;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "workspace_collaborating_companies")
    @Column(name = "company_name", nullable = false, length = 100)
    private Set<String> collaboratingCompanyNames = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkspaceStatus status;

    @Version
    private Long version;

    public static Workspace create(
            String name,
            String organizationCode,
            String companyName,
            String companyCountryCode,
            Collection<String> collaboratingCompanyNames
    ) {
        Workspace workspace = new Workspace();
        workspace.name = name;
        workspace.organizationCode = organizationCode;
        workspace.companyName = companyName;
        workspace.companyCountryCode = companyCountryCode;
        workspace.status = WorkspaceStatus.ACTIVE;
        workspace.replaceCollaboratingCompanies(collaboratingCompanyNames);
        return workspace;
    }

    public void update(
            String name,
            String companyName,
            String companyCountryCode,
            Collection<String> collaboratingCompanyNames,
            WorkspaceStatus status
    ) {
        this.name = name;
        this.companyName = companyName;
        this.companyCountryCode = companyCountryCode;
        this.status = status;
        replaceCollaboratingCompanies(collaboratingCompanyNames);
    }

    private void replaceCollaboratingCompanies(Collection<String> companyNames) {
        collaboratingCompanyNames.clear();
        if (companyNames != null) {
            collaboratingCompanyNames.addAll(companyNames);
        }
    }
}
