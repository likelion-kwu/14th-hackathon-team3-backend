package com.example.likelionhackathon.domain.workspace.service;

import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.entity.UserEnums.ActivityStatus;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceRepository;
import com.example.likelionhackathon.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkspaceOrganizationChartIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;

    private User requester;
    private Workspace workspace;
    private String authorization;

    @BeforeEach
    void setUp() {
        requester = saveUser("organization-owner@example.com", "Owner", ActivityStatus.ACTIVE);
        workspace = workspaceRepository.save(Workspace.create(
                "Organization Workspace", "ORG-KR-0001", "RelAI", "KR", List.of()
        ));
        memberRepository.save(member(
                requester, "Zoe", "RelAI", "Engineering", "Lead", WorkspaceRole.OWNER
        ));
        authorization = bearer(requester);
    }

    @Test
    void getOrganizationChartGroupsSortsAndCombinesActivityStatus() throws Exception {
        User amy = saveUser("organization-amy@example.com", "Amy User", ActivityStatus.OFF);
        User bob = saveUser("organization-bob@example.com", "Bob User", ActivityStatus.ACTIVE);
        User kim = saveUser("organization-kim@example.com", "Kim User", ActivityStatus.OFF);
        User hidden = saveUser("organization-hidden@example.com", "Hidden User", ActivityStatus.ACTIVE);
        memberRepository.save(member(amy, "Amy", "Partner", "Engineering", null, WorkspaceRole.MEMBER));
        memberRepository.save(member(bob, "Bob", "RelAI", "Design", "Designer", WorkspaceRole.MEMBER));
        memberRepository.save(member(kim, "Kim", "RelAI", null, null, WorkspaceRole.MEMBER));
        WorkspaceMember suspended = member(
                hidden, "Hidden", "RelAI", "Product", null, WorkspaceRole.MEMBER
        );
        suspended.suspend();
        memberRepository.save(suspended);

        mockMvc.perform(get(url()).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조직도를 조회했습니다."))
                .andExpect(jsonPath("$.data.workspaceId").value(workspace.getId()))
                .andExpect(jsonPath("$.data.teams.length()").value(3))
                .andExpect(jsonPath("$.data.teams[0].teamName").value("Design"))
                .andExpect(jsonPath("$.data.teams[1].teamName").value("Engineering"))
                .andExpect(jsonPath("$.data.teams[1].members[0].name").value("Amy"))
                .andExpect(jsonPath("$.data.teams[1].members[0].companyName").value("Partner"))
                .andExpect(jsonPath("$.data.teams[1].members[0].jobTitle").doesNotExist())
                .andExpect(jsonPath("$.data.teams[1].members[0].activityStatus").value("OFF"))
                .andExpect(jsonPath("$.data.teams[1].members[1].name").value("Zoe"))
                .andExpect(jsonPath("$.data.teams[2].teamName").doesNotExist())
                .andExpect(jsonPath("$.data.teams[2].members[0].name").value("Kim"))
                .andExpect(jsonPath("$.data.teams[*].members[*].name").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Hidden"))
                ));
    }

    @Test
    void getOrganizationChartRequiresJwt() throws Exception {
        mockMvc.perform(get(url()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401UNAUTHORIZED"));
    }

    @Test
    void getOrganizationChartRejectsNonMemberAndSuspendedRequester() throws Exception {
        User outsider = saveUser("organization-outsider@example.com", "Outsider", ActivityStatus.OFF);
        mockMvc.perform(get(url()).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403WORKSPACE_ACCESS_DENIED"));

        WorkspaceMember requesterMember = memberRepository
                .findByWorkspaceIdAndPrincipalKey(workspace.getId(), requester.getId().toString())
                .orElseThrow();
        requesterMember.suspend();
        memberRepository.flush();

        mockMvc.perform(get(url()).header("Authorization", authorization))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void getOrganizationChartRejectsMissingWorkspace() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/organization-chart", Long.MAX_VALUE)
                        .header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404WORKSPACE_NOT_FOUND"));
    }

    @Test
    void getOrganizationChartRejectsMissingUser() throws Exception {
        memberRepository.save(WorkspaceMember.createInvitedMember(
                workspace, "999999999", "Missing", null, "RelAI", "Engineering", null,
                WorkspaceRole.MEMBER
        ));

        mockMvc.perform(get(url()).header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404USER_NOT_FOUND"));
    }

    @Test
    void getOrganizationChartRejectsNonNumericPrincipalKeyAsInternalDataError() throws Exception {
        memberRepository.save(WorkspaceMember.createInvitedMember(
                workspace, "legacy@example.com", "Legacy", null, "RelAI", "Engineering", null,
                WorkspaceRole.MEMBER
        ));

        mockMvc.perform(get(url()).header("Authorization", authorization))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500INTERNAL_SERVER_ERROR"));
    }

    private User saveUser(String email, String name, ActivityStatus activityStatus) {
        User user = User.create(email, passwordEncoder.encode("password123!"), name);
        user.changeActivityStatus(activityStatus);
        return userRepository.save(user);
    }

    private WorkspaceMember member(
            User user,
            String name,
            String companyName,
            String teamName,
            String jobTitle,
            WorkspaceRole role
    ) {
        return WorkspaceMember.createInvitedMember(
                workspace,
                user.getId().toString(),
                name,
                user.getEmail(),
                companyName,
                teamName,
                jobTitle,
                role
        );
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    private String url() {
        return "/api/v1/workspaces/" + workspace.getId() + "/organization-chart";
    }
}
