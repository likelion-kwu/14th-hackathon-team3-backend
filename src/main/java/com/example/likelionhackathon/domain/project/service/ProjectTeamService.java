package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.ProjectTeam;
import com.example.likelionhackathon.domain.project.repository.ProjectTeamRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectTeamService {

    private final ProjectAccessService projectAccessService;
    private final ProjectTeamRepository teamRepository;

    @Transactional
    public ProjectResponse.TeamSettingsSaved saveSettings(
            Long projectId,
            ProjectRequest.SaveTeamSettings request
    ) {
        projectAccessService.findProject(projectId);
        projectAccessService.requireAdmin(projectId);
        if (request == null || request.teams() == null || request.teams().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_TEAM_SETTING);
        }

        Set<Long> teamIds = new HashSet<>();
        for (ProjectRequest.TeamSetting setting : request.teams()) {
            validate(setting, teamIds);
            ProjectTeam team = teamRepository.findByIdAndProjectId(setting.teamId(), projectId)
                    .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
            team.updateSchedule(
                    setting.countryCode().trim().toUpperCase(),
                    setting.timezone().trim(),
                    setting.languageCode().trim().toLowerCase(),
                    setting.workStartTime(),
                    setting.workEndTime(),
                    setting.workDays(),
                    setting.includeNationalHolidays()
            );
        }
        return new ProjectResponse.TeamSettingsSaved(request.teams().size());
    }

    private void validate(ProjectRequest.TeamSetting setting, Set<Long> teamIds) {
        if (setting == null
                || setting.teamId() == null
                || !teamIds.add(setting.teamId())
                || setting.countryCode() == null
                || setting.countryCode().trim().length() != 2
                || setting.timezone() == null
                || setting.timezone().isBlank()
                || setting.languageCode() == null
                || setting.languageCode().isBlank()
                || setting.languageCode().trim().length() > 10
                || setting.workStartTime() == null
                || setting.workEndTime() == null
                || !setting.workStartTime().isBefore(setting.workEndTime())
                || setting.workDays() == null
                || setting.workDays().isEmpty()
                || setting.workDays().stream().anyMatch(day -> day == null)
                || setting.includeNationalHolidays() == null) {
            throw new CustomException(ErrorCode.INVALID_TEAM_SETTING);
        }
        try {
            ZoneId.of(setting.timezone().trim());
        } catch (DateTimeException e) {
            throw new CustomException(ErrorCode.INVALID_TIMEZONE);
        }
    }
}
