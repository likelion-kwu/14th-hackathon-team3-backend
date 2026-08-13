package com.example.likelionhackathon.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequest {
    private AuthRequest() {
    }

    public record Login(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "올바른 이메일 형식이어야 합니다.")
            String email,
            @NotBlank(message = "비밀번호는 필수입니다.")
            String password
    ) {
    }

    public record EmailVerificationRequest(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "올바른 이메일 형식이어야 합니다.")
            String email
    ) {
    }

    public record VerifyEmail(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "올바른 이메일 형식이어야 합니다.")
            String email,
            @NotBlank(message = "인증번호는 필수입니다.")
            @Pattern(regexp = "\\d{6}", message = "인증번호는 6자리 숫자여야 합니다.")
            String verificationCode
    ) {
    }
    public record PasswordResetRequest(@NotBlank @Email String email) {}
    public record VerifyPasswordReset(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String verificationCode) {}
    public record ResetPassword(
            @NotBlank @Email String email,
            @NotBlank String resetToken,
            @NotBlank @Size(min = 8) String newPassword,
            @NotBlank String newPasswordConfirm) {}
}
