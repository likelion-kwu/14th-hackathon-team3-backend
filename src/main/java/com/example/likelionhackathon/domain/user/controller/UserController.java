package com.example.likelionhackathon.domain.user.controller;

import com.example.likelionhackathon.domain.user.dto.UserRequest;
import com.example.likelionhackathon.domain.user.dto.UserResponse;
import com.example.likelionhackathon.domain.user.service.UserService;
import com.example.likelionhackathon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse.Signup>> signup(
            @Valid @RequestBody UserRequest.Signup request
    ) {
        UserResponse.Signup response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("회원가입이 완료되었습니다.", response));
    }

    @Operation(summary = "활동 상태 조회")
    @GetMapping("/me/activity-status")
    public ApiResponse<UserResponse.ActivityStatusResult> getActivityStatus() {
        return ApiResponse.success("활동 상태를 조회했습니다.", userService.getActivityStatus());
    }

    @Operation(summary = "활동 상태 변경")
    @PatchMapping("/me/activity-status")
    public ApiResponse<UserResponse.ActivityStatusResult> updateActivityStatus(
            @Valid @RequestBody UserRequest.UpdateActivityStatus request
    ) {
        return ApiResponse.success("활동 상태가 변경되었습니다.", userService.updateActivityStatus(request));
    }

    @Operation(summary = "기본 언어 조회")
    @GetMapping("/me/language")
    public ApiResponse<UserResponse.LanguageResult> getMyLanguage() {
        return ApiResponse.success("기본 언어를 조회했습니다.", userService.getMyLanguage());
    }

    @Operation(summary = "기본 언어 변경")
    @PatchMapping("/me/language")
    public ApiResponse<UserResponse.LanguageResult> changeMyLanguage(
            @Valid @RequestBody UserRequest.UpdateLanguage request
    ) {
        return ApiResponse.success("기본 언어가 변경되었습니다.", userService.changeMyLanguage(request));
    }


    @Operation(summary = "업무 지역 조회")
    @GetMapping("/me/region")
    public ApiResponse<UserResponse.RegionResult> getMyRegion() {
        return ApiResponse.success("업무 지역을 조회했습니다.", userService.getMyRegion());
    }

    @Operation(summary = "업무 지역 변경")
    @PatchMapping("/me/region")
    public ApiResponse<UserResponse.RegionResult> changeMyRegion(
            @Valid @RequestBody UserRequest.UpdateRegion request) {
        return ApiResponse.success("업무 지역이 변경되었습니다.", userService.changeMyRegion(request));
    }
}
