package com.example.likelionhackathon.global.error;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void 모든_오류_코드는_HTTP_상태값으로_시작한다() {
        assertThat(Arrays.stream(ErrorCode.values()))
                .allSatisfy(errorCode -> assertThat(errorCode.getCode())
                        .startsWith(String.valueOf(errorCode.getHttpStatus().value())));
    }

    @Test
    void 오류_코드는_중복되지_않는다() {
        long distinctCodeCount = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .distinct()
                .count();

        assertThat(distinctCodeCount).isEqualTo(ErrorCode.values().length);
    }
}
