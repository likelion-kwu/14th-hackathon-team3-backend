package com.example.likelionhackathon.domain.auth.controller;

import com.example.likelionhackathon.domain.auth.dto.AuthRequest;
import com.example.likelionhackathon.domain.auth.dto.AuthResponse;
import com.example.likelionhackathon.domain.auth.service.AuthService;
import com.example.likelionhackathon.domain.auth.service.EmailVerificationService;
import com.example.likelionhackathon.domain.auth.service.PasswordResetService;
import com.example.likelionhackathon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ApiResponse<AuthResponse.Login> login(@Valid @RequestBody AuthRequest.Login request) {
        return ApiResponse.success("로그인이 완료되었습니다.", authService.login(request));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success("로그아웃이 완료되었습니다.", null);
    }

    @Operation(summary = "이메일 인증번호 요청")
    @PostMapping("/email-verifications")
    public ApiResponse<Void> requestEmailVerification(@Valid @RequestBody AuthRequest.EmailVerificationRequest request) {
        emailVerificationService.request(request);
        return ApiResponse.success("인증번호가 이메일로 발송되었습니다.", null);
    }

    @Operation(summary = "이메일 인증번호 검증")
    @PostMapping("/email-verifications/verify")
    public ApiResponse<AuthResponse.EmailVerificationResult> verifyEmail(@Valid @RequestBody AuthRequest.VerifyEmail request) {
        return ApiResponse.success("이메일 인증이 완료되었습니다.", emailVerificationService.verify(request));
    }

    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody AuthRequest.PasswordResetRequest request) {
        passwordResetService.request(request);
        return ApiResponse.success("비밀번호 재설정 안내를 확인해주세요.", null);
    }

    @PostMapping("/password-reset/verify")
    public ApiResponse<AuthResponse.PasswordResetVerificationResult> verifyPasswordReset(@Valid @RequestBody AuthRequest.VerifyPasswordReset request) {
        return ApiResponse.success("비밀번호 재설정 인증이 완료되었습니다.", passwordResetService.verify(request));
    }

    @PostMapping("/password-reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody AuthRequest.ResetPassword request) {
        passwordResetService.resetPassword(request);
        return ApiResponse.success("비밀번호가 변경되었습니다.", null);
    }
}
