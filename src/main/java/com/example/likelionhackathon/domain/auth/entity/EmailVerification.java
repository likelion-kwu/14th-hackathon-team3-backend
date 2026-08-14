package com.example.likelionhackathon.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "email_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {
    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String encodedVerificationCode;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private boolean verified;

    private OffsetDateTime verifiedAt;

    @Column(nullable = false)
    private int failedAttempts;

    @Column(nullable = false)
    private OffsetDateTime resendAvailableAt;

    public static EmailVerification create(String email, String encodedCode, OffsetDateTime expiresAt,
                                           OffsetDateTime resendAvailableAt) {
        EmailVerification verification = new EmailVerification();
        verification.email = email;
        verification.reissue(encodedCode, expiresAt, resendAvailableAt);
        return verification;
    }

    public void reissue(String encodedCode, OffsetDateTime expiresAt, OffsetDateTime resendAvailableAt) {
        this.encodedVerificationCode = encodedCode;
        this.expiresAt = expiresAt;
        this.resendAvailableAt = resendAvailableAt;
        this.failedAttempts = 0;
        this.verified = false;
        this.verifiedAt = null;
    }

    public boolean canResend(OffsetDateTime now) {
        return !now.isBefore(resendAvailableAt);
    }

    public boolean registerFailedAttempt() {
        if (failedAttempts < MAX_FAILED_ATTEMPTS) {
            failedAttempts++;
        }
        return failedAttempts >= MAX_FAILED_ATTEMPTS;
    }

    public boolean hasExceededMaxAttempts() {
        return failedAttempts >= MAX_FAILED_ATTEMPTS;
    }

    public boolean isExpired(OffsetDateTime now) {
        return now.isAfter(expiresAt);
    }

    public void verify(OffsetDateTime verifiedAt) {
        this.verified = true;
        this.verifiedAt = verifiedAt;
    }

    public boolean isSignupAvailable(OffsetDateTime now, long validMinutes) {
        return verified && verifiedAt != null && !now.isAfter(verifiedAt.plusMinutes(validMinutes));
    }
}
