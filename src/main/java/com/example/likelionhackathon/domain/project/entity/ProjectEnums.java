package com.example.likelionhackathon.domain.project.entity;

public final class ProjectEnums {

    private ProjectEnums() {
    }

    public enum ProjectStatus {
        DRAFT,
        ACTIVE,
        ENDED
    }

    public enum ParticipatingCompanyRole {
        HOST,
        PARTNER
    }

    public enum ProjectMemberRole {
        PROJECT_ADMIN,
        MEMBER;

        public boolean canManage() {
            return this == PROJECT_ADMIN;
        }
    }

    public enum AccessScope {
        FULL,
        COMPANY_ONLY,
        TEAM_ONLY
    }

    public enum ProjectMemberStatus {
        ACTIVE,
        SUSPENDED
    }

    public enum ProjectMemberViewStatus {
        ACTIVE,
        INVITED,
        SUSPENDED
    }

    public enum ProjectMemberActionType {
        INVITE,
        UPDATE,
        SUSPEND,
        REMOVE
    }

    public enum IntegrationProvider {
        SLACK,
        TEAMS,
        NOTION,
        FIGMA,
        GOOGLE_DRIVE
    }

    public enum IntegrationStatus {
        CONNECTED,
        DISCONNECTED
    }

    public enum IntegrationActionType {
        UPDATE,
        SYNC,
        DISCONNECT
    }

    public enum WorkDay {
        MON,
        TUE,
        WED,
        THU,
        FRI,
        SAT,
        SUN
    }
}
