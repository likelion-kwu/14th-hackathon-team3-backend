package com.example.likelionhackathon.domain.project.dto;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.AccessScope;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationActionType;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ParticipatingCompanyRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberActionType;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberRole;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.WorkDay;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class ProjectRequest {

    private ProjectRequest() {
    }

    public record Create(
            String name,
            String objective,
            LocalDate startDate,
            LocalDate endDate,
            List<ParticipatingCompany> participatingCompanies
    ) {
    }

    public record Update(
            String name,
            String objective,
            LocalDate startDate,
            LocalDate endDate,
            List<ParticipatingCompany> participatingCompanies,
            ProjectStatus status,
            Long version
    ) {
    }

    public record ParticipatingCompany(
            Long companyId,
            ParticipatingCompanyRole role
    ) {
    }

    public record ManageMembers(List<MemberAction> actions) {
    }

    public record MemberAction(
            ProjectMemberActionType type,
            Long memberId,
            Long teamId,
            ProjectMemberRole role,
            AccessScope accessScope
    ) {
    }

    public record SaveTeamSettings(List<TeamSetting> teams) {
    }

    public record TeamSetting(
            Long teamId,
            String countryCode,
            String timezone,
            String languageCode,
            LocalTime workStartTime,
            LocalTime workEndTime,
            List<WorkDay> workDays,
            Boolean includeNationalHolidays
    ) {
    }

    public record ManageIntegrations(List<IntegrationAction> actions) {
    }

    public record IntegrationAction(
            IntegrationActionType type,
            Long integrationId,
            List<String> resourceIds,
            Integer syncIntervalMinutes
    ) {
    }

    public record CompleteIntegrationOAuth(
            String code,
            String state,
            List<String> resourceIds,
            Integer syncIntervalMinutes
    ) {
    }
}
