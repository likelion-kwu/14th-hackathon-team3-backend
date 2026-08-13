package com.example.likelionhackathon.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 사이클 AI 분석처럼 요청 접수만 응답하고 뒤에서 처리하는 작업을 위한 설정.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
