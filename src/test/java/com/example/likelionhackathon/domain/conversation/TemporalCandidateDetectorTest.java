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
    @Test void detectsExplicitDates() {
        assertThat(detector.mayContainTemporalExpression("회의는 2026-08-20에 있습니다.")).isTrue();
        assertThat(detector.mayContainTemporalExpression("회의는 2026/08/20에 있습니다.")).isTrue();
        assertThat(detector.mayContainTemporalExpression("회의는 2026.08.20에 있습니다.")).isTrue();
        assertThat(detector.mayContainTemporalExpression("Meeting date: 8/20.")).isTrue();
        assertThat(detector.mayContainTemporalExpression("회의는 8월 20일입니다.")).isTrue();
        assertThat(detector.mayContainTemporalExpression("会議は8月20日です。")).isTrue();
    }
    @Test void ignoresOrdinaryEnglishSentences() {
        assertThat(detector.mayContainTemporalExpression("We discussed the database.")).isFalse();
        assertThat(detector.mayContainTemporalExpression("This is a binary file.")).isFalse();
    }
    @Test void ignoresOrdinaryJapaneseSentence() {
        assertThat(detector.mayContainTemporalExpression("よろしくお願いします。")).isFalse();
    }
}
