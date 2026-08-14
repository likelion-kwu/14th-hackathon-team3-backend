package com.example.likelionhackathon.global.error;

import com.example.likelionhackathon.global.common.ApiResponse;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void customException은_ErrorCode의_HTTP_상태와_응답_코드를_사용한다() {
        CustomException exception = new CustomException(ErrorCode.WORKSPACE_NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response = handler.handleCustomException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("404WORKSPACE_NOT_FOUND");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void 지원하지_않는_HTTP_메서드는_405_명세_코드로_응답한다() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("PATCH");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotAllowed(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("405METHOD_NOT_ALLOWED");
    }

    @Test
    void 알_수_없는_예외는_500_명세_코드로_응답한다() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(new RuntimeException("test"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("500INTERNAL_SERVER_ERROR");
    }

    @Test
    void 존재하지_않는_리소스는_404_명세_코드로_응답한다() {
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.GET,
                "/missing",
                "No static resource"
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResourceFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("404RESOURCE_NOT_FOUND");
    }
}
