package com.example.likelionhackathon.global.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    private final HealthController healthController = new HealthController();

    @Test
    void 서버_상태를_UP으로_응답한다() {
        ApiResponse<HealthController.ServerStatus> response = healthController.health();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("200OK");
        assertThat(response.getData().status()).isEqualTo("UP");
    }
}
