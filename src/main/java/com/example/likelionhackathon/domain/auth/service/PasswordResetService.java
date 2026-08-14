package com.example.likelionhackathon.domain.auth.service;

import com.example.likelionhackathon.domain.auth.dto.AuthRequest;
import com.example.likelionhackathon.domain.auth.dto.AuthResponse;
import com.example.likelionhackathon.domain.auth.entity.PasswordResetVerification;
import com.example.likelionhackathon.domain.auth.repository.PasswordResetVerificationRepository;
import com.example.likelionhackathon.domain.user.entity.User;
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
public class PasswordResetService {
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private final PasswordResetVerificationRepository repository;
    private final UserRepository userRepository;
    private final EmailVerificationCodeGenerator codeGenerator;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final PasswordResetMailService mailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void request(AuthRequest.PasswordResetRequest request) {
        if (!userRepository.existsByEmail(request.email())) return;
        OffsetDateTime now = OffsetDateTime.now();
        PasswordResetVerification existing = repository.findByEmail(request.email()).orElse(null);
        if (existing != null && !existing.canResend(now)) return;

        String code = codeGenerator.generate();
        String encodedCode = passwordEncoder.encode(code);
        OffsetDateTime expiresAt = now.plusMinutes(5);
        OffsetDateTime resendAvailableAt = now.plusSeconds(RESEND_COOLDOWN_SECONDS);
        PasswordResetVerification verification;
        if (existing == null) {
            verification = PasswordResetVerification.create(request.email(), encodedCode, expiresAt, resendAvailableAt);
        } else {
            existing.reissue(encodedCode, expiresAt, resendAvailableAt);
            verification = existing;
        }
        repository.save(verification);
        mailService.sendVerificationCode(request.email(), code);
    }

    @Transactional(noRollbackFor = CustomException.class)
    public AuthResponse.PasswordResetVerificationResult verify(AuthRequest.VerifyPasswordReset request) {
        PasswordResetVerification verification = repository.findByEmailForUpdate(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_NOT_VERIFIED));
        OffsetDateTime now = OffsetDateTime.now();
        if (verification.isUsed() || verification.isVerificationExpired(now))
            throw new CustomException(ErrorCode.EXPIRED_VERIFICATION_CODE);
        if (verification.hasExceededMaxAttempts())
            throw new CustomException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        if (!passwordEncoder.matches(request.verificationCode(), verification.getEncodedVerificationCode())) {
            if (verification.registerFailedAttempt())
                throw new CustomException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        String token = tokenGenerator.generate();
        verification.completeVerification(now, passwordEncoder.encode(token), now.plusMinutes(10));
        return new AuthResponse.PasswordResetVerificationResult(token);
    }

    @Transactional
    public void resetPassword(AuthRequest.ResetPassword request) {
        if (!request.newPassword().equals(request.newPasswordConfirm()))
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        PasswordResetVerification verification = find(request.email());
        OffsetDateTime now = OffsetDateTime.now();
        if (!verification.isVerified() || verification.isUsed() || verification.getEncodedResetToken() == null)
            throw new CustomException(ErrorCode.PASSWORD_RESET_NOT_VERIFIED);
        if (verification.isResetTokenExpired(now))
            throw new CustomException(ErrorCode.EXPIRED_PASSWORD_RESET_TOKEN);
        if (!passwordEncoder.matches(request.resetToken(), verification.getEncodedResetToken()))
            throw new CustomException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_NOT_VERIFIED));
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        verification.markUsed();
    }

    private PasswordResetVerification find(String email) {
        return repository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_NOT_VERIFIED));
    }
}
