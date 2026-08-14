package com.example.likelionhackathon.global.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({"/", "/health"})
    public ApiResponse<ServerStatus> health() {
        return ApiResponse.success("서버가 정상적으로 실행 중입니다.", new ServerStatus("UP"));
    }

    public record ServerStatus(String status) {
    }
}
