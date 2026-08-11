package com.example.likelionhackathon.global.common;

import com.example.likelionhackathon.global.error.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiResponse<T> {

    private static final String DEFAULT_SUCCESS_MESSAGE = "요청이 성공적으로 처리되었습니다.";

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    private ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 200 OK 성공 응답
    public static <T> ApiResponse<T> success(T data) {
        return success(HttpStatus.OK, DEFAULT_SUCCESS_MESSAGE, data);
    }

    // 200 OK 성공 응답 (메시지 직접 입력)
    public static <T> ApiResponse<T> success(String message, T data) {
        return success(HttpStatus.OK, message, data);
    }

    // HTTP 상태를 직접 지정하는 성공 응답
    public static <T> ApiResponse<T> success(HttpStatus httpStatus, String message, T data) {
        if (!httpStatus.is2xxSuccessful()) {
            throw new IllegalArgumentException("성공 응답에는 2xx HTTP 상태만 사용할 수 있습니다.");
        }

        return new ApiResponse<>(true, toResponseCode(httpStatus), message, data);
    }

    // 201 Created 성공 응답
    public static <T> ApiResponse<T> created(String message, T data) {
        return success(HttpStatus.CREATED, message, data);
    }

    // 202 Accepted 성공 응답
    public static <T> ApiResponse<T> accepted(String message, T data) {
        return success(HttpStatus.ACCEPTED, message, data);
    }

    // 예외/실패 응답 (ErrorCode 기반)
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    // 예외/실패 응답 (커스텀 메시지 override)
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String customMessage) {
        return new ApiResponse<>(false, errorCode.getCode(), customMessage, null);
    }

    private static String toResponseCode(HttpStatus httpStatus) {
        return httpStatus.value() + httpStatus.name().replace("_", "");
    }
}
