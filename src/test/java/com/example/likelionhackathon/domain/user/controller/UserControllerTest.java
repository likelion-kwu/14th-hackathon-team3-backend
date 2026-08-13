package com.example.likelionhackathon.domain.user.controller;

import com.example.likelionhackathon.domain.auth.entity.EmailVerification;
import com.example.likelionhackathon.domain.auth.repository.EmailVerificationRepository;
import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationRepository verificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        verificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void signupStoresUserAfterEmailVerification() throws Exception {
        saveVerifiedEmail("user@example.com");

        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("user@example.com", "password123!", "password123!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("201CREATED"))
                .andExpect(jsonPath("$.data.userId").isNumber());

        User saved = userRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("password123!");
        assertThat(passwordEncoder.matches("password123!", saved.getPassword())).isTrue();
        assertThat(saved.getCountry()).isNull();
        assertThat(saved.getLanguage()).isNull();
        assertThat(saved.getTimezone()).isNull();
        assertThat(verificationRepository.findByEmail("user@example.com")).isEmpty();
    }

    @Test
    void signupRejectsMissingOrUnverifiedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("missing@example.com", "password123!", "password123!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400EMAIL_NOT_VERIFIED"));

        verificationRepository.save(EmailVerification.create("unverified@example.com",
                passwordEncoder.encode("123456"), OffsetDateTime.now().plusMinutes(5)));
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("unverified@example.com", "password123!", "password123!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400EMAIL_NOT_VERIFIED"));
    }

    @Test
    void signupRejectsPasswordMismatch() throws Exception {
        saveVerifiedEmail("user@example.com");
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("user@example.com", "password123!", "differentPassword!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400PASSWORD_MISMATCH"));
    }

    @Test
    void signupRejectsDuplicateEmail() throws Exception {
        userRepository.save(User.create("duplicate@example.com", passwordEncoder.encode("password123!"), "홍길동"));
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("duplicate@example.com", "password123!", "password123!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409DUPLICATE_EMAIL"));
    }

    @Test
    void signupRejectsInvalidEmailAndBlankPasswordConfirm() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("invalid-email", "password123!", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
    }

    @Test
    void signupUserCanLoginWithSameCredentials() throws Exception {
        saveVerifiedEmail("login@example.com");
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("login@example.com", "password123!", "password123!")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@example.com\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200OK"))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    private void saveVerifiedEmail(String email) {
        EmailVerification verification = EmailVerification.create(email,
                passwordEncoder.encode("123456"), OffsetDateTime.now().plusMinutes(5));
        verification.verify(OffsetDateTime.now());
        verificationRepository.save(verification);
    }

    private String signupRequest(String email, String password, String passwordConfirm) {
        return """
                {"name":"홍길동","email":"%s","password":"%s","passwordConfirm":"%s"}
                """.formatted(email, password, passwordConfirm);
    }
}
