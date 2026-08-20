package com.example.likelionhackathon.domain.handover.service;

import com.example.likelionhackathon.domain.handover.entity.Handover;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.GenerationStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.HandoverStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
import com.example.likelionhackathon.domain.handover.repository.HandoverRepository;
import com.example.likelionhackathon.domain.handover.dto.OpenAiHandoverResult;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ItemCategory;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ReviewStatus;
import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.repository.IssueRepository;
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
    private IssueRepository issueRepository;

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
        when(issueRepository.findByCycleIdOrderByDueDateAscIdAsc(2L)).thenReturn(List.of());

        HandoverGenerationService generationService = new HandoverGenerationService(
                handoverRepository,
                issueRepository,
                openAiHandoverClient
        );

        generationService.generate(101L, false);

        assertThat(handover.getGenerationStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.READY);
        assertThat(handover.getItems()).isEmpty();
        verifyNoInteractions(openAiHandoverClient);
    }

    @Test
    void generateUsesCycleIssuesAsAiEvidence() {
        Handover handover = Handover.startGeneration(
                1L, 2L, OffsetDateTime.now().minusDays(1), OffsetDateTime.now(), Set.of(Provider.CYCLE)
        );
        ReflectionTestUtils.setField(handover, "id", 102L);
        Issue issue = Issue.create(
                2L, "결제 API 연동", "결제 API 응답 형식을 맞춥니다.",
                IssuePriority.HIGH, 77L, java.time.LocalDate.of(2026, 8, 20)
        );
        ReflectionTestUtils.setField(issue, "id", 501L);
        when(handoverRepository.findOneById(102L)).thenReturn(Optional.of(handover));
        when(issueRepository.findByCycleIdOrderByDueDateAscIdAsc(2L)).thenReturn(List.of(issue));
        when(openAiHandoverClient.generate(any())).thenReturn(new OpenAiHandoverResult(List.of(
                new OpenAiHandoverResult.GeneratedItem(
                        ItemCategory.IN_PROGRESS,
                        "결제 API 연동 진행",
                        "응답 형식을 맞추는 중입니다.",
                        77L,
                        ReviewStatus.VERIFIED,
                        List.of(0)
                )
        )));

        new HandoverGenerationService(handoverRepository, issueRepository, openAiHandoverClient)
                .generate(102L, false);

        assertThat(handover.getItems()).singleElement()
                .extracting("title")
                .isEqualTo("결제 API 연동 진행");
        assertThat(handover.getItems().getFirst().getEvidences()).singleElement()
                .extracting("provider", "sourceName")
                .containsExactly(Provider.CYCLE, "이슈 #501: 결제 API 연동");
    }
}
