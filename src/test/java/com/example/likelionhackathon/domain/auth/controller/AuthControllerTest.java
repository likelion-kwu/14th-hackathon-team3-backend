package com.example.likelionhackathon.domain.auth.controller;

import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.entity.UserEnums.ActivityStatus;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.global.security.jwt.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    private Long userId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = User.create("test@example.com", passwordEncoder.encode("password123!"), "홍길동");
        userId = userRepository.save(user).getId();
    }

    @Test
    void loginReturnsUserIdAndAccessTokenWhenCredentialsAreValid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("test@example.com", "password123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("200OK"))
                .andExpect(jsonPath("$.message").value("로그인이 완료되었습니다."))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    String token = JsonPath.read(response, "$.data.accessToken");
                    assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(userId);
                });

        assertThat(userRepository.findById(userId).orElseThrow().getActivityStatus())
                .isEqualTo(ActivityStatus.ACTIVE);
    }

    @Test
    void loginRejectsUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("notfound@example.com", "password123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401INVALID_LOGIN_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void loginRejectsWrongPasswordWithSameError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("test@example.com", "wrongPassword!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401INVALID_LOGIN_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));

        assertThat(userRepository.findById(userId).orElseThrow().getActivityStatus())
                .isEqualTo(ActivityStatus.OFF);
    }

    @Test
    void loginFailurePreservesActiveStatus() throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        user.changeActivityStatus(ActivityStatus.ACTIVE);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("test@example.com", "wrongPassword!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401INVALID_LOGIN_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));

        assertThat(userRepository.findById(userId).orElseThrow().getActivityStatus())
                .isEqualTo(ActivityStatus.ACTIVE);
    }

    @Test
    void logoutChangesCurrentUserActivityStatusToOff() throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        user.changeActivityStatus(ActivityStatus.ACTIVE);
        userRepository.saveAndFlush(user);

        String accessToken = jwtTokenProvider.createAccessToken(userId);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("200OK"))
                .andExpect(jsonPath("$.message").value("로그아웃이 완료되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(userRepository.findById(userId).orElseThrow().getActivityStatus())
                .isEqualTo(ActivityStatus.OFF);
    }

    @Test
    void logoutRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401UNAUTHORIZED"));
    }

    @Test
    void loginRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("invalid-email", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
    }

    private String loginRequest(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
