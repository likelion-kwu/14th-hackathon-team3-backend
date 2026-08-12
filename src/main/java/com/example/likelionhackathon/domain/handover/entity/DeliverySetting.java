package com.example.likelionhackathon.domain.handover.entity;

import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.TimingType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliverySetting {

    private Long targetTeamId;
    private Long recipientMemberId;

    @Enumerated(EnumType.STRING)
    private TimingType timingType;

    private String timezone;
    private OffsetDateTime scheduledAt;

    public DeliverySetting(
            Long targetTeamId,
            Long recipientMemberId,
            TimingType timingType,
            String timezone,
            OffsetDateTime scheduledAt
    ) {
        this.targetTeamId = targetTeamId;
        this.recipientMemberId = recipientMemberId;
        this.timingType = timingType;
        this.timezone = timezone;
        this.scheduledAt = scheduledAt;
    }
}
