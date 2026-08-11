package com.example.likelionhackathon.domain.handover.entity;

import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ItemCategory;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ReviewStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoverItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handover_id", nullable = false)
    private Handover handover;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    private Long assigneeMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus reviewStatus;

    @Column(nullable = false)
    private boolean manuallyEdited;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<HandoverEvidence> evidences = new ArrayList<>();

    public HandoverItem(
            ItemCategory category,
            String title,
            String description,
            Long assigneeMemberId,
            ReviewStatus reviewStatus,
            boolean manuallyEdited,
            Collection<HandoverEvidence> evidences
    ) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.assigneeMemberId = assigneeMemberId;
        this.reviewStatus = reviewStatus;
        this.manuallyEdited = manuallyEdited;
        evidences.forEach(this::addEvidence);
    }

    void attachTo(Handover handover) {
        this.handover = handover;
    }

    public void addEvidence(HandoverEvidence evidence) {
        evidence.attachTo(this);
        evidences.add(evidence);
    }

    public void updateDraft(
            ItemCategory category,
            String title,
            String description,
            Long assigneeMemberId,
            ReviewStatus reviewStatus,
            Collection<HandoverEvidence> evidences
    ) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.assigneeMemberId = assigneeMemberId;
        this.reviewStatus = reviewStatus;
        this.manuallyEdited = true;
        this.evidences.clear();
        evidences.forEach(this::addEvidence);
    }

    public boolean requiresReview() {
        return reviewStatus != ReviewStatus.VERIFIED;
    }

    public HandoverItem copyForRefresh() {
        List<HandoverEvidence> copied = evidences.stream().map(HandoverEvidence::copy).toList();
        return new HandoverItem(category, title, description, assigneeMemberId, reviewStatus, true, copied);
    }
}
