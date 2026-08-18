package com.example.likelionhackathon.global.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 프로젝트 생성 시 자동으로 깔아 두는 사이클의 길이 설정.
 *
 * <p>명세에 사이클 주기가 없어서 디자인 목업(2주 안팎)을 기본값으로 잡았다.
 * 팀마다 리듬이 달라 바꿀 여지를 남긴다.</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "cycle")
public class CycleProperties {

    /** 사이클 하나의 길이. 이 값이 0 이하면 기간을 나눌 수 없어 뜨는 시점에 막는다. */
    @Min(1)
    private int initialLengthDays = 14;

    /**
     * 마지막에 남는 자투리가 이 일수보다 짧으면 직전 사이클에 붙인다.
     * 0 으로 두면 3일짜리 사이클도 그대로 만든다.
     */
    @Min(0)
    private int minTailDays = 7;
}
