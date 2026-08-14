package com.example.likelionhackathon.domain.user.dto;

import com.example.likelionhackathon.global.validation.Utf8ByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserRequest {
    private UserRequest() {
    }

    public record Signup(
            @NotBlank(message = "이름은 필수입니다.")
            String name,
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "올바른 이메일 형식이어야 합니다.")
            String email,
            @NotBlank(message = "비밀번호는 필수입니다.")
            @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
            @Utf8ByteLength(max = 72, message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
            String password,
            @NotBlank(message = "비밀번호 확인은 필수입니다.")
            String passwordConfirm
    ) {
    }
}
