package com.example.likelionhackathon.domain.conversation.entity;

import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(
        name = "uk_conversation_workspace_participants",
        columnNames = {"workspace_id", "member_low_id", "member_high_id"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "member_low_id", nullable = false)
    private Long memberLowId;

    @Column(name = "member_high_id", nullable = false)
    private Long memberHighId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Conversation create(Workspace workspace, Long firstMemberId, Long secondMemberId) {
        if (workspace == null || firstMemberId == null || secondMemberId == null) {
            throw new IllegalArgumentException("Workspace and participant IDs must not be null.");
        }
        if (firstMemberId.equals(secondMemberId)) {
            throw new IllegalArgumentException("A direct conversation requires two different participants.");
        }
        Conversation conversation = new Conversation();
        conversation.workspace = workspace;
        conversation.memberLowId = Math.min(firstMemberId, secondMemberId);
        conversation.memberHighId = Math.max(firstMemberId, secondMemberId);
        return conversation;
    }

    public boolean hasParticipant(Long memberId) {
        return memberLowId.equals(memberId) || memberHighId.equals(memberId);
    }

    public Long getOtherMemberId(Long memberId) {
        if (memberLowId.equals(memberId)) return memberHighId;
        if (memberHighId.equals(memberId)) return memberLowId;
        throw new IllegalArgumentException("The member is not a participant in this conversation.");
    }
}
