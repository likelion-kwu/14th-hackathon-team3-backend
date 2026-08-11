package com.example.likelionhackathon.domain.handover.entity;

import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.GenerationStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.HandoverStatus;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.TimingType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long cycleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HandoverStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GenerationStatus generationStatus;

    @Column(nullable = false)
    private int generationProgress;

    @Column(nullable = false, unique = true, length = 40)
    private String generationJobId;

    @Column(nullable = false)
    private OffsetDateTime sourceFrom;

    @Column(nullable = false)
    private OffsetDateTime sourceTo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "handover_source_types")
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private Set<Provider> sourceTypes = new LinkedHashSet<>();

    private OffsetDateTime lastSyncedAt;

    @Embedded
    private DeliverySetting delivery;

    private Long deliveryId;

    @Column(unique = true)
    private UUID deliveryRequestId;

    @OneToMany(mappedBy = "handover", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<HandoverItem> items = new ArrayList<>();

    @Version
    private Long version;

    public static Handover startGeneration(
            Long projectId,
            Long cycleId,
            OffsetDateTime sourceFrom,
            OffsetDateTime sourceTo,
            Collection<Provider> sourceTypes
    ) {
        Handover handover = new Handover();
        handover.projectId = projectId;
        handover.cycleId = cycleId;
        handover.sourceFrom = sourceFrom;
        handover.sourceTo = sourceTo;
        handover.sourceTypes.addAll(sourceTypes);
        handover.startNewJob();
        return handover;
    }

    public void startRefresh(Collection<Provider> providers) {
        if (providers != null && !providers.isEmpty()) {
            sourceTypes.clear();
            sourceTypes.addAll(providers);
        }
        startNewJob();
    }

    private void startNewJob() {
        status = HandoverStatus.AI_GENERATING;
        generationStatus = GenerationStatus.PENDING;
        generationProgress = 0;
        generationJobId = "job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public void markGenerationRunning() {
        generationStatus = GenerationStatus.RUNNING;
        generationProgress = 10;
    }

    public void completeGeneration(List<HandoverItem> generatedItems, OffsetDateTime syncedAt) {
        replaceItems(generatedItems);
        generationStatus = GenerationStatus.COMPLETED;
        generationProgress = 100;
        lastSyncedAt = syncedAt;
        updateReviewStatus();
    }

    public void failGeneration() {
        generationStatus = GenerationStatus.FAILED;
        generationProgress = 0;
        status = HandoverStatus.GENERATION_FAILED;
    }

    public void replaceItems(List<HandoverItem> replacement) {
        items.clear();
        replacement.forEach(this::addItem);
    }

    public void addItem(HandoverItem item) {
        item.attachTo(this);
        items.add(item);
    }

    public void updateDraft(List<HandoverItem> replacement, DeliverySetting delivery) {
        replaceItems(replacement);
        this.delivery = delivery;
        updateReviewStatus();
    }

    public void applyDeliverySetting(DeliverySetting delivery) {
        this.delivery = delivery;
        updateReviewStatus();
    }

    public void removeItemsNotIn(Set<Long> retainedItemIds) {
        items.removeIf(item -> item.getId() != null && !retainedItemIds.contains(item.getId()));
    }

    private void updateReviewStatus() {
        boolean reviewRequired = items.stream().anyMatch(HandoverItem::requiresReview);
        status = reviewRequired ? HandoverStatus.REVIEW_REQUIRED : HandoverStatus.READY;
    }

    public void scheduleDelivery(Long deliveryId, UUID requestId) {
        this.deliveryId = deliveryId;
        this.deliveryRequestId = requestId;
        this.status = HandoverStatus.SCHEDULED;
    }

    public boolean isLocked() {
        return status == HandoverStatus.SCHEDULED || status == HandoverStatus.DELIVERED;
    }

    public boolean hasReviewAlerts() {
        return items.stream().anyMatch(HandoverItem::requiresReview);
    }
}
