package com.example.likelionhackathon.global.common;

import com.example.likelionhackathon.global.error.ErrorCode;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;

    private ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    // 성공 응답 (데이터 있음)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "요청에 성공하였습니다.", data);
    }

    // 성공 응답 (데이터 없음/메시지 직접 입력)
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "OK", message, data);
    }

    // 예외/실패 응답 (ErrorCode 기반)
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    // 예외/실패 응답 (커스텀 메시지 override)
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String customMessage) {
        return new ApiResponse<>(false, errorCode.getCode(), customMessage, null);
    }
}
