package com.example.likelionhackathon.domain.conversation.dto;

import com.example.likelionhackathon.domain.user.entity.UserEnums.ActivityStatus;

import java.time.LocalDateTime;
import java.util.List;

public final class ConversationResponse {
    private ConversationResponse() {
    }

    public record DirectConversation(Long conversationId, boolean created, TargetMember targetMember) {
    }

    public record TargetMember(Long memberId, String name, String companyName, String teamName,
                               String jobTitle, ActivityStatus activityStatus) {
    }

    public record Messages(
            Long conversationId,
            List<MessageItem> messages,
            int page,
            int size,
            boolean hasNext
    ) {
    }

    public record MessageItem(
            Long messageId,
            Long senderMemberId,
            String senderName,
            String originalContent,
            String translatedContent,
            boolean translationUsed,
            LocalDateTime createdAt
    ) {
    }

    public record RecentConversations(List<RecentConversation> conversations) {
    }

    public record RecentConversation(
            Long conversationId,
            Long targetMemberId,
            String targetName,
            String companyName,
            String teamName,
            String jobTitle,
            ActivityStatus activityStatus,
            LocalDateTime lastMessageAt
    ) {
    }

    public record TranslationPreview(
            String originalContent,
            String translatedContent,
            String targetLanguage,
            boolean translationRequired,
            String nuance
    ) {
    }

    public record SentMessage(
            Long messageId,
            Long conversationId,
            Long senderMemberId,
            String originalContent,
            String translatedContent,
            boolean translationUsed,
            LocalDateTime createdAt
    ) {
    }
}
