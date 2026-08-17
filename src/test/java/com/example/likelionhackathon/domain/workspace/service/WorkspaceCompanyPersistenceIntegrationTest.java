package com.example.likelionhackathon.domain.workspace.service;

import com.example.likelionhackathon.domain.workspace.dto.WorkspaceRequest;
import com.example.likelionhackathon.domain.workspace.dto.WorkspaceResponse;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceCompany;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceCompanyRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class WorkspaceCompanyPersistenceIntegrationTest {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPersistsGeneratedCompanyIdsByHostAndPartnerRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("company-owner", "N/A", List.of())
        );

        WorkspaceResponse.Created response = workspaceService.create(new WorkspaceRequest.Create(
                "Workspace Company Registry",
                "RelAI",
                "kr",
                List.of(
                        new WorkspaceRequest.CollaboratingCompany("Microsoft", "us"),
                        new WorkspaceRequest.CollaboratingCompany("Notion", "jp")
                ),
                List.of()
        ));

        assertThat(response.company().companyId()).isPositive();
        assertThat(response.company().role()).isEqualTo(WorkspaceCompanyRole.HOST);
        assertThat(response.collaboratingCompanies())
                .extracting(WorkspaceResponse.Company::name)
                .containsExactly("Microsoft", "Notion");
        assertThat(response.collaboratingCompanies())
                .extracting(WorkspaceResponse.Company::companyId)
                .allMatch(companyId -> companyId != null && companyId > 0);

        Workspace saved = workspaceRepository.findById(response.workspaceId()).orElseThrow();
        assertThat(saved.getCompanies())
                .extracting(
                        WorkspaceCompany::getName,
                        WorkspaceCompany::getCountryCode,
                        WorkspaceCompany::getRole
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "RelAI", "KR", WorkspaceCompanyRole.HOST
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "Microsoft", "US", WorkspaceCompanyRole.PARTNER
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "Notion", "JP", WorkspaceCompanyRole.PARTNER
                        )
                );
    }

    @Test
    void detailBackfillsCompanyIdsForWorkspaceCreatedBeforeCompanyRegistry() {
        Workspace legacy = workspaceRepository.saveAndFlush(Workspace.create(
                "Legacy Workspace Company Registry",
                "RELAI-KR-LEGACY",
                "Legacy Host",
                "KR",
                List.of("Legacy Partner")
        ));
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.createOwner(
                legacy,
                "legacy-company-owner",
                "Legacy Owner",
                "Legacy Host"
        ));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "legacy-company-owner", "N/A", List.of()
                )
        );

        WorkspaceResponse.Detail detail = workspaceService.getDetail(legacy.getId());

        assertThat(detail.company().companyId()).isPositive();
        assertThat(detail.collaboratingCompanies()).singleElement()
                .extracting(WorkspaceResponse.Company::companyId)
                .isNotNull();
        assertThat(legacy.getCompanies()).hasSize(2);
    }
}
