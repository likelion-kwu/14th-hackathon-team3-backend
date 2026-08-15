package com.example.likelionhackathon.domain.cycle.entity;

import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CheckNeededType;
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
public class AnalysisCheckNeeded {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckNeededType type;

    @Column(nullable = false, length = 500)
    private String message;

    private Long issueId;

    public AnalysisCheckNeeded(CheckNeededType type, String message, Long issueId) {
        this.type = type;
        this.message = message;
        this.issueId = issueId;
    }
}
