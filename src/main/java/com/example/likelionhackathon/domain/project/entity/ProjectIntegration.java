package com.example.likelionhackathon.domain.project.entity;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationStatus;
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

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Entity
@Table(name = "project_integrations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private IntegrationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private IntegrationStatus status;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "project_integration_resources")
    @Column(name = "resource_id", nullable = false, length = 255)
    private Set<String> resourceIds = new LinkedHashSet<>();

    private Integer syncIntervalMinutes;

    private OffsetDateTime lastSyncedAt;

    public static ProjectIntegration connect(
            IntegrationProvider provider,
            Collection<String> resourceIds,
            Integer syncIntervalMinutes
    ) {
        ProjectIntegration integration = new ProjectIntegration();
        integration.provider = provider;
        integration.status = IntegrationStatus.CONNECTED;
        integration.update(resourceIds, syncIntervalMinutes);
        return integration;
    }

    public void update(Collection<String> resourceIds, Integer syncIntervalMinutes) {
        if (resourceIds != null) {
            this.resourceIds.clear();
            this.resourceIds.addAll(resourceIds);
        }
        if (syncIntervalMinutes != null) {
            this.syncIntervalMinutes = syncIntervalMinutes;
        }
        status = IntegrationStatus.CONNECTED;
    }

    public void sync(OffsetDateTime syncedAt) {
        status = IntegrationStatus.CONNECTED;
        lastSyncedAt = syncedAt;
    }

    public void disconnect() {
        status = IntegrationStatus.DISCONNECTED;
    }

    void attachTo(Project project) {
        this.project = project;
    }
}
