package com.example.likelionhackathon.domain.auth.service;

import com.example.likelionhackathon.domain.auth.dto.AuthRequest;
import com.example.likelionhackathon.domain.auth.dto.AuthResponse;
import com.example.likelionhackathon.domain.auth.entity.EmailVerification;
import com.example.likelionhackathon.domain.auth.repository.EmailVerificationRepository;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final long EXPIRATION_MINUTES = 5;
    private static final long RESEND_COOLDOWN_SECONDS = 60;

    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final EmailVerificationCodeGenerator codeGenerator;
    private final EmailVerificationMailService mailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void request(AuthRequest.EmailVerificationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        OffsetDateTime now = OffsetDateTime.now();
        EmailVerification existing = verificationRepository.findByEmail(request.email()).orElse(null);
        if (existing != null && !existing.canResend(now)) {
            throw new CustomException(ErrorCode.VERIFICATION_REQUEST_TOO_FREQUENT);
        }

        String code = codeGenerator.generate();
        String encodedCode = passwordEncoder.encode(code);
        OffsetDateTime expiresAt = now.plusMinutes(EXPIRATION_MINUTES);
        OffsetDateTime resendAvailableAt = now.plusSeconds(RESEND_COOLDOWN_SECONDS);
        EmailVerification verification;
        if (existing == null) {
            verification = EmailVerification.create(request.email(), encodedCode, expiresAt, resendAvailableAt);
        } else {
            existing.reissue(encodedCode, expiresAt, resendAvailableAt);
            verification = existing;
        }

        verificationRepository.save(verification);
        mailService.sendVerificationCode(request.email(), code);
    }

    @Transactional(noRollbackFor = CustomException.class)
    public AuthResponse.EmailVerificationResult verify(AuthRequest.VerifyEmail request) {
        EmailVerification verification = verificationRepository.findByEmailForUpdate(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));
        OffsetDateTime now = OffsetDateTime.now();

        if (verification.isExpired(now)) {
            throw new CustomException(ErrorCode.EXPIRED_VERIFICATION_CODE);
        }
        if (verification.hasExceededMaxAttempts()) {
            throw new CustomException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (!passwordEncoder.matches(request.verificationCode(), verification.getEncodedVerificationCode())) {
            if (verification.registerFailedAttempt()) {
                throw new CustomException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
            }
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        verification.verify(now);
        return new AuthResponse.EmailVerificationResult(true);
    }
}
