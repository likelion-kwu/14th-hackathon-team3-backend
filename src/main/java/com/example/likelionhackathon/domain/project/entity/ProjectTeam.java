package com.example.likelionhackathon.domain.project.entity;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.WorkDay;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Entity
@Table(name = "project_teams")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String teamName;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 100)
    private String timezone;

    @Column(nullable = false, length = 10)
    private String languageCode;

    @Column(nullable = false)
    private LocalTime workStartTime;

    @Column(nullable = false)
    private LocalTime workEndTime;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "project_team_work_days")
    @Enumerated(EnumType.STRING)
    @Column(name = "work_day", nullable = false, length = 8)
    private Set<WorkDay> workDays = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean includeNationalHolidays;

    public static ProjectTeam createDefault(
            Long companyId,
            String countryCode,
            String timezone,
            String languageCode
    ) {
        ProjectTeam team = new ProjectTeam();
        team.companyId = companyId;
        team.teamName = "General";
        team.countryCode = countryCode;
        team.timezone = timezone;
        team.languageCode = languageCode;
        team.workStartTime = LocalTime.of(9, 0);
        team.workEndTime = LocalTime.of(18, 0);
        team.workDays.addAll(Set.of(WorkDay.MON, WorkDay.TUE, WorkDay.WED, WorkDay.THU, WorkDay.FRI));
        team.includeNationalHolidays = true;
        return team;
    }

    public void updateSchedule(
            String countryCode,
            String timezone,
            String languageCode,
            LocalTime workStartTime,
            LocalTime workEndTime,
            Collection<WorkDay> workDays,
            boolean includeNationalHolidays
    ) {
        this.countryCode = countryCode;
        this.timezone = timezone;
        this.languageCode = languageCode;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
        this.workDays.clear();
        this.workDays.addAll(workDays);
        this.includeNationalHolidays = includeNationalHolidays;
    }

    void attachTo(Project project) {
        this.project = project;
    }
}
