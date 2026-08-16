package com.example.likelionhackathon.domain.conversation.controller;

import com.example.likelionhackathon.domain.conversation.dto.ConversationRequest;
import com.example.likelionhackathon.domain.conversation.dto.ConversationResponse;
import com.example.likelionhackathon.domain.conversation.service.ConversationService;
import com.example.likelionhackathon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag(name = "대화")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    @Operation(summary = "1:1 대화방 조회 또는 생성")
    @PostMapping("/direct")
    public ApiResponse<ConversationResponse.DirectConversation> findOrCreateDirectConversation(
            @PathVariable Long workspaceId,
            @Valid @RequestBody ConversationRequest.DirectConversation request) {
        return ApiResponse.success("1:1 대화방을 조회했습니다.",
                conversationService.findOrCreateDirectConversation(workspaceId, request));
    }

    @Operation(summary = "메시지 대화 내용 조회")
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<ConversationResponse.Messages> getMessages(
            @PathVariable Long workspaceId,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.success("메시지 대화 내용을 조회했습니다.",
                conversationService.getMessages(workspaceId, conversationId, page, size));
    }

    @Operation(summary = "최근 대화 조회")
    @GetMapping("/recent")
    public ApiResponse<ConversationResponse.RecentConversations> getRecentConversations(
            @PathVariable Long workspaceId
    ) {
        return ApiResponse.success("최근 대화를 조회했습니다.",
                conversationService.getRecentConversations(workspaceId));
    }

    @Operation(summary = "AI 뉘앙스 번역 미리보기")
    @PostMapping("/{conversationId}/translation-preview")
    public ApiResponse<ConversationResponse.TranslationPreview> previewTranslation(
            @PathVariable Long workspaceId,
            @PathVariable Long conversationId,
            @Valid @RequestBody ConversationRequest.TranslationPreview request
    ) {
        ConversationResponse.TranslationPreview response =
                conversationService.previewTranslation(workspaceId, conversationId, request);
        String message = response.translationRequired()
                ? "뉘앙스 번역이 완료되었습니다."
                : "동일한 언어를 사용하고 있습니다.";
        return ApiResponse.success(message, response);
    }

    @Operation(summary = "메시지 전송")
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<ConversationResponse.SentMessage>> sendMessage(
            @PathVariable Long workspaceId,
            @PathVariable Long conversationId,
            @Valid @RequestBody ConversationRequest.SendMessage request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("메시지를 전송했습니다.",
                        conversationService.sendMessage(workspaceId, conversationId, request)));
    }
}
