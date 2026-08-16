package com.example.likelionhackathon.domain.conversation.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

public final class TemporalModels {
    private TemporalModels() {}

    public enum Type { RELATIVE_DATE, RELATIVE_DATE_TIME, WEEKDAY, EXPLICIT_DATE, EXPLICIT_DATE_TIME }
    public enum RelativeDateType { TODAY, TOMORROW, DAY_AFTER_TOMORROW, NEXT_WEEK, NEXT_DAY_OF_WEEK, NONE }
    public enum Role { DEADLINE, EVENT_TIME, REFERENCE }

    public record TemporalExtraction(boolean hasTemporalExpression, List<TemporalExpression> expressions) {}
    public record TemporalExpression(String originalText, Type type, RelativeDateType relativeDateType,
                                     String dayOfWeek, String explicitDate, String localTime,
                                     boolean hasExplicitTime, Role role) {}
    public record ResolvedTemporalContext(List<ResolvedTemporalExpression> expressions) {}
    public record ResolvedTemporalExpression(String originalText, Role role, LocalDate senderDate,
                                             ZonedDateTime senderDateTime, String senderZoneId,
                                             ZonedDateTime receiverDateTime, String receiverZoneId,
                                             boolean hasExplicitTime) {}
}
