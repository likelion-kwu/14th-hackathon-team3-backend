package com.example.likelionhackathon.domain.workspace.entity;

import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceCompanyRole;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
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

    @OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<WorkspaceCompany> companies = new ArrayList<>();

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
        synchronizeRegisteredCompanies(companyName, companyCountryCode, collaboratingCompanyNames);
    }

    private void replaceCollaboratingCompanies(Collection<String> companyNames) {
        collaboratingCompanyNames.clear();
        if (companyNames != null) {
            collaboratingCompanyNames.addAll(companyNames);
        }
    }

    public void addCompany(WorkspaceCompany company) {
        company.attachTo(this);
        companies.add(company);
    }

    private void synchronizeRegisteredCompanies(
            String hostCompanyName,
            String hostCountryCode,
            Collection<String> partnerCompanyNames
    ) {
        if (companies.isEmpty()) {
            return;
        }

        companies.stream()
                .filter(company -> company.getRole() == WorkspaceCompanyRole.HOST)
                .findFirst()
                .ifPresent(company -> company.update(hostCompanyName, hostCountryCode));

        List<WorkspaceCompany> partners = companies.stream()
                .filter(company -> company.getRole() == WorkspaceCompanyRole.PARTNER)
                .toList();
        List<String> names = partnerCompanyNames == null
                ? List.of()
                : List.copyOf(partnerCompanyNames);
        for (int index = 0; index < names.size(); index++) {
            if (index < partners.size()) {
                WorkspaceCompany partner = partners.get(index);
                partner.update(names.get(index), partner.getCountryCode());
            } else {
                addCompany(WorkspaceCompany.partner(names.get(index), hostCountryCode));
            }
        }
        if (partners.size() > names.size()) {
            companies.removeAll(partners.subList(names.size(), partners.size()));
        }
    }
}
