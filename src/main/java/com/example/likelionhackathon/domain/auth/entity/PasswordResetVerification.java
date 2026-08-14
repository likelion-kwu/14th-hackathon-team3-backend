package com.example.likelionhackathon.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "password_reset_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetVerification {
    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String encodedVerificationCode;
    @Column(nullable = false)
    private OffsetDateTime verificationExpiresAt;
    @Column(nullable = false)
    private boolean verified;
    private OffsetDateTime verifiedAt;
    private String encodedResetToken;
    private OffsetDateTime resetTokenExpiresAt;
    @Column(nullable = false)
    private boolean used;
    @Column(nullable = false)
    private int failedAttempts;
    @Column(nullable = false)
    private OffsetDateTime resendAvailableAt;

    public static PasswordResetVerification create(String email, String encodedCode, OffsetDateTime expiresAt,
                                                   OffsetDateTime resendAvailableAt) {
        PasswordResetVerification verification = new PasswordResetVerification();
        verification.email = email;
        verification.reissue(encodedCode, expiresAt, resendAvailableAt);
        return verification;
    }

    public void reissue(String encodedCode, OffsetDateTime expiresAt, OffsetDateTime resendAvailableAt) {
        encodedVerificationCode = encodedCode;
        verificationExpiresAt = expiresAt;
        this.resendAvailableAt = resendAvailableAt;
        failedAttempts = 0;
        verified = false;
        verifiedAt = null;
        encodedResetToken = null;
        resetTokenExpiresAt = null;
        used = false;
    }

    public boolean canResend(OffsetDateTime now) { return !now.isBefore(resendAvailableAt); }

    public boolean registerFailedAttempt() {
        if (failedAttempts < MAX_FAILED_ATTEMPTS) failedAttempts++;
        return failedAttempts >= MAX_FAILED_ATTEMPTS;
    }

    public boolean hasExceededMaxAttempts() { return failedAttempts >= MAX_FAILED_ATTEMPTS; }

    public boolean isVerificationExpired(OffsetDateTime now) { return now.isAfter(verificationExpiresAt); }

    public void completeVerification(OffsetDateTime now, String encodedToken, OffsetDateTime tokenExpiresAt) {
        verified = true;
        verifiedAt = now;
        encodedResetToken = encodedToken;
        resetTokenExpiresAt = tokenExpiresAt;
    }

    public boolean isResetTokenExpired(OffsetDateTime now) {
        return resetTokenExpiresAt == null || now.isAfter(resetTokenExpiresAt);
    }

    public void markUsed() { used = true; }
}
