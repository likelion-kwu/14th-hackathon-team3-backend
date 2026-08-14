package com.example.likelionhackathon.domain.auth.dto;

public final class AuthResponse {
    private AuthResponse() {
    }

    public record Login(Long userId, String accessToken) {
    }

    public record EmailVerificationResult(boolean verified) {
    }
    public record PasswordResetVerificationResult(String resetToken) {}
}
