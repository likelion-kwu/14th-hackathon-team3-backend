package com.example.likelionhackathon.domain.user.dto;

public final class UserResponse {

    private UserResponse() {
    }

    public record Signup(Long userId) {
    }
}
