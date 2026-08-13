package com.example.likelionhackathon.domain.auth.controller;

import com.example.likelionhackathon.domain.auth.entity.PasswordResetVerification;
import com.example.likelionhackathon.domain.auth.repository.PasswordResetVerificationRepository;
import com.example.likelionhackathon.domain.auth.service.EmailVerificationCodeGenerator;
import com.example.likelionhackathon.domain.auth.service.PasswordResetMailService;
import com.example.likelionhackathon.domain.auth.service.PasswordResetTokenGenerator;
import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordResetVerificationRepository repository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean EmailVerificationCodeGenerator codeGenerator;
    @MockitoBean PasswordResetTokenGenerator tokenGenerator;
    @MockitoBean PasswordResetMailService mailService;

    @BeforeEach void setUp() {
        repository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.create("user@example.com", passwordEncoder.encode("oldPassword!"), "홍길동"));
    }

    @Test void registeredEmailRequestStoresHashAndSendsMail() throws Exception {
        when(codeGenerator.generate()).thenReturn("004821");
        request("user@example.com").andExpect(status().isOk());
        PasswordResetVerification saved = repository.findByEmail("user@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("004821", saved.getEncodedVerificationCode())).isTrue();
        verify(mailService).sendVerificationCode("user@example.com", "004821");
    }

    @Test void unknownEmailReturnsSuccessWithoutStorageOrMail() throws Exception {
        request("missing@example.com").andExpect(status().isOk());
        assertThat(repository.findByEmail("missing@example.com")).isEmpty();
        verifyNoInteractions(mailService);
    }

    @Test void reissueInvalidatesOldCodeAndToken() throws Exception {
        PasswordResetVerification v = verified("111111", "old-token");
        repository.save(v);
        when(codeGenerator.generate()).thenReturn("222222");
        request("user@example.com").andExpect(status().isOk());
        PasswordResetVerification updated = repository.findByEmail("user@example.com").orElseThrow();
        assertThat(updated.isVerified()).isFalse();
        assertThat(updated.getEncodedResetToken()).isNull();
        assertThat(passwordEncoder.matches("222222", updated.getEncodedVerificationCode())).isTrue();
    }

    @Test void verifyReturnsTokenAndStoresOnlyHash() throws Exception {
        repository.save(PasswordResetVerification.create("user@example.com", passwordEncoder.encode("381205"), OffsetDateTime.now().plusMinutes(5)));
        when(tokenGenerator.generate()).thenReturn("safe-reset-token");
        verifyCode("381205").andExpect(status().isOk()).andExpect(jsonPath("$.data.resetToken").value("safe-reset-token"));
        PasswordResetVerification saved = repository.findByEmail("user@example.com").orElseThrow();
        assertThat(saved.isVerified()).isTrue();
        assertThat(saved.getEncodedResetToken()).isNotEqualTo("safe-reset-token");
        assertThat(passwordEncoder.matches("safe-reset-token", saved.getEncodedResetToken())).isTrue();
    }

    @Test void wrongAndExpiredVerificationCodesFail() throws Exception {
        repository.save(PasswordResetVerification.create("user@example.com", passwordEncoder.encode("381205"), OffsetDateTime.now().plusMinutes(5)));
        verifyCode("927104").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("400INVALID_VERIFICATION_CODE"));
        PasswordResetVerification saved = repository.findByEmail("user@example.com").orElseThrow();
        ReflectionTestUtils.setField(saved, "verificationExpiresAt", OffsetDateTime.now().minusSeconds(1));
        repository.save(saved);
        verifyCode("381205").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("400EXPIRED_VERIFICATION_CODE"));
    }

    @Test void resetChangesPasswordConsumesTokenAndSupportsNewLogin() throws Exception {
        repository.save(verified("381205", "reset-token"));
        reset("reset-token", "newPassword!", "newPassword!").andExpect(status().isOk());
        User user = userRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("newPassword!", user.getPassword())).isTrue();
        assertThat(repository.findByEmail("user@example.com").orElseThrow().isUsed()).isTrue();
        reset("reset-token", "anotherPassword!", "anotherPassword!").andExpect(status().isBadRequest());
        login("oldPassword!").andExpect(status().isUnauthorized());
        login("newPassword!").andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test void invalidOrExpiredTokenDoesNotChangePassword() throws Exception {
        PasswordResetVerification v = verified("381205", "reset-token");
        repository.save(v);
        reset("wrong-token", "newPassword!", "newPassword!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_PASSWORD_RESET_TOKEN"));
        ReflectionTestUtils.setField(v, "resetTokenExpiresAt", OffsetDateTime.now().minusSeconds(1));
        repository.save(v);
        reset("reset-token", "newPassword!", "newPassword!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400EXPIRED_PASSWORD_RESET_TOKEN"));
        assertThat(passwordEncoder.matches("oldPassword!", userRepository.findByEmail("user@example.com").orElseThrow().getPassword())).isTrue();
    }

    @Test void passwordConfirmationMustMatch() throws Exception {
        repository.save(verified("381205", "reset-token"));
        reset("reset-token", "newPassword!", "differentPassword!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400PASSWORD_MISMATCH"));
    }

    private PasswordResetVerification verified(String code, String token) {
        PasswordResetVerification v = PasswordResetVerification.create("user@example.com", passwordEncoder.encode(code), OffsetDateTime.now().plusMinutes(5));
        v.completeVerification(OffsetDateTime.now(), passwordEncoder.encode(token), OffsetDateTime.now().plusMinutes(10));
        return v;
    }
    private org.springframework.test.web.servlet.ResultActions request(String email) throws Exception { return mockMvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\"}")); }
    private org.springframework.test.web.servlet.ResultActions verifyCode(String code) throws Exception { return mockMvc.perform(post("/api/v1/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user@example.com\",\"verificationCode\":\""+code+"\"}")); }
    private org.springframework.test.web.servlet.ResultActions reset(String token,String password,String confirm) throws Exception { return mockMvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user@example.com\",\"resetToken\":\""+token+"\",\"newPassword\":\""+password+"\",\"newPasswordConfirm\":\""+confirm+"\"}")); }
    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception { return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user@example.com\",\"password\":\""+password+"\"}")); }
}
