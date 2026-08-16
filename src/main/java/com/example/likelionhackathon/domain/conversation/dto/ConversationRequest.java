package com.example.likelionhackathon.domain.conversation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ConversationRequest {
    private ConversationRequest() {
    }

    public record DirectConversation(@NotNull Long targetMemberId) {
    }

    public record TranslationPreview(
            @NotBlank @Size(max = 4000) String content
    ) {
    }

    public record SendMessage(
            @NotBlank @Size(max = 4000) String originalContent,
            @NotNull Boolean translationUsed
    ) {
    }
}
