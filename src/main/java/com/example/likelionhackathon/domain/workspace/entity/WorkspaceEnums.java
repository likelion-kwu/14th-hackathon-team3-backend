package com.example.likelionhackathon.domain.workspace.entity;

public final class WorkspaceEnums {

    private WorkspaceEnums() {
    }

    public enum WorkspaceStatus {
        ACTIVE,
        ARCHIVED
    }

    public enum WorkspaceRole {
        OWNER,
        ADMIN,
        MEMBER;

        public boolean canManage() {
            return this == OWNER || this == ADMIN;
        }
    }

    public enum AssignableWorkspaceRole {
        ADMIN,
        MEMBER;

        public WorkspaceRole toWorkspaceRole() {
            return WorkspaceRole.valueOf(name());
        }
    }

    public enum WorkspaceMemberStatus {
        ACTIVE,
        SUSPENDED
    }

    public enum WorkspaceMemberViewStatus {
        ACTIVE,
        INVITED,
        SUSPENDED
    }

    public enum MemberActionType {
        UPDATE,
        SUSPEND,
        REMOVE
    }

    public enum InvitationType {
        LINK,
        EMAIL
    }

    public enum InvitationStatus {
        PENDING,
        ACCEPTED
    }
}
