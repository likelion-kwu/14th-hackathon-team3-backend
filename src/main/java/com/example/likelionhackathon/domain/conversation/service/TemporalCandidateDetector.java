package com.example.likelionhackathon.domain.conversation.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class TemporalCandidateDetector {
    private static final Pattern CANDIDATE = Pattern.compile(
            "(?iu)(오늘|내일|모레|이번\\s*주|다음\\s*주|월요일|화요일|수요일|목요일|금요일|토요일|일요일|오전|오후|\\d{1,2}\\s*(?:시|분)|\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|까지|부터|today|tomorrow|day\\s+after\\s+tomorrow|next\\s+week|monday|tuesday|wednesday|thursday|friday|saturday|sunday|\\b(?:am|pm|at|by)\\b|\\b(?:\\d{4}-\\d{1,2}-\\d{1,2}|\\d{4}/\\d{1,2}/\\d{1,2}|\\d{4}\\.\\d{1,2}\\.\\d{1,2}|\\d{1,2}/\\d{1,2})\\b|今日|明日|明後日|来週|月曜日|火曜日|水曜日|木曜日|金曜日|土曜日|日曜日|午前|午後|\\d{1,2}\\s*時|\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日|まで)"
    );

    public boolean mayContainTemporalExpression(String content) {
        return content != null && CANDIDATE.matcher(content).find();
    }
}
