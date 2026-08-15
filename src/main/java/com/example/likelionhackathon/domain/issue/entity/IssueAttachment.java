package com.example.likelionhackathon.domain.issue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @Column(nullable = false, length = 255)
    private String fileName;

    private Long fileSize;

    @Column(nullable = false, length = 1000)
    private String fileUrl;

    public IssueAttachment(String fileName, Long fileSize, String fileUrl) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileUrl = fileUrl;
    }

    void attachTo(Issue issue) {
        this.issue = issue;
    }
}
