package com.example.likelionhackathon.domain.handover.entity;

import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollaborationActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long cycleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(nullable = false, length = 200)
    private String sourceName;

    @Column(nullable = false, length = 8000)
    private String content;

    @Column(length = 1000)
    private String sourceUrl;

    @Column(nullable = false)
    private OffsetDateTime occurredAt;

    public CollaborationActivity(
            Long projectId,
            Long cycleId,
            Provider provider,
            String sourceName,
            String content,
            String sourceUrl,
            OffsetDateTime occurredAt
    ) {
        this.projectId = projectId;
        this.cycleId = cycleId;
        this.provider = provider;
        this.sourceName = sourceName;
        this.content = content;
        this.sourceUrl = sourceUrl;
        this.occurredAt = occurredAt;
    }
}
