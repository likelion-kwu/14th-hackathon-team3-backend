package com.example.likelionhackathon.domain.cycle.entity;

import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.AnalysisStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사이클 화면 "AI 분석" 탭 결과. 재실행 요청마다 새 행을 만들고
 * 가장 최근 완료 건을 조회에 사용한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CycleAiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long cycleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime analyzedAt;

    @Column(nullable = false)
    private int progressRate;

    @Column(nullable = false)
    private int previousProgressRate;

    @Column(length = 2000)
    private String summary;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cycle_analysis_evidence", joinColumns = @JoinColumn(name = "analysis_id"))
    private List<AnalysisEvidence> evidences = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cycle_analysis_check_needed", joinColumns = @JoinColumn(name = "analysis_id"))
    private List<AnalysisCheckNeeded> checkNeeded = new ArrayList<>();

    public static CycleAiAnalysis pending(Long cycleId, int previousProgressRate) {
        CycleAiAnalysis analysis = new CycleAiAnalysis();
        analysis.cycleId = cycleId;
        analysis.status = AnalysisStatus.PENDING;
        analysis.requestedAt = LocalDateTime.now();
        analysis.previousProgressRate = previousProgressRate;
        analysis.progressRate = previousProgressRate;
        return analysis;
    }

    public void markRunning() {
        this.status = AnalysisStatus.RUNNING;
    }

    public void complete(
            int progressRate,
            String summary,
            List<AnalysisEvidence> evidences,
            List<AnalysisCheckNeeded> checkNeeded,
            LocalDateTime analyzedAt
    ) {
        this.progressRate = progressRate;
        this.summary = summary;
        this.evidences.clear();
        this.evidences.addAll(evidences);
        this.checkNeeded.clear();
        this.checkNeeded.addAll(checkNeeded);
        this.analyzedAt = analyzedAt;
        this.status = AnalysisStatus.COMPLETED;
    }

    public void fail() {
        this.status = AnalysisStatus.FAILED;
    }

    public boolean isRunning() {
        return status == AnalysisStatus.PENDING || status == AnalysisStatus.RUNNING;
    }
}
