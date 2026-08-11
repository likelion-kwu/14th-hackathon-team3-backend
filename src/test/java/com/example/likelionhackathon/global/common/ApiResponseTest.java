package com.example.likelionhackathon.global.common;

import com.example.likelionhackathon.global.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void success_기본_응답은_200OK를_사용한다() {
        ApiResponse<Map<String, Long>> response = ApiResponse.success(Map.of("workspaceId", 1L));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("200OK");
        assertThat(response.getMessage()).isEqualTo("요청이 성공적으로 처리되었습니다.");
        assertThat(response.getData()).containsEntry("workspaceId", 1L);
    }

    @Test
    void created와_accepted는_명세의_성공_코드를_사용한다() {
        ApiResponse<Long> created = ApiResponse.created("워크스페이스를 생성했습니다.", 1L);
        ApiResponse<Long> accepted = ApiResponse.accepted("인수인계 생성을 시작했습니다.", 101L);

        assertThat(created.getCode()).isEqualTo("201CREATED");
        assertThat(accepted.getCode()).isEqualTo("202ACCEPTED");
    }

    @Test
    void 응답_JSON은_명세의_네_필드만_포함한다() throws Exception {
        ApiResponse<Long> response = ApiResponse.success(1L);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.size()).isEqualTo(4);
        assertThat(json.has("success")).isTrue();
        assertThat(json.has("code")).isTrue();
        assertThat(json.has("message")).isTrue();
        assertThat(json.has("data")).isTrue();
        assertThat(json.has("timestamp")).isFalse();
    }

    @Test
    void error_응답은_명세_코드와_null_데이터를_사용한다() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.HANDOVER_NOT_FOUND);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("404HANDOVER_NOT_FOUND");
        assertThat(response.getMessage()).isEqualTo("인수인계를 찾을 수 없습니다.");
        assertThat(response.getData()).isNull();
    }

    @Test
    void success에는_2xx가_아닌_HTTP_상태를_사용할_수_없다() {
        assertThatThrownBy(() -> ApiResponse.success(HttpStatus.BAD_REQUEST, "실패", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("성공 응답에는 2xx HTTP 상태만 사용할 수 있습니다.");
    }
}
