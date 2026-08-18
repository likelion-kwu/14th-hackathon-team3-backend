package com.example.likelionhackathon.domain.project.entity;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(
        name = "project_oauth_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_oauth_state_hash",
                columnNames = "state_hash"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectOAuthState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private IntegrationProvider provider;

    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    private OffsetDateTime consumedAt;

    public static ProjectOAuthState issue(
            Project project,
            IntegrationProvider provider,
            String stateHash,
            OffsetDateTime expiresAt
    ) {
        ProjectOAuthState state = new ProjectOAuthState();
        state.project = project;
        state.provider = provider;
        state.stateHash = stateHash;
        state.expiresAt = expiresAt;
        return state;
    }

    public boolean isUsable(OffsetDateTime now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(OffsetDateTime now) {
        consumedAt = now;
    }
}
