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

    public static PasswordResetVerification create(String email, String encodedCode, OffsetDateTime expiresAt) {
        PasswordResetVerification verification = new PasswordResetVerification();
        verification.email = email;
        verification.reissue(encodedCode, expiresAt);
        return verification;
    }

    public void reissue(String encodedCode, OffsetDateTime expiresAt) {
        encodedVerificationCode = encodedCode;
        verificationExpiresAt = expiresAt;
        verified = false;
        verifiedAt = null;
        encodedResetToken = null;
        resetTokenExpiresAt = null;
        used = false;
    }

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
