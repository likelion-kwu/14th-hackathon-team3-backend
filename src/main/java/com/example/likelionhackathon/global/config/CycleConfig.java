package com.example.likelionhackathon.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 사이클 설정값을 읽고, 기간이 지난 사이클을 따라잡는 스케줄러를 켠다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CycleProperties.class)
public class CycleConfig {
}
