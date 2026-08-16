package com.example.likelionhackathon.domain.conversation.service;

import com.example.likelionhackathon.domain.conversation.service.TemporalModels.*;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Component
public class TemporalResolver {
    private final Clock clock;

    public TemporalResolver(Clock clock) { this.clock = clock; }

    public ResolvedTemporalContext resolve(List<TemporalExpression> expressions, ZoneId senderZone, ZoneId receiverZone) {
        LocalDate today = Instant.now(clock).atZone(senderZone).toLocalDate();
        return new ResolvedTemporalContext(expressions.stream()
                .map(expression -> resolveOne(expression, today, senderZone, receiverZone)).toList());
    }

    private ResolvedTemporalExpression resolveOne(TemporalExpression expression, LocalDate today,
                                                   ZoneId senderZone, ZoneId receiverZone) {
        LocalDate date = resolveDate(expression, today);
        ZonedDateTime senderDateTime = null;
        ZonedDateTime receiverDateTime = null;
        if (expression.hasExplicitTime()) {
            if (expression.localTime() == null) throw new IllegalArgumentException("Explicit time is missing");
            LocalTime time = LocalTime.parse(expression.localTime());
            senderDateTime = ZonedDateTime.of(date, time, senderZone);
            receiverDateTime = senderDateTime.withZoneSameInstant(receiverZone);
        }
        return new ResolvedTemporalExpression(expression.originalText(), expression.role(), date,
                senderDateTime, senderZone.getId(), receiverDateTime, receiverZone.getId(),
                expression.hasExplicitTime());
    }

    private LocalDate resolveDate(TemporalExpression expression, LocalDate today) {
        RelativeDateType relative = expression.relativeDateType() == null ? RelativeDateType.NONE : expression.relativeDateType();
        return switch (relative) {
            case TODAY -> today;
            case TOMORROW -> today.plusDays(1);
            case DAY_AFTER_TOMORROW -> today.plusDays(2);
            case NEXT_WEEK -> expression.dayOfWeek() == null ? today.plusWeeks(1)
                    : today.plusWeeks(1)
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            .with(TemporalAdjusters.nextOrSame(parseDay(expression.dayOfWeek())));
            case NEXT_DAY_OF_WEEK -> today.with(TemporalAdjusters.next(parseDay(expression.dayOfWeek())));
            case NONE -> explicitDate(expression);
        };
    }

    private LocalDate explicitDate(TemporalExpression expression) {
        String value = expression.explicitDate();
        if (value == null) {
            throw new IllegalArgumentException("Resolved expression has no valid date");
        }
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Resolved expression has no valid date", exception);
        }
    }

    private DayOfWeek parseDay(String value) {
        if (value == null) throw new IllegalArgumentException("Day of week is missing");
        return DayOfWeek.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }
}
