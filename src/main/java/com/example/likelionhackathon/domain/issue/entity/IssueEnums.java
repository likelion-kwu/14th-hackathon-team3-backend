package com.example.likelionhackathon.domain.issue.entity;

public final class IssueEnums {

    private IssueEnums() {
    }

    public enum IssueStatus {
        TODO, IN_PROGRESS, NEEDS_REVIEW, DELAYED, DONE, CANCELED;

        // 완료·취소는 종결 상태라 더 이상 옮길 수 없다.
        public boolean isClosed() {
            return this == DONE || this == CANCELED;
        }

        public boolean canTransitionTo(IssueStatus next) {
            if (isClosed()) {
                return false;
            }
            return this != next;
        }
    }

    public enum IssuePriority {
        LOW, NORMAL, HIGH, URGENT
    }
}
