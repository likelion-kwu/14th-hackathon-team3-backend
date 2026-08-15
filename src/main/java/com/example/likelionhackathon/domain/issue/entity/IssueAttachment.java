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

    /**
     * 저장소에서 이 파일을 가리키는 키. 다운로드 권한 확인에 쓴다.
     * URL 문자열을 잘라 맞추면 저장 방식이 바뀔 때 조회가 조용히 빗나가므로 따로 보관한다.
     */
    @Column(nullable = false, length = 255)
    private String storedKey;

    public IssueAttachment(String fileName, Long fileSize, String fileUrl, String storedKey) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileUrl = fileUrl;
        this.storedKey = storedKey;
    }

    void attachTo(Issue issue) {
        this.issue = issue;
    }
}
