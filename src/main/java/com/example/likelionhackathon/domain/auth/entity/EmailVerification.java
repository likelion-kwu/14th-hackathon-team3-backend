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

    public static EmailVerification create(String email, String encodedCode, OffsetDateTime expiresAt) {
        EmailVerification verification = new EmailVerification();
        verification.email = email;
        verification.reissue(encodedCode, expiresAt);
        return verification;
    }

    public void reissue(String encodedCode, OffsetDateTime expiresAt) {
        this.encodedVerificationCode = encodedCode;
        this.expiresAt = expiresAt;
        this.verified = false;
        this.verifiedAt = null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return now.isAfter(expiresAt);
    }

    public void verify(OffsetDateTime verifiedAt) {
        this.verified = true;
        this.verifiedAt = verifiedAt;
    }
}
