package com.example.likelionhackathon.domain.user.dto;

import com.example.likelionhackathon.domain.user.entity.UserEnums.ActivityStatus;
import com.example.likelionhackathon.domain.user.entity.UserEnums.UserRegion;

public final class UserResponse {

    private UserResponse() {
    }

    public record Signup(Long userId) {
    }

    public record ActivityStatusResult(ActivityStatus status) {
    }

    public record LanguageResult(String language) {
    }

    public record RegionResult(UserRegion region, String timezone) {
    }
}
