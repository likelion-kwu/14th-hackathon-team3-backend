package com.example.likelionhackathon.domain.handover.entity;

import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoverEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private HandoverItem item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(nullable = false, length = 200)
    private String sourceName;

    @Column(nullable = false, length = 1000)
    private String snippet;

    @Column(length = 1000)
    private String sourceUrl;

    public HandoverEvidence(Provider provider, String sourceName, String snippet, String sourceUrl) {
        this.provider = provider;
        this.sourceName = sourceName;
        this.snippet = snippet;
        this.sourceUrl = sourceUrl;
    }

    void attachTo(HandoverItem item) {
        this.item = item;
    }

    public HandoverEvidence copy() {
        return new HandoverEvidence(provider, sourceName, snippet, sourceUrl);
    }
}
