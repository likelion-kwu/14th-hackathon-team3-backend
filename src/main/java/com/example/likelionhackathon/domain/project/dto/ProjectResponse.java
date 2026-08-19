package com.example.likelionhackathon.domain.project.dto;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.AccessScope;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ParticipatingCompanyRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.WorkDay;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class ProjectResponse {

    private ProjectResponse() {
    }

    public record Created(Long projectId, ProjectStatus status) {
    }

    public record Summary(
            Long projectId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            ProjectStatus status,
            long memberCount,
            boolean joined,
            Long cycleId
        ) {
    }

    public record Detail(
            Long projectId,
            String name,
            String objective,
            LocalDate startDate,
            LocalDate endDate,
            ProjectStatus status,
            List<ParticipatingCompany> participatingCompanies,
            List<TeamSchedule> teamSchedules,
            List<DetailMember> members,
            List<PendingInvitation> pendingInvitations,
            List<Integration> integrations,
            Long version
    ) {
    }

    public record ParticipatingCompany(
            Long companyId,
            String name,
            ParticipatingCompanyRole role
    ) {
    }

    public record TeamSchedule(
            Long teamId,
            String teamName,
            String timezone,
            String languageCode,
            LocalTime workStartTime,
            LocalTime workEndTime,
            List<WorkDay> workDays
    ) {
    }

    public record DetailMember(
            Long memberId,
            String name,
            Long teamId,
            ProjectMemberRole role,
            AccessScope accessScope
    ) {
    }

    public record Updated(Long projectId, ProjectStatus status, Long version) {
    }

    public record MemberDirectory(
            List<Member> members,
            List<PendingInvitation> pendingInvitations
    ) {
    }

    public record Member(
            Long memberId,
            String name,
            String companyName,
            String teamName,
            ProjectMemberRole role,
            AccessScope accessScope,
            ProjectMemberStatus status
    ) {
    }

    public record PendingInvitation(Long invitationId, String email) {
    }

    public record MembersManaged(int processedCount, List<FailedAction> failedActions) {
    }

    public record Joined(
            Long projectId,
            Long memberId,
            ProjectMemberRole role,
            AccessScope accessScope
    ) {
    }

    public record TeamSettingsSaved(int updatedTeamCount) {
    }

    public record IntegrationsManaged(int processedCount, List<FailedAction> failedActions) {
    }

    public record OAuthStarted(
            String authorizationUrl,
            OffsetDateTime expiresAt
    ) {
    }

    public record OAuthConnected(
            Long integrationId,
            IntegrationProvider provider,
            IntegrationStatus status
    ) {
    }

    public record Integration(
            Long integrationId,
            IntegrationProvider provider,
            IntegrationStatus status,
            OffsetDateTime lastSyncedAt
    ) {
    }

    public record FailedAction(Long actionId, String code, String message) {
    }
}
