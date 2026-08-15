package com.example.likelionhackathon.domain.cycle.entity;

import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.ActivityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사이클 화면 "활동 기록" 탭에 쌓이는 타임라인 한 줄.
 * 유형마다 채워지는 필드가 달라 공통 필드 외에는 모두 nullable 이다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CycleActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long cycleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 50)
    private String actorName;

    @Column(nullable = false, length = 300)
    private String title;

    private Long issueId;

    @Column(length = 200)
    private String issueTitle;

    // 상태 변경(IN_PROGRESS → DONE)과 진행률 변경(72 → 78)을 모두 담아야 해서 문자열로 둔다.
    @Column(length = 50)
    private String beforeValue;

    @Column(length = 50)
    private String afterValue;

    @Column(length = 1000)
    private String reason;

    @Column(length = 255)
    private String fileName;

    private Long fileSize;

    private CycleActivity(Long cycleId, ActivityType type, LocalDateTime occurredAt, String actorName, String title) {
        this.cycleId = cycleId;
        this.type = type;
        this.occurredAt = occurredAt;
        this.actorName = actorName;
        this.title = title;
    }

    public static CycleActivity issueStatusChanged(
            Long cycleId,
            LocalDateTime occurredAt,
            String actorName,
            Long issueId,
            String issueTitle,
            String before,
            String after
    ) {
        CycleActivity activity = new CycleActivity(
                cycleId, ActivityType.ISSUE_STATUS_CHANGED, occurredAt, actorName,
                actorName + " 님이 이슈 상태를 변경했습니다."
        );
        activity.issueId = issueId;
        activity.issueTitle = issueTitle;
        activity.beforeValue = before;
        activity.afterValue = after;
        return activity;
    }

    public static CycleActivity aiProgressUpdated(
            Long cycleId,
            LocalDateTime occurredAt,
            int before,
            int after,
            String reason
    ) {
        CycleActivity activity = new CycleActivity(
                cycleId, ActivityType.AI_PROGRESS_UPDATED, occurredAt, "AI",
                "AI가 사이클 진행률을 업데이트했습니다."
        );
        activity.beforeValue = String.valueOf(before);
        activity.afterValue = String.valueOf(after);
        activity.reason = reason;
        return activity;
    }

    public static CycleActivity commentAdded(
            Long cycleId,
            LocalDateTime occurredAt,
            String actorName,
            Long issueId,
            String issueTitle
    ) {
        CycleActivity activity = new CycleActivity(
                cycleId, ActivityType.COMMENT_ADDED, occurredAt, actorName,
                actorName + " 님이 댓글을 남겼습니다."
        );
        activity.issueId = issueId;
        activity.issueTitle = issueTitle;
        return activity;
    }

    public static CycleActivity fileUploaded(
            Long cycleId,
            LocalDateTime occurredAt,
            String actorName,
            Long issueId,
            String fileName,
            Long fileSize
    ) {
        CycleActivity activity = new CycleActivity(
                cycleId, ActivityType.FILE_UPLOADED, occurredAt, actorName,
                actorName + " 님이 파일을 업로드했습니다."
        );
        activity.issueId = issueId;
        activity.fileName = fileName;
        activity.fileSize = fileSize;
        return activity;
    }
}
