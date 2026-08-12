package com.example.likelionhackathon.domain.handover.entity;

public final class HandoverEnums {

    private HandoverEnums() {
    }

    public enum Provider {
        SLACK, TEAMS, NOTION, GOOGLE_DRIVE
    }

    public enum HandoverStatus {
        AI_GENERATING, REVIEW_REQUIRED, READY, SCHEDULED, DELIVERED, GENERATION_FAILED
    }

    public enum GenerationStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    public enum ItemCategory {
        COMPLETED, IN_PROGRESS, NEXT_ACTION, DECISION, QUESTION
    }

    public enum ReviewStatus {
        VERIFIED, NEEDS_REVIEW, UNANSWERED
    }

    public enum TimingType {
        NOW, SCHEDULED, NEXT_SHIFT_START
    }
}
