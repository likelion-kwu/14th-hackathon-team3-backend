package com.example.likelionhackathon.domain.handover.dto;

import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ItemCategory;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ReviewStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.TimingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class HandoverRequest {

    private HandoverRequest() {
    }

    public record GenerateDraft(
            @NotNull @Valid SourceRange sourceRange,
            Set<Provider> sourceTypes
    ) {
    }

    public record SourceRange(
            @NotNull OffsetDateTime from,
            @NotNull OffsetDateTime to
    ) {
    }

    public record Refresh(
            Set<Provider> sourceTypes,
            Boolean preserveManualEdits
    ) {
        public boolean shouldPreserveManualEdits() {
            return preserveManualEdits == null || preserveManualEdits;
        }
    }

    public record SaveDraft(
            @NotNull @Valid List<DraftItem> items,
            List<Long> removedItemIds,
            @NotNull @Valid Delivery delivery,
            @NotNull Long version
    ) {
        public List<Long> safeRemovedItemIds() {
            return removedItemIds == null ? List.of() : removedItemIds;
        }
    }

    public record DraftItem(
            Long itemId,
            @NotNull ItemCategory category,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String description,
            Long assigneeMemberId,
            List<Long> evidenceIds,
            @NotNull ReviewStatus reviewStatus
    ) {
        public List<Long> safeEvidenceIds() {
            return evidenceIds == null ? List.of() : evidenceIds;
        }
    }

    public record Delivery(
            @NotNull Long targetTeamId,
            @NotNull Long recipientMemberId,
            @NotNull TimingType timingType,
            OffsetDateTime scheduledAt,
            @NotBlank String timezone
    ) {
    }

    public record Deliver(
            @NotNull Long version,
            @NotNull Boolean acknowledgeReviewAlerts,
            @NotNull UUID deliveryRequestId
    ) {
    }
}
