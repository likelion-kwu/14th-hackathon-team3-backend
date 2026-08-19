package com.example.likelionhackathon.domain.handover.service;

import com.example.likelionhackathon.domain.handover.entity.Handover;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.GenerationStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.HandoverStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
import com.example.likelionhackathon.domain.handover.repository.CollaborationActivityRepository;
import com.example.likelionhackathon.domain.handover.repository.HandoverRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandoverGenerationServiceTest {

    @Mock
    private HandoverRepository handoverRepository;

    @Mock
    private CollaborationActivityRepository activityRepository;

    @Mock
    private OpenAiHandoverClient openAiHandoverClient;

    @Test
    void generateCompletesEmptyDraftWhenNoActivityExists() {
        Handover handover = Handover.startGeneration(
                1L,
                2L,
                OffsetDateTime.parse("2026-08-10T00:00:00Z"),
                OffsetDateTime.parse("2026-08-10T01:00:00Z"),
                Set.of(Provider.SLACK)
        );
        ReflectionTestUtils.setField(handover, "id", 101L);
        when(handoverRepository.findOneById(101L)).thenReturn(Optional.of(handover));
        when(activityRepository
                .findByProjectIdAndCycleIdAndOccurredAtBetweenAndProviderInOrderByOccurredAtAsc(
                        any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        HandoverGenerationService generationService = new HandoverGenerationService(
                handoverRepository,
                activityRepository,
                openAiHandoverClient
        );

        generationService.generate(101L, false);

        assertThat(handover.getGenerationStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.READY);
        assertThat(handover.getItems()).isEmpty();
        verifyNoInteractions(openAiHandoverClient);
    }
}
