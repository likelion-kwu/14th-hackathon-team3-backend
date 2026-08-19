package com.example.likelionhackathon.domain.handover.service;

import com.example.likelionhackathon.domain.handover.dto.HandoverRequest;
import com.example.likelionhackathon.domain.handover.dto.HandoverResponse;
import com.example.likelionhackathon.domain.handover.entity.DeliverySetting;
import com.example.likelionhackathon.domain.handover.entity.Handover;
import com.example.likelionhackathon.domain.handover.entity.HandoverItem;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.HandoverStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ItemCategory;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ReviewStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.TimingType;
import com.example.likelionhackathon.domain.handover.repository.HandoverRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandoverServiceTest {

    @Mock
    private HandoverRepository handoverRepository;

    @Mock
    private HandoverGenerationService generationService;

    private HandoverService handoverService;

    @BeforeEach
    void setUp() {
        handoverService = new HandoverService(
                handoverRepository,
                generationService
        );
    }

    @Test
    void generateDraftStartsAsyncJob() {
        OffsetDateTime from = OffsetDateTime.parse("2026-08-10T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-08-10T13:00:00Z");
        HandoverRequest.GenerateDraft request = new HandoverRequest.GenerateDraft(
                new HandoverRequest.SourceRange(from, to),
                Set.of(Provider.SLACK)
        );

        when(handoverRepository.existsByProjectIdAndCycleIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(handoverRepository.save(any(Handover.class))).thenAnswer(invocation -> {
            Handover handover = invocation.getArgument(0);
            ReflectionTestUtils.setField(handover, "id", 101L);
            return handover;
        });

        HandoverResponse.GenerationJob response = handoverService.generateDraft(1L, 2L, request);

        assertThat(response.handoverId()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo(HandoverStatus.AI_GENERATING);
        assertThat(response.generationJobId()).startsWith("job_");
        verify(generationService).generate(101L, false);
    }

    @Test
    void generateDraftAllowsEmptySourceTypes() {
        OffsetDateTime from = OffsetDateTime.parse("2026-08-10T00:00:00Z");
        HandoverRequest.GenerateDraft request = new HandoverRequest.GenerateDraft(
                new HandoverRequest.SourceRange(from, from.plusHours(1)),
                Set.of()
        );
        when(handoverRepository.existsByProjectIdAndCycleIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(handoverRepository.save(any(Handover.class))).thenAnswer(invocation -> {
            Handover handover = invocation.getArgument(0);
            ReflectionTestUtils.setField(handover, "id", 102L);
            return handover;
        });

        handoverService.generateDraft(1L, 2L, request);

        verify(generationService).generate(102L, false);
    }

    @Test
    void generateDraftAllowsMissingSourceActivities() {
        OffsetDateTime from = OffsetDateTime.parse("2026-08-10T00:00:00Z");
        HandoverRequest.GenerateDraft request = new HandoverRequest.GenerateDraft(
                new HandoverRequest.SourceRange(from, from.plusHours(1)),
                Set.of(Provider.NOTION)
        );

        when(handoverRepository.existsByProjectIdAndCycleIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(handoverRepository.save(any(Handover.class))).thenAnswer(invocation -> {
            Handover handover = invocation.getArgument(0);
            ReflectionTestUtils.setField(handover, "id", 103L);
            return handover;
        });

        HandoverResponse.GenerationJob response = handoverService.generateDraft(1L, 2L, request);

        assertThat(response.handoverId()).isEqualTo(103L);
        verify(generationService).generate(103L, false);
    }

    @Test
    void saveDraftRejectsStaleVersion() {
        Handover handover = readyHandover(ReviewStatus.VERIFIED);
        ReflectionTestUtils.setField(handover, "version", 4L);
        when(handoverRepository.findOneById(101L)).thenReturn(Optional.of(handover));

        HandoverRequest.SaveDraft request = new HandoverRequest.SaveDraft(
                List.of(),
                List.of(),
                new HandoverRequest.Delivery(
                        42L,
                        23L,
                        TimingType.NEXT_SHIFT_START,
                        null,
                        "Europe/London"
                ),
                3L
        );

        assertThatThrownBy(() -> handoverService.saveDraft(101L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DRAFT_VERSION_CONFLICT);
    }

    @Test
    void deliverRequiresReviewAlertAcknowledgement() {
        Handover handover = readyHandover(ReviewStatus.NEEDS_REVIEW);
        when(handoverRepository.findOneById(101L)).thenReturn(Optional.of(handover));

        HandoverRequest.Deliver request = new HandoverRequest.Deliver(
                3L,
                false,
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> handoverService.deliver(101L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_ALERT_NOT_ACKNOWLEDGED);
    }

    @Test
    void deliverSchedulesReadyHandover() {
        Handover handover = readyHandover(ReviewStatus.NEEDS_REVIEW);
        when(handoverRepository.findOneById(101L)).thenReturn(Optional.of(handover));
        when(handoverRepository.saveAndFlush(handover)).thenReturn(handover);

        HandoverRequest.Deliver request = new HandoverRequest.Deliver(
                3L,
                true,
                UUID.randomUUID()
        );

        HandoverResponse.Delivered response = handoverService.deliver(101L, request);

        assertThat(response.status()).isEqualTo(HandoverStatus.SCHEDULED);
        assertThat(response.deliveryId()).isPositive();
        verify(handoverRepository).saveAndFlush(handover);
    }

    private Handover readyHandover(ReviewStatus reviewStatus) {
        OffsetDateTime now = OffsetDateTime.now();
        Handover handover = Handover.startGeneration(
                1L,
                2L,
                now.minusHours(2),
                now.minusHours(1),
                Set.of(Provider.SLACK)
        );
        HandoverItem item = new HandoverItem(
                ItemCategory.IN_PROGRESS,
                "결제 API 테스트",
                "운영 환경 테스트가 필요합니다.",
                7L,
                reviewStatus,
                false,
                List.of()
        );
        handover.completeGeneration(List.of(item), now);
        handover.applyDeliverySetting(new DeliverySetting(
                42L,
                23L,
                TimingType.NEXT_SHIFT_START,
                "Europe/London",
                now.plusHours(3)
        ));
        ReflectionTestUtils.setField(handover, "id", 101L);
        ReflectionTestUtils.setField(handover, "version", 3L);
        return handover;
    }
}
