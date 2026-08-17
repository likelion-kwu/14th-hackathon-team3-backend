package com.example.likelionhackathon.domain.conversation;

import com.example.likelionhackathon.domain.conversation.service.TemporalModels.*;
import com.example.likelionhackathon.domain.conversation.service.TemporalResolver;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TemporalResolverTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T03:00:00Z"), ZoneOffset.UTC);
    private final TemporalResolver resolver = new TemporalResolver(CLOCK);

    @Test void resolvesTomorrowFromSenderDateWithoutInventingTime() {
        var result = resolve(expression(false, null), "Asia/Seoul", "America/Los_Angeles");
        assertThat(result.senderDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(result.senderDateTime()).isNull();
        assertThat(result.receiverDateTime()).isNull();
    }

    @Test void convertsSeoulToLosAngeles() {
        var result = resolve(expression(true, "15:00"), "Asia/Seoul", "America/Los_Angeles");
        assertThat(result.senderDateTime().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 8, 17, 15, 0));
        assertThat(result.receiverDateTime().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 8, 16, 23, 0));
    }

    @Test void convertsLosAngelesToSeoul() {
        var result = resolve(expression(true, "15:00"), "America/Los_Angeles", "Asia/Seoul");
        assertThat(result.receiverDateTime().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 0));
    }

    @Test void javaTimeAppliesLosAngelesDst() {
        TemporalResolver dstResolver = new TemporalResolver(Clock.fixed(Instant.parse("2026-03-07T12:00:00Z"), ZoneOffset.UTC));
        var resolved = dstResolver.resolve(List.of(expression(true, "15:00")),
                ZoneId.of("America/Los_Angeles"), ZoneId.of("Asia/Seoul")).expressions().getFirst();
        assertThat(resolved.senderDateTime().getOffset()).isEqualTo(ZoneOffset.ofHours(-7));
    }

    @Test void javaTimeAppliesNewYorkDst() {
        TemporalResolver dstResolver = new TemporalResolver(Clock.fixed(Instant.parse("2026-03-07T12:00:00Z"), ZoneOffset.UTC));
        var resolved = dstResolver.resolve(List.of(expression(true, "15:00")),
                ZoneId.of("America/New_York"), ZoneId.of("Asia/Tokyo")).expressions().getFirst();
        assertThat(resolved.senderDateTime().getOffset()).isEqualTo(ZoneOffset.ofHours(-4));
    }

    @Test void resolvesNextWeekWeekdaysWithinMondayToSundayWeek() {
        TemporalResolver wednesdayResolver = new TemporalResolver(
                Clock.fixed(Instant.parse("2026-08-19T03:00:00Z"), ZoneOffset.UTC));
        assertThat(resolveNextWeek(wednesdayResolver, "FRIDAY")).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(resolveNextWeek(wednesdayResolver, "MONDAY")).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(resolveNextWeek(wednesdayResolver, "SUNDAY")).isEqualTo(LocalDate.of(2026, 8, 30));
    }

    @Test void resolvesExplicitDateWithoutChangingIt() {
        TemporalExpression expression = new TemporalExpression("2026-08-20", Type.EXPLICIT_DATE,
                RelativeDateType.NONE, null, "2026-08-20", null, false, Role.EVENT_TIME);
        assertThat(resolve(expression, "Asia/Seoul", "America/Los_Angeles").senderDate())
                .isEqualTo(LocalDate.of(2026, 8, 20));
    }

    private LocalDate resolveNextWeek(TemporalResolver target, String dayOfWeek) {
        TemporalExpression expression = new TemporalExpression("다음 주", Type.WEEKDAY,
                RelativeDateType.NEXT_WEEK, dayOfWeek, null, null, false, Role.EVENT_TIME);
        return target.resolve(List.of(expression), ZoneId.of("Asia/Seoul"), ZoneId.of("Asia/Seoul"))
                .expressions().getFirst().senderDate();
    }

    private ResolvedTemporalExpression resolve(TemporalExpression expression, String sender, String receiver) {
        return resolver.resolve(List.of(expression), ZoneId.of(sender), ZoneId.of(receiver)).expressions().getFirst();
    }

    private TemporalExpression expression(boolean explicitTime, String time) {
        return new TemporalExpression("내일", explicitTime ? Type.RELATIVE_DATE_TIME : Type.RELATIVE_DATE,
                RelativeDateType.TOMORROW, null, null, time, explicitTime, Role.DEADLINE);
    }
}
