package com.example.likelionhackathon.domain.handover.dto;

import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.GenerationStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.HandoverStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ItemCategory;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ReviewStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.TimingType;

import java.time.OffsetDateTime;
import java.util.List;

public final class HandoverResponse {

    private HandoverResponse() {
    }

    public record GenerationJob(
            Long handoverId,
            String generationJobId,
            HandoverStatus status
    ) {
    }

    public record Detail(
            Long handoverId,
            HandoverStatus status,
            OffsetDateTime lastSyncedAt,
            Generation generation,
            ReviewSummary reviewSummary,
            List<Item> items,
            List<ReviewAlert> reviewAlerts,
            Delivery delivery,
            Long version
    ) {
    }

    public record Generation(GenerationStatus status, int progress) {
    }

    public record ReviewSummary(
            long verifiedCount,
            long needsReviewCount,
            long unansweredCount,
            long totalCount
    ) {
    }

    public record Item(
            Long itemId,
            ItemCategory category,
            String title,
            String description,
            Long assigneeMemberId,
            ReviewStatus reviewStatus,
            List<Evidence> evidences
    ) {
    }

    public record Evidence(
            Long evidenceId,
            Provider provider,
            String sourceName,
            String snippet,
            String sourceUrl
    ) {
    }

    public record ReviewAlert(String type, Long itemId, String message) {
    }

    public record Delivery(
            Long targetTeamId,
            Long recipientMemberId,
            TimingType timingType,
            String timezone,
            OffsetDateTime scheduledAt
    ) {
    }

    public record DraftSaved(
            Long handoverId,
            int savedItemCount,
            int removedItemCount,
            OffsetDateTime resolvedScheduledAt,
            Long version
    ) {
    }

    public record Delivered(
            Long handoverId,
            Long deliveryId,
            HandoverStatus status,
            OffsetDateTime scheduledAt,
            String timezone
    ) {
    }
}
