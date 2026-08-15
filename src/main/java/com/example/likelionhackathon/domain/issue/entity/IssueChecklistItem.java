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
public class IssueChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @Column(nullable = false, length = 300)
    private String content;

    @Column(nullable = false)
    private boolean done;

    @Column(nullable = false)
    private int orderIndex;

    public IssueChecklistItem(String content, boolean done, int orderIndex) {
        this.content = content;
        this.done = done;
        this.orderIndex = orderIndex;
    }

    void attachTo(Issue issue) {
        this.issue = issue;
    }

    public void update(String content, boolean done, int orderIndex) {
        this.content = content;
        this.done = done;
        this.orderIndex = orderIndex;
    }

    public void changeDone(boolean done) {
        this.done = done;
    }
}
