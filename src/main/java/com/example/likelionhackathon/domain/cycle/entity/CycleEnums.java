package com.example.likelionhackathon.domain.cycle.entity;

public final class CycleEnums {

    private CycleEnums() {
    }

    public enum CycleStatus {
        PLANNED, IN_PROGRESS, COMPLETED;

        /** 선언 순서(PLANNED → IN_PROGRESS → COMPLETED)가 곧 진행 순서다. */
        public boolean isAheadOf(CycleStatus other) {
            return ordinal() > other.ordinal();
        }

        // PLANNED → IN_PROGRESS → COMPLETED 순서만 허용한다.
        public boolean canTransitionTo(CycleStatus next) {
            return switch (this) {
                case PLANNED -> next == IN_PROGRESS;
                case IN_PROGRESS -> next == COMPLETED;
                case COMPLETED -> false;
            };
        }
    }

    public enum ActivityType {
        ISSUE_STATUS_CHANGED, AI_PROGRESS_UPDATED, COMMENT_ADDED, FILE_UPLOADED
    }

    public enum AnalysisStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    public enum EvidenceSource {
        SLACK, TEAMS, NOTION, GOOGLE_DRIVE
    }

    public enum CheckNeededType {
        INSUFFICIENT_EVIDENCE, UNANSWERED_QUESTION
    }
}
