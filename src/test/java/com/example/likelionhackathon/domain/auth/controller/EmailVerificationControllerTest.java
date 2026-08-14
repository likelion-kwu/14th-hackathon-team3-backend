package com.example.likelionhackathon.domain.auth.controller;

import com.example.likelionhackathon.domain.auth.dto.AuthRequest;
import com.example.likelionhackathon.domain.auth.entity.EmailVerification;
import com.example.likelionhackathon.domain.auth.repository.EmailVerificationRepository;
import com.example.likelionhackathon.domain.auth.service.EmailVerificationCodeGenerator;
import com.example.likelionhackathon.domain.auth.service.EmailVerificationMailService;
import com.example.likelionhackathon.domain.auth.service.EmailVerificationService;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private EmailVerificationRepository verificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailVerificationService emailVerificationService;
    @MockitoBean private EmailVerificationCodeGenerator codeGenerator;
    @MockitoBean private EmailVerificationMailService mailService;

    @BeforeEach
    void setUp() {
        verificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void requestStoresEncodedCodeAndSendsEmail() throws Exception {
        when(codeGenerator.generate()).thenReturn("004821");

        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(emailRequest("user@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200OK"))
                .andExpect(jsonPath("$.message").value("인증번호가 이메일로 발송되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        EmailVerification saved = verificationRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(saved.getEncodedVerificationCode()).isNotEqualTo("004821");
        assertThat(passwordEncoder.matches("004821", saved.getEncodedVerificationCode())).isTrue();
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getFailedAttempts()).isZero();
        assertThat(saved.getResendAvailableAt()).isAfter(OffsetDateTime.now());
        verify(mailService).sendVerificationCode("user@example.com", "004821");
    }

    @Test
    void requestRejectsResendDuringCooldownWithoutSendingMail() throws Exception {
        when(codeGenerator.generate()).thenReturn("004821");
        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(emailRequest("user@example.com")))
                .andExpect(status().isOk());

        String encodedCode = verificationRepository.findByEmail("user@example.com").orElseThrow()
                .getEncodedVerificationCode();
        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(emailRequest("user@example.com")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("429VERIFICATION_REQUEST_TOO_FREQUENT"));

        assertThat(verificationRepository.findByEmail("user@example.com").orElseThrow()
                .getEncodedVerificationCode()).isEqualTo(encodedCode);
        verify(mailService).sendVerificationCode("user@example.com", "004821");
    }

    @Test
    void requestRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(emailRequest("invalid-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
    }

    @Test
    void requestRejectsRegisteredEmail() throws Exception {
        userRepository.save(User.create("member@example.com", passwordEncoder.encode("password123!"), "홍길동"));

        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(emailRequest("member@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409DUPLICATE_EMAIL"));
    }

    @Test
    void reissueInvalidatesOldCodeAndResetsVerification() throws Exception {
        EmailVerification existing = EmailVerification.create("user@example.com",
                passwordEncoder.encode("111111"), OffsetDateTime.now().plusMinutes(1),
                OffsetDateTime.now().minusSeconds(1));
        existing.verify(OffsetDateTime.now());
        existing.registerFailedAttempt();
        existing.registerFailedAttempt();
        OffsetDateTime oldExpiresAt = existing.getExpiresAt();
        verificationRepository.save(existing);
        when(codeGenerator.generate()).thenReturn("222222");

        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(emailRequest("user@example.com")))
                .andExpect(status().isOk());

        EmailVerification updated = verificationRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("111111", updated.getEncodedVerificationCode())).isFalse();
        assertThat(passwordEncoder.matches("222222", updated.getEncodedVerificationCode())).isTrue();
        assertThat(updated.getExpiresAt()).isAfter(oldExpiresAt);
        assertThat(updated.isVerified()).isFalse();
        assertThat(updated.getVerifiedAt()).isNull();
        assertThat(updated.getFailedAttempts()).isZero();
        assertThat(updated.getResendAvailableAt()).isAfter(OffsetDateTime.now());

        mockMvc.perform(post("/api/v1/auth/email-verifications/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyRequest("user@example.com", "111111")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_VERIFICATION_CODE"));
    }

    @Test
    void verifyMarksVerificationAsCompleted() throws Exception {
        saveVerification("user@example.com", "381205", OffsetDateTime.now().plusMinutes(5));

        mockMvc.perform(post("/api/v1/auth/email-verifications/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyRequest("user@example.com", "381205")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("이메일 인증이 완료되었습니다."))
                .andExpect(jsonPath("$.data.verified").value(true));

        EmailVerification verified = verificationRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(verified.isVerified()).isTrue();
        assertThat(verified.getVerifiedAt()).isNotNull();
    }

    @Test
    void verifyRejectsWrongCode() throws Exception {
        saveVerification("user@example.com", "381205", OffsetDateTime.now().plusMinutes(5));
        mockMvc.perform(post("/api/v1/auth/email-verifications/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyRequest("user@example.com", "927104")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_VERIFICATION_CODE"));
        assertThat(verificationRepository.findByEmail("user@example.com").orElseThrow()
                .getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void fifthWrongCodeInvalidatesCurrentCodeUntilReissue() throws Exception {
        saveVerification("user@example.com", "381205", OffsetDateTime.now().plusMinutes(5));

        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/email-verifications/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyRequest("user@example.com", "927104")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400INVALID_VERIFICATION_CODE"));
        }
        mockMvc.perform(post("/api/v1/auth/email-verifications/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyRequest("user@example.com", "927104")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400VERIFICATION_ATTEMPTS_EXCEEDED"));

        EmailVerification locked = verificationRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(locked.getFailedAttempts()).isEqualTo(5);
        mockMvc.perform(post("/api/v1/auth/email-verifications/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyRequest("user@example.com", "381205")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400VERIFICATION_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void concurrentWrongCodesIncrementFailedAttemptsWithoutLostUpdate() throws Exception {
        saveVerification("user@example.com", "381205", OffsetDateTime.now().plusMinutes(5));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> verifyWrongCodeAfterSignal(ready, start));
            Future<Boolean> second = executor.submit(() -> verifyWrongCodeAfterSignal(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(verificationRepository.findByEmail("user@example.com").orElseThrow()
                .getFailedAttempts()).isEqualTo(2);
    }

    @Test
    void verifyRejectsExpiredCode() throws Exception {
        saveVerification("user@example.com", "381205", OffsetDateTime.now().minusSeconds(1));
        mockMvc.perform(post("/api/v1/auth/email-verifications/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyRequest("user@example.com", "381205")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400EXPIRED_VERIFICATION_CODE"));
    }

    @Test
    void verifyRejectsMissingRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verifications/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyRequest("missing@example.com", "381205")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404EMAIL_VERIFICATION_NOT_FOUND"));
    }

    @Test
    void verifyRejectsMalformedCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verifications/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyRequest("user@example.com", "12345A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
    }

    private void saveVerification(String email, String code, OffsetDateTime expiresAt) {
        verificationRepository.save(EmailVerification.create(email, passwordEncoder.encode(code), expiresAt,
                OffsetDateTime.now().minusSeconds(1)));
    }

    private boolean verifyWrongCodeAfterSignal(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) return false;
        try {
            emailVerificationService.verify(new AuthRequest.VerifyEmail("user@example.com", "927104"));
            return false;
        } catch (CustomException exception) {
            return exception.getErrorCode() == ErrorCode.INVALID_VERIFICATION_CODE;
        }
    }

    private String emailRequest(String email) {
        return "{\"email\":\"%s\"}".formatted(email);
    }

    private String verifyRequest(String email, String code) {
        return "{\"email\":\"%s\",\"verificationCode\":\"%s\"}".formatted(email, code);
    }
}
