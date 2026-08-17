package com.example.likelionhackathon.domain.cycle.entity;

import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.EvidenceSource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisEvidence {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceSource source;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(nullable = false)
    private int referenceCount;

    public AnalysisEvidence(EvidenceSource source, String label, int referenceCount) {
        this.source = source;
        this.label = label;
        this.referenceCount = referenceCount;
    }
}
