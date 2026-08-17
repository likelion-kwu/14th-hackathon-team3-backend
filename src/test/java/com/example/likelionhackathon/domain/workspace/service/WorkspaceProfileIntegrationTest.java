package com.example.likelionhackathon.domain.workspace.service;

import com.example.likelionhackathon.domain.user.entity.User;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkspaceProfileIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;

    private User user;
    private Workspace workspace;
    private WorkspaceMember member;
    private String authorization;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create(
                "profile@example.com", passwordEncoder.encode("password123!"), "Global Name"
        ));
        workspace = workspaceRepository.save(Workspace.create(
                "Profile Workspace", "PROFILE-KR-0001", "Workspace Company", "KR", List.of()
        ));
        member = memberRepository.save(WorkspaceMember.createInvitedMember(
                workspace, user.getId().toString(), "Workspace Name", user.getEmail(),
                "Member Company", "Backend", "Developer", WorkspaceRole.MEMBER
        ));
        authorization = "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    @Test
    void getProfileReturnsWorkspaceMemberProfile() throws Exception {
        mockMvc.perform(get(profileUrl()).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.workspaceId").value(workspace.getId()))
                .andExpect(jsonPath("$.data.name").value("Workspace Name"))
                .andExpect(jsonPath("$.data.companyName").value("Member Company"))
                .andExpect(jsonPath("$.data.teamName").value("Backend"))
                .andExpect(jsonPath("$.data.jobTitle").value("Developer"));
    }

    @Test
    void getProfileUsesRequestedWorkspace() throws Exception {
        Workspace other = workspaceRepository.save(Workspace.create(
                "Other Workspace", "PROFILE-KR-0002", "Other Workspace Company", "KR", List.of()
        ));
        memberRepository.save(WorkspaceMember.createInvitedMember(
                other, user.getId().toString(), "Other Name", user.getEmail(),
                "Other Company", "AI", "Researcher", WorkspaceRole.MEMBER
        ));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/members/me/profile", other.getId())
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceId").value(other.getId()))
                .andExpect(jsonPath("$.data.name").value("Other Name"));
    }

    @Test
    void membershipIsRequired() throws Exception {
        User outsider = userRepository.save(User.create(
                "outsider@example.com", passwordEncoder.encode("password123!"), "Outsider"
        ));

        mockMvc.perform(get(profileUrl()).header(
                        "Authorization", "Bearer " + jwtTokenProvider.createAccessToken(outsider.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void updateProfileChangesAllFieldsWithoutChangingUserOrWorkspace() throws Exception {
        mockMvc.perform(patch(profileUrl())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Updated Name  ","companyName":"  Updated Company  ",
                                 "teamName":"  AI  ","jobTitle":"  Lead  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.companyName").value("Updated Company"))
                .andExpect(jsonPath("$.data.teamName").value("AI"))
                .andExpect(jsonPath("$.data.jobTitle").value("Lead"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getName()).isEqualTo("Global Name");
        assertThat(workspaceRepository.findById(workspace.getId()).orElseThrow().getCompanyName())
                .isEqualTo("Workspace Company");
    }

    @Test
    void partialUpdateKeepsOmittedFields() throws Exception {
        mockMvc.perform(patch(profileUrl())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"AI\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Workspace Name"))
                .andExpect(jsonPath("$.data.companyName").value("Member Company"))
                .andExpect(jsonPath("$.data.teamName").value("AI"))
                .andExpect(jsonPath("$.data.jobTitle").value("Developer"));
    }

    @Test
    void blankRequiredProfileFieldsAreRejected() throws Exception {
        for (String body : List.of(
                "{\"name\":\"   \"}",
                "{\"companyName\":\"\"}",
                "{\"teamName\":\"   \"}"
        )) {
            mockMvc.perform(patch(profileUrl())
                            .header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
        }
    }

    @Test
    void blankJobTitleDeletesJobTitle() throws Exception {
        mockMvc.perform(patch(profileUrl())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobTitle").doesNotExist());

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getJobTitle()).isNull();
    }

    @Test
    void emptyPatchIsRejected() throws Exception {
        mockMvc.perform(patch(profileUrl())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
    }

    @Test
    void jwtIsRequired() throws Exception {
        mockMvc.perform(get(profileUrl()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401UNAUTHORIZED"));
    }

    private String profileUrl() {
        return "/api/v1/workspaces/" + workspace.getId() + "/members/me/profile";
    }
}
