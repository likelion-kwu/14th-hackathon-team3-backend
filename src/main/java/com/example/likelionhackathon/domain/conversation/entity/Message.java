package com.example.likelionhackathon.domain.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "sender_member_id", nullable = false)
    private Long senderMemberId;

    @Column(nullable = false, length = 4000)
    private String originalContent;

    @Column(length = 4000)
    private String translatedContent;

    @Column(nullable = false)
    private boolean translationUsed;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Message create(Conversation conversation, Long senderMemberId, String originalContent,
                                 String translatedContent, boolean translationUsed) {
        if (!translationUsed) {
            translatedContent = null;
        } else if (translatedContent == null || translatedContent.isBlank()) {
            throw new IllegalArgumentException("Translated content is required when translation is used.");
        }
        Message message = new Message();
        message.conversation = conversation;
        message.senderMemberId = senderMemberId;
        message.originalContent = originalContent;
        message.translatedContent = translatedContent;
        message.translationUsed = translationUsed;
        return message;
    }
}
