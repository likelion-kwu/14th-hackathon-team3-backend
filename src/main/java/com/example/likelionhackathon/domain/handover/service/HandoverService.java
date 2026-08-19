package com.example.likelionhackathon.domain.handover.service;

import com.example.likelionhackathon.domain.handover.dto.HandoverRequest;
import com.example.likelionhackathon.domain.handover.dto.HandoverResponse;
import com.example.likelionhackathon.domain.handover.entity.DeliverySetting;
import com.example.likelionhackathon.domain.handover.entity.Handover;
import com.example.likelionhackathon.domain.handover.entity.HandoverEvidence;
import com.example.likelionhackathon.domain.handover.entity.HandoverItem;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.GenerationStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.HandoverStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ReviewStatus;
import com.example.likelionhackathon.domain.handover.repository.HandoverRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HandoverService {

    private static final Set<HandoverStatus> GENERATING_STATUSES = Set.of(HandoverStatus.AI_GENERATING);

    private final HandoverRepository handoverRepository;
    private final HandoverGenerationService generationService;

    public HandoverResponse.GenerationJob generateDraft(
            Long projectId,
            Long cycleId,
            HandoverRequest.GenerateDraft request
    ) {
        validateSourceRange(request.sourceRange());
        if (handoverRepository.existsByProjectIdAndCycleIdAndStatusIn(
                projectId, cycleId, GENERATING_STATUSES)) {
            throw new CustomException(ErrorCode.HANDOVER_ALREADY_GENERATING);
        }

        Set<Provider> sourceTypes = sourceTypesOrAll(request.sourceTypes());
        Handover handover = Handover.startGeneration(
                projectId,
                cycleId,
                request.sourceRange().from(),
                request.sourceRange().to(),
                sourceTypes
        );
        Handover saved = handoverRepository.save(handover);
        generationService.generate(saved.getId(), false);
        return toGenerationJob(saved);
    }

    @Transactional(readOnly = true)
    public HandoverResponse.Detail getDetail(Long handoverId) {
        return toDetail(findHandover(handoverId));
    }

    public HandoverResponse.GenerationJob refresh(Long handoverId, HandoverRequest.Refresh request) {
        Handover handover = findHandover(handoverId);
        if (handover.isLocked()) {
            throw new CustomException(ErrorCode.HANDOVER_LOCKED);
        }
        if (handover.getGenerationStatus() == GenerationStatus.PENDING
                || handover.getGenerationStatus() == GenerationStatus.RUNNING) {
            throw new CustomException(ErrorCode.REFRESH_ALREADY_RUNNING);
        }

        Set<Provider> providers = request != null
                ? sourceTypesOrDefault(request.sourceTypes(), handover.getSourceTypes())
                : sourceTypesOrAll(handover.getSourceTypes());

        handover.startRefresh(providers);
        Handover saved = handoverRepository.save(handover);
        boolean preserveManualEdits = request == null || request.shouldPreserveManualEdits();
        generationService.generate(saved.getId(), preserveManualEdits);
        return toGenerationJob(saved);
    }

    private Set<Provider> sourceTypesOrAll(Set<Provider> sourceTypes) {
        return sourceTypes == null || sourceTypes.isEmpty()
                ? Set.of(Provider.values())
                : sourceTypes;
    }

    private Set<Provider> sourceTypesOrDefault(
            Set<Provider> requestedSourceTypes,
            Set<Provider> previousSourceTypes
    ) {
        if (requestedSourceTypes != null && !requestedSourceTypes.isEmpty()) {
            return requestedSourceTypes;
        }
        return sourceTypesOrAll(previousSourceTypes);
    }

    @Transactional
    public HandoverResponse.DraftSaved saveDraft(Long handoverId, HandoverRequest.SaveDraft request) {
        Handover handover = findHandover(handoverId);
        validateEditable(handover);
        validateVersion(request.version(), handover.getVersion(), ErrorCode.DRAFT_VERSION_CONFLICT);

        Map<Long, HandoverItem> existingItems = new LinkedHashMap<>();
        Map<Long, HandoverEvidence> existingEvidences = new HashMap<>();
        for (HandoverItem item : handover.getItems()) {
            existingItems.put(item.getId(), item);
            for (HandoverEvidence evidence : item.getEvidences()) {
                existingEvidences.put(evidence.getId(), evidence);
            }
        }

        validateRemovedItems(request.safeRemovedItemIds(), existingItems.keySet());
        Set<Long> retainedItemIds = new HashSet<>();
        for (HandoverRequest.DraftItem itemRequest : request.items()) {
            List<HandoverEvidence> copiedEvidences = copyEvidences(
                    itemRequest.safeEvidenceIds(), existingEvidences);

            if (itemRequest.itemId() == null) {
                handover.addItem(toNewItem(itemRequest, copiedEvidences));
                continue;
            }

            HandoverItem existingItem = existingItems.get(itemRequest.itemId());
            if (existingItem == null || request.safeRemovedItemIds().contains(itemRequest.itemId())) {
                throw new CustomException(ErrorCode.INVALID_DRAFT);
            }
            retainedItemIds.add(existingItem.getId());
            existingItem.updateDraft(
                    itemRequest.category(),
                    itemRequest.title(),
                    itemRequest.description(),
                    itemRequest.assigneeMemberId(),
                    itemRequest.reviewStatus(),
                    copiedEvidences
            );
        }

        handover.removeItemsNotIn(retainedItemIds);
        int removedItemCount = existingItems.size() - retainedItemIds.size();
        DeliverySetting delivery = resolveDelivery(request.delivery());
        handover.applyDeliverySetting(delivery);
        Handover saved = handoverRepository.saveAndFlush(handover);

        return new HandoverResponse.DraftSaved(
                saved.getId(),
                saved.getItems().size(),
                removedItemCount,
                saved.getDelivery().getScheduledAt(),
                normalizeVersion(saved.getVersion())
        );
    }

    @Transactional
    public HandoverResponse.Delivered deliver(Long handoverId, HandoverRequest.Deliver request) {
        Handover handover = findHandover(handoverId);
        if (Objects.equals(request.deliveryRequestId(), handover.getDeliveryRequestId())) {
            return toDelivered(handover);
        }
        if (handover.isLocked()) {
            throw new CustomException(ErrorCode.HANDOVER_ALREADY_DELIVERED);
        }
        validateVersion(request.version(), handover.getVersion(), ErrorCode.HANDOVER_VERSION_CONFLICT);
        if (handover.getGenerationStatus() != GenerationStatus.COMPLETED
                || handover.getItems().isEmpty()
                || handover.getDelivery() == null) {
            throw new CustomException(ErrorCode.HANDOVER_NOT_READY);
        }
        if (handover.hasReviewAlerts() && !Boolean.TRUE.equals(request.acknowledgeReviewAlerts())) {
            throw new CustomException(ErrorCode.REVIEW_ALERT_NOT_ACKNOWLEDGED);
        }

        long deliveryId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        handover.scheduleDelivery(deliveryId, request.deliveryRequestId());
        Handover saved = handoverRepository.saveAndFlush(handover);
        return toDelivered(saved);
    }

    private void validateSourceRange(HandoverRequest.SourceRange range) {
        if (range.from() == null || range.to() == null || !range.from().isBefore(range.to())) {
            throw new CustomException(ErrorCode.INVALID_SOURCE_RANGE);
        }
    }

    private void validateEditable(Handover handover) {
        if (handover.isLocked()) {
            throw new CustomException(ErrorCode.HANDOVER_LOCKED);
        }
    }

    private void validateVersion(Long requested, Long current, ErrorCode errorCode) {
        if (!Objects.equals(requested, normalizeVersion(current))) {
            throw new CustomException(errorCode);
        }
    }

    private Long normalizeVersion(Long version) {
        return version == null ? 0L : version;
    }

    private void validateRemovedItems(List<Long> removedItemIds, Set<Long> existingItemIds) {
        if (!existingItemIds.containsAll(removedItemIds)) {
            throw new CustomException(ErrorCode.INVALID_DRAFT);
        }
    }

    private List<HandoverEvidence> copyEvidences(
            List<Long> evidenceIds,
            Map<Long, HandoverEvidence> existingEvidences
    ) {
        return evidenceIds.stream().map(id -> {
            HandoverEvidence evidence = existingEvidences.get(id);
            if (evidence == null) {
                throw new CustomException(ErrorCode.INVALID_EVIDENCE);
            }
            return evidence.copy();
        }).toList();
    }

    private HandoverItem toNewItem(
            HandoverRequest.DraftItem request,
            List<HandoverEvidence> evidences
    ) {
        return new HandoverItem(
                request.category(),
                request.title(),
                request.description(),
                request.assigneeMemberId(),
                request.reviewStatus(),
                true,
                evidences
        );
    }

    private DeliverySetting resolveDelivery(HandoverRequest.Delivery request) {
        try {
            ZoneId zoneId = ZoneId.of(request.timezone());
            ZonedDateTime now = ZonedDateTime.now(zoneId).truncatedTo(ChronoUnit.SECONDS);
            OffsetDateTime scheduledAt = switch (request.timingType()) {
                case NOW -> now.toOffsetDateTime();
                case SCHEDULED -> validateScheduledAt(request.scheduledAt(), now);
                case NEXT_SHIFT_START -> nextShiftStart(now).toOffsetDateTime();
            };
            return new DeliverySetting(
                    request.targetTeamId(),
                    request.recipientMemberId(),
                    request.timingType(),
                    request.timezone(),
                    scheduledAt
            );
        } catch (DateTimeException e) {
            throw new CustomException(ErrorCode.INVALID_DRAFT, "유효하지 않은 전달 시간 또는 타임존입니다.");
        }
    }

    private OffsetDateTime validateScheduledAt(OffsetDateTime scheduledAt, ZonedDateTime now) {
        if (scheduledAt == null || !scheduledAt.isAfter(now.toOffsetDateTime())) {
            throw new CustomException(ErrorCode.INVALID_DRAFT, "예약 전달 시각은 현재 이후여야 합니다.");
        }
        return scheduledAt;
    }

    private ZonedDateTime nextShiftStart(ZonedDateTime now) {
        ZonedDateTime todayAtNine = now.withHour(9).withMinute(0).withSecond(0).withNano(0);
        return now.isBefore(todayAtNine) ? todayAtNine : todayAtNine.plusDays(1);
    }

    private Handover findHandover(Long handoverId) {
        return handoverRepository.findOneById(handoverId)
                .orElseThrow(() -> new CustomException(ErrorCode.HANDOVER_NOT_FOUND));
    }

    private HandoverResponse.GenerationJob toGenerationJob(Handover handover) {
        return new HandoverResponse.GenerationJob(
                handover.getId(),
                handover.getGenerationJobId(),
                handover.getStatus()
        );
    }

    private HandoverResponse.Detail toDetail(Handover handover) {
        long verifiedCount = handover.getItems().stream()
                .filter(item -> item.getReviewStatus() == ReviewStatus.VERIFIED)
                .count();
        long needsReviewCount = handover.getItems().stream()
                .filter(item -> item.getReviewStatus() == ReviewStatus.NEEDS_REVIEW)
                .count();
        long unansweredCount = handover.getItems().stream()
                .filter(item -> item.getReviewStatus() == ReviewStatus.UNANSWERED)
                .count();

        return new HandoverResponse.Detail(
                handover.getId(),
                handover.getStatus(),
                handover.getLastSyncedAt(),
                new HandoverResponse.Generation(
                        handover.getGenerationStatus(),
                        handover.getGenerationProgress()
                ),
                new HandoverResponse.ReviewSummary(
                        verifiedCount,
                        needsReviewCount,
                        unansweredCount,
                        handover.getItems().size()
                ),
                handover.getItems().stream().map(this::toItem).toList(),
                handover.getItems().stream()
                        .filter(HandoverItem::requiresReview)
                        .map(this::toReviewAlert)
                        .toList(),
                toDelivery(handover.getDelivery()),
                normalizeVersion(handover.getVersion())
        );
    }

    private HandoverResponse.Item toItem(HandoverItem item) {
        return new HandoverResponse.Item(
                item.getId(),
                item.getCategory(),
                item.getTitle(),
                item.getDescription(),
                item.getAssigneeMemberId(),
                item.getReviewStatus(),
                item.getEvidences().stream().map(this::toEvidence).toList()
        );
    }

    private HandoverResponse.Evidence toEvidence(HandoverEvidence evidence) {
        return new HandoverResponse.Evidence(
                evidence.getId(),
                evidence.getProvider(),
                evidence.getSourceName(),
                evidence.getSnippet(),
                evidence.getSourceUrl()
        );
    }

    private HandoverResponse.ReviewAlert toReviewAlert(HandoverItem item) {
        String type = item.getReviewStatus() == ReviewStatus.UNANSWERED
                ? "UNANSWERED_QUESTION"
                : "INSUFFICIENT_EVIDENCE";
        String message = item.getReviewStatus() == ReviewStatus.UNANSWERED
                ? item.getTitle()
                : "근거가 부족하거나 담당자 확인이 필요한 내용입니다.";
        return new HandoverResponse.ReviewAlert(type, item.getId(), message);
    }

    private HandoverResponse.Delivery toDelivery(DeliverySetting delivery) {
        if (delivery == null) {
            return null;
        }
        return new HandoverResponse.Delivery(
                delivery.getTargetTeamId(),
                delivery.getRecipientMemberId(),
                delivery.getTimingType(),
                delivery.getTimezone(),
                delivery.getScheduledAt()
        );
    }

    private HandoverResponse.Delivered toDelivered(Handover handover) {
        return new HandoverResponse.Delivered(
                handover.getId(),
                handover.getDeliveryId(),
                handover.getStatus(),
                handover.getDelivery().getScheduledAt(),
                handover.getDelivery().getTimezone()
        );
    }
}
