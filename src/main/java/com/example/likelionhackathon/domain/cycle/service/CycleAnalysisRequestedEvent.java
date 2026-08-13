package com.example.likelionhackathon.domain.cycle.service;

/**
 * AI 분석 재실행 요청이 커밋된 뒤 {@link CycleAnalysisRunner} 를 깨우는 이벤트.
 */
public record CycleAnalysisRequestedEvent(Long analysisId) {
}
