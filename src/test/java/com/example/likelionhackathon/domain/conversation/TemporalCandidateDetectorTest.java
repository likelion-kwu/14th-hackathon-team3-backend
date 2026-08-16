package com.example.likelionhackathon.domain.conversation;

import com.example.likelionhackathon.domain.conversation.service.TemporalCandidateDetector;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TemporalCandidateDetectorTest {
    private final TemporalCandidateDetector detector = new TemporalCandidateDetector();

    @Test void ignoresOrdinaryGreeting() { assertThat(detector.mayContainTemporalExpression("안녕하세요")).isFalse(); }
    @Test void detectsKorean() { assertThat(detector.mayContainTemporalExpression("내일까지 확인해주세요.")).isTrue(); }
    @Test void detectsEnglish() { assertThat(detector.mayContainTemporalExpression("Please review this tomorrow.")).isTrue(); }
    @Test void detectsJapanese() { assertThat(detector.mayContainTemporalExpression("明日までに確認してください。")).isTrue(); }
}
