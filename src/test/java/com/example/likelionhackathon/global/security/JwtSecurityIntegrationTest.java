package com.example.likelionhackathon.global.security;

import com.example.likelionhackathon.global.common.ApiResponse;
import com.example.likelionhackathon.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(JwtSecurityIntegrationTest.SecurityTestController.class)
class JwtSecurityIntegrationTest {

    private static final String TEST_SECRET =
            "VGhpcy1pcy1hLXRlc3Qtb25seS0zMi1ieXRlLWp3dC1zZWNyZXQ=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void healthIsPublicWithoutJwt() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void publicAuthApiIsNotBlockedBySecurity() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    void publicHealthIsNotBlockedByInvalidJwt() throws Exception {
        mockMvc.perform(get("/health")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }

    @Test
    void publicLoginIsNotBlockedByExpiredJwt() throws Exception {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(TEST_SECRET, -60_000L);
        String token = expiredProvider.createAccessToken(9L);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    void protectedApiWithoutAuthorizationReturnsApiResponse401() throws Exception {
        mockMvc.perform(get("/api/v1/security-test/principal"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("401UNAUTHORIZED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void malformedJwtReturnsApiResponse401() throws Exception {
        mockMvc.perform(get("/api/v1/security-test/principal")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("401UNAUTHORIZED"));
    }

    @Test
    void expiredJwtReturnsApiResponse401() throws Exception {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(TEST_SECRET, -60_000L);
        String token = expiredProvider.createAccessToken(9L);

        mockMvc.perform(get("/api/v1/security-test/principal")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401UNAUTHORIZED"));
    }

    @Test
    void validJwtPassesSecurityAndCurrentUserProviderReturnsSubject() throws Exception {
        String token = jwtTokenProvider.createAccessToken(42L);

        mockMvc.perform(get("/api/v1/security-test/principal")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("42"));
    }

    @Test
    void authenticationDoesNotLeakToNextRequest() throws Exception {
        String token = jwtTokenProvider.createAccessToken(77L);
        mockMvc.perform(get("/api/v1/security-test/principal")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("77"));

        mockMvc.perform(get("/api/v1/security-test/principal"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    @RequestMapping("/api/v1/security-test")
    static class SecurityTestController {

        private final CurrentUserProvider currentUserProvider;

        SecurityTestController(CurrentUserProvider currentUserProvider) {
            this.currentUserProvider = currentUserProvider;
        }

        @GetMapping("/principal")
        ApiResponse<String> principal() {
            return ApiResponse.success(currentUserProvider.currentPrincipalKey());
        }
    }
}
