package com.example.likelionhackathon.domain.conversation;

import com.example.likelionhackathon.domain.conversation.entity.Conversation;
import com.example.likelionhackathon.domain.conversation.entity.Message;
import com.example.likelionhackathon.domain.conversation.repository.ConversationRepository;
import com.example.likelionhackathon.domain.conversation.repository.MessageRepository;
import com.example.likelionhackathon.domain.conversation.service.OpenAiTranslationClient;
import com.example.likelionhackathon.domain.conversation.service.OpenAiTranslationClient.TranslationResult;
import com.example.likelionhackathon.domain.conversation.service.OpenAiTemporalExpressionClient;
import com.example.likelionhackathon.domain.conversation.service.TemporalModels.TemporalExtraction;
import com.example.likelionhackathon.domain.conversation.service.TemporalModels.*;
import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.entity.UserEnums.ActivityStatus;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceRepository;
import com.example.likelionhackathon.global.security.jwt.JwtTokenProvider;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;
    @MockitoBean OpenAiTranslationClient translationClient;
    @MockitoBean OpenAiTemporalExpressionClient temporalExpressionClient;

    private Workspace workspace;
    private User currentUser;
    private User targetUser;
    private WorkspaceMember currentMember;
    private WorkspaceMember targetMember;

    @BeforeEach
    void setUp() {
        when(temporalExpressionClient.extract(anyString(), anyString()))
                .thenReturn(new TemporalExtraction(false, List.of()));
        currentUser = saveUser("conversation-current@example.com", "Current", ActivityStatus.ACTIVE);
        targetUser = saveUser("conversation-target@example.com", "Target User", ActivityStatus.OFF);
        workspace = workspaceRepository.save(Workspace.create(
                "Conversation Workspace", "CONV-KR-0001", "RelAI", "KR", List.of()));
        currentMember = memberRepository.save(member(currentUser, "Current Member", "Platform", "Lead"));
        targetMember = memberRepository.save(member(targetUser, "Target Member", "Engineering", "Backend Engineer"));
    }

    @Test
    void entityNormalizesParticipantsAndProvidesHelpers() {
        Conversation conversation = Conversation.create(workspace, 23L, 10L);
        assertThat(conversation.getMemberLowId()).isEqualTo(10L);
        assertThat(conversation.getMemberHighId()).isEqualTo(23L);
        assertThat(conversation.hasParticipant(10L)).isTrue();
        assertThat(conversation.getOtherMemberId(10L)).isEqualTo(23L);
    }

    @Test
    void repositoryFindsReversedPairAndAllowsSamePairInDifferentWorkspace() {
        Conversation saved = conversationRepository.saveAndFlush(
                Conversation.create(workspace, currentMember.getId(), targetMember.getId()));
        assertThat(conversationRepository.findByWorkspaceIdAndMemberLowIdAndMemberHighId(
                workspace.getId(), saved.getMemberLowId(), saved.getMemberHighId())).contains(saved);

        Workspace other = workspaceRepository.save(Workspace.create(
                "Other Conversation Workspace", "CONV-US-0002", "RelAI", "US", List.of()));
        assertThat(conversationRepository.saveAndFlush(Conversation.create(
                other, currentMember.getId(), targetMember.getId())).getId()).isNotEqualTo(saved.getId());
    }

    @Test
    void databaseRejectsDuplicateConversation() {
        conversationRepository.saveAndFlush(Conversation.create(
                workspace, currentMember.getId(), targetMember.getId()));
        assertThatThrownBy(() -> conversationRepository.saveAndFlush(Conversation.create(
                workspace, targetMember.getId(), currentMember.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void messageStoresConversationAndLongSenderId() {
        Conversation conversation = conversationRepository.saveAndFlush(Conversation.create(
                workspace, currentMember.getId(), targetMember.getId()));
        Message message = messageRepository.saveAndFlush(Message.create(
                conversation, currentMember.getId(), "hello", null, false));
        assertThat(message.getConversation().getId()).isEqualTo(conversation.getId());
        assertThat(message.getSenderMemberId()).isEqualTo(currentMember.getId());
        assertThat(message.isTranslationUsed()).isFalse();
    }

    @Test
    void directConversationCreatesThenReturnsExistingFromEitherDirection() throws Exception {
        mockMvc.perform(post(url()).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"targetMemberId\":" + targetMember.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.targetMember.memberId").value(targetMember.getId()))
                .andExpect(jsonPath("$.data.targetMember.name").value("Target Member"))
                .andExpect(jsonPath("$.data.targetMember.companyName").value("RelAI"))
                .andExpect(jsonPath("$.data.targetMember.teamName").value("Engineering"))
                .andExpect(jsonPath("$.data.targetMember.jobTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$.data.targetMember.activityStatus").value("OFF"));

        Long id = conversationRepository.findAll().getFirst().getId();
        mockMvc.perform(post(url()).header("Authorization", bearer(targetUser))
                        .contentType("application/json")
                        .content("{\"targetMemberId\":" + currentMember.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(id))
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.targetMember.activityStatus").value("ACTIVE"));
    }

    @Test
    void directConversationRejectsSelfMissingAndSuspendedTarget() throws Exception {
        perform(currentUser, currentMember.getId()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400SELF_CONVERSATION_NOT_ALLOWED"));
        perform(currentUser, Long.MAX_VALUE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404MEMBER_NOT_FOUND"));
        targetMember.suspend();
        memberRepository.flush();
        perform(currentUser, targetMember.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404MEMBER_NOT_FOUND"));
    }

    @Test
    void directConversationRejectsNonMemberSuspendedRequesterAndMissingJwt() throws Exception {
        User outsider = saveUser("conversation-outsider@example.com", "Outsider", ActivityStatus.OFF);
        perform(outsider, targetMember.getId()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403WORKSPACE_ACCESS_DENIED"));
        currentMember.suspend();
        memberRepository.flush();
        perform(currentUser, targetMember.getId()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403WORKSPACE_ACCESS_DENIED"));
        mockMvc.perform(post(url()).contentType("application/json")
                        .content("{\"targetMemberId\":" + targetMember.getId() + "}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void directConversationHidesCrossWorkspaceTargetAndRejectsMissingUser() throws Exception {
        Workspace other = workspaceRepository.save(Workspace.create(
                "Cross Workspace", "CONV-JP-0003", "Partner", "JP", List.of()));
        WorkspaceMember crossTarget = memberRepository.save(WorkspaceMember.createInvitedMember(
                other, targetUser.getId().toString(), "Cross", targetUser.getEmail(),
                "Partner", null, null, WorkspaceRole.MEMBER));
        perform(currentUser, crossTarget.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404MEMBER_NOT_FOUND"));

        WorkspaceMember missingUser = memberRepository.save(WorkspaceMember.createInvitedMember(
                workspace, "999999999", "Missing", null, "RelAI", null, null, WorkspaceRole.MEMBER));
        perform(currentUser, missingUser.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404USER_NOT_FOUND"));
    }

    @Test
    void messagesReturnsNewestFirstWithDefaultPagingAndSenderNames() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        Message first = saveMessage(conversation, currentMember, "first", null, false);
        Message second = saveMessage(conversation, targetMember, "second", "두 번째", true);
        LocalDateTime sameTime = LocalDateTime.of(2026, 8, 16, 1, 0);
        setCreatedAt(first, sameTime);
        setCreatedAt(second, sameTime);

        mockMvc.perform(get(messagesUrl(conversation)).header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(conversation.getId()))
                .andExpect(jsonPath("$.data.messages[0].messageId").value(second.getId()))
                .andExpect(jsonPath("$.data.messages[0].senderMemberId").value(targetMember.getId()))
                .andExpect(jsonPath("$.data.messages[0].senderName").value("Target Member"))
                .andExpect(jsonPath("$.data.messages[0].originalContent").value("second"))
                .andExpect(jsonPath("$.data.messages[0].translatedContent").value("두 번째"))
                .andExpect(jsonPath("$.data.messages[0].translationUsed").value(true))
                .andExpect(jsonPath("$.data.messages[1].translatedContent")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void messagesSupportsCustomPagingAndEmptyConversation() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        saveMessage(conversation, currentMember, "one", null, false);
        saveMessage(conversation, currentMember, "two", null, false);
        saveMessage(conversation, currentMember, "three", null, false);

        mockMvc.perform(get(messagesUrl(conversation)).param("page", "0").param("size", "2")
                        .header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(get(messagesUrl(conversation)).param("page", "1").param("size", "2")
                        .header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        Conversation empty = conversationRepository.saveAndFlush(
                Conversation.create(workspace, currentMember.getId(), 999999L));
        mockMvc.perform(get(messagesUrl(empty)).header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void messagesEnforcesWorkspaceParticipantAndActiveMembership() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        User thirdUser = saveUser("conversation-third@example.com", "Third", ActivityStatus.ACTIVE);
        WorkspaceMember third = memberRepository.save(member(thirdUser, "Third", "Design", null));
        mockMvc.perform(get(messagesUrl(conversation)).header("Authorization", bearer(thirdUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403CONVERSATION_ACCESS_DENIED"));

        Workspace other = workspaceRepository.save(Workspace.create(
                "Message Other Workspace", "MSG-US-0002", "Other", "US", List.of()));
        Conversation otherConversation = conversationRepository.saveAndFlush(
                Conversation.create(other, currentMember.getId(), third.getId()));
        mockMvc.perform(get(messagesUrl(otherConversation)).header("Authorization", bearer(currentUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404CONVERSATION_NOT_FOUND"));

        currentMember.suspend();
        memberRepository.flush();
        mockMvc.perform(get(messagesUrl(conversation)).header("Authorization", bearer(currentUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void messagesReturnsNullSenderNameWhenWorkspaceMemberWasRemoved() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        saveMessage(conversation, targetMember, "legacy", null, false);
        memberRepository.delete(targetMember);
        memberRepository.flush();

        mockMvc.perform(get(messagesUrl(conversation)).header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].senderMemberId").value(targetMember.getId()))
                .andExpect(jsonPath("$.data.messages[0].senderName")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void recentConversationsUsesLastMessageAndReturnsBothLowAndHighTargets() throws Exception {
        Conversation withTarget = saveConversation(currentMember, targetMember);
        saveMessage(withTarget, currentMember, "target", null, false);

        User thirdUser = saveUser("conversation-recent-third@example.com", "Third User", ActivityStatus.ACTIVE);
        WorkspaceMember third = memberRepository.save(member(thirdUser, "Third Member", "Design", "Designer"));
        Conversation withThird = saveConversation(third, currentMember);
        Message latest = saveMessage(withThird, third, "latest", null, false);
        LocalDateTime earlier = LocalDateTime.of(2026, 8, 16, 1, 0);
        LocalDateTime later = earlier.plusHours(1);
        setCreatedAt(messageRepository.findAll().stream()
                .filter(message -> message.getConversation().getId().equals(withTarget.getId()))
                .findFirst().orElseThrow(), earlier);
        setCreatedAt(latest, later);
        conversationRepository.saveAndFlush(Conversation.create(
                workspace, currentMember.getId(), 888888L));

        mockMvc.perform(get(recentUrl()).header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations.length()").value(2))
                .andExpect(jsonPath("$.data.conversations[0].conversationId").value(withThird.getId()))
                .andExpect(jsonPath("$.data.conversations[0].targetMemberId").value(third.getId()))
                .andExpect(jsonPath("$.data.conversations[0].targetName").value("Third Member"))
                .andExpect(jsonPath("$.data.conversations[0].companyName").value("RelAI"))
                .andExpect(jsonPath("$.data.conversations[0].teamName").value("Design"))
                .andExpect(jsonPath("$.data.conversations[0].jobTitle").value("Designer"))
                .andExpect(jsonPath("$.data.conversations[0].activityStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.conversations[1].targetMemberId").value(targetMember.getId()))
                .andExpect(jsonPath("$.data.conversations[1].activityStatus").value("OFF"));
    }

    @Test
    void recentConversationsKeepsSuspendedTargetAndNullsRemovedTargetProfile() throws Exception {
        Conversation suspendedConversation = saveConversation(currentMember, targetMember);
        saveMessage(suspendedConversation, targetMember, "suspended", null, false);
        targetMember.suspend();
        memberRepository.flush();
        mockMvc.perform(get(recentUrl()).header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations[0].targetName").value("Target Member"))
                .andExpect(jsonPath("$.data.conversations[0].activityStatus").value("OFF"));

        memberRepository.delete(targetMember);
        memberRepository.flush();
        mockMvc.perform(get(recentUrl()).header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations[0].targetMemberId").value(targetMember.getId()))
                .andExpect(jsonPath("$.data.conversations[0].targetName")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.conversations[0].activityStatus")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void recentConversationsUsesConversationIdAsStableTieBreaker() throws Exception {
        Conversation lowerId = saveConversation(currentMember, targetMember);
        Message lowerMessage = saveMessage(lowerId, currentMember, "lower", null, false);
        User thirdUser = saveUser("conversation-tie@example.com", "Tie", ActivityStatus.ACTIVE);
        WorkspaceMember third = memberRepository.save(member(thirdUser, "Tie", "Design", null));
        Conversation higherId = saveConversation(currentMember, third);
        Message higherMessage = saveMessage(higherId, currentMember, "higher", null, false);
        LocalDateTime sameTime = LocalDateTime.of(2026, 8, 16, 3, 0);
        setCreatedAt(lowerMessage, sameTime);
        setCreatedAt(higherMessage, sameTime);

        mockMvc.perform(get(recentUrl()).header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations[0].conversationId").value(higherId.getId()))
                .andExpect(jsonPath("$.data.conversations[1].conversationId").value(lowerId.getId()));
    }

    @Test
    void conversationReadApisRequireJwtAndActiveWorkspaceMembership() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        mockMvc.perform(get(messagesUrl(conversation))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(recentUrl())).andExpect(status().isUnauthorized());

        User outsider = saveUser("conversation-read-outsider@example.com", "Outsider", ActivityStatus.OFF);
        mockMvc.perform(get(recentUrl()).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403WORKSPACE_ACCESS_DENIED"));

        currentMember.suspend();
        memberRepository.flush();
        mockMvc.perform(get(recentUrl()).header("Authorization", bearer(currentUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void messagesRejectsInvalidPaging() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        mockMvc.perform(get(messagesUrl(conversation)).param("page", "-1")
                        .header("Authorization", bearer(currentUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
        mockMvc.perform(get(messagesUrl(conversation)).param("size", "101")
                        .header("Authorization", bearer(currentUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
    }

    @Test
    void translationPreviewTranslatesUsingServerResolvedLanguages() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        currentUser.changeLanguage("ko");
        targetUser.changeLanguage("en");
        userRepository.flush();
        when(translationClient.translate("내일까지 확인해주세요.", "ko", "en"))
                .thenReturn(new TranslationResult("Could you review this by tomorrow?", "정중하게 조정했습니다."));

        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"content\":\"내일까지 확인해주세요.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("뉘앙스 번역이 완료되었습니다."))
                .andExpect(jsonPath("$.data.originalContent").value("내일까지 확인해주세요."))
                .andExpect(jsonPath("$.data.translatedContent").value("Could you review this by tomorrow?"))
                .andExpect(jsonPath("$.data.targetLanguage").value("en"))
                .andExpect(jsonPath("$.data.translationRequired").value(true))
                .andExpect(jsonPath("$.data.nuance").value("정중하게 조정했습니다."));
        verify(translationClient).translate("내일까지 확인해주세요.", "ko", "en");
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void translationPreviewSupportsEnToKoAndKoToJa() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        currentUser.changeLanguage("en");
        targetUser.changeLanguage("ko");
        userRepository.flush();
        when(translationClient.translate("Please review.", "en", "ko"))
                .thenReturn(new TranslationResult("검토 부탁드립니다.", "정중한 요청으로 조정했습니다."));
        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json").content("{\"content\":\"Please review.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetLanguage").value("ko"));

        reset(translationClient);
        currentUser.changeLanguage("ko");
        targetUser.changeLanguage("ja");
        userRepository.flush();
        when(translationClient.translate("확인해주세요.", "ko", "ja"))
                .thenReturn(new TranslationResult("ご確認ください。", "丁寧な表現に調整しました。"));
        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json").content("{\"content\":\"확인해주세요.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetLanguage").value("ja"));
    }

    @Test
    void translationPreviewSkipsAiForSameLanguage() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        currentUser.changeLanguage("ko");
        targetUser.changeLanguage("ko");
        userRepository.flush();

        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json").content("{\"content\":\"안녕하세요.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("동일한 언어를 사용하고 있습니다."))
                .andExpect(jsonPath("$.data.translatedContent").value("안녕하세요."))
                .andExpect(jsonPath("$.data.translationRequired").value(false))
                .andExpect(jsonPath("$.data.nuance").value(org.hamcrest.Matchers.nullValue()));
        verify(translationClient, never()).translate(anyString(), anyString(), anyString());
    }

    @Test
    void translationPreviewRequiresBothLanguagesAndValidContent() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        currentUser.changeLanguage("ko");
        userRepository.flush();
        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json").content("{\"content\":\"hello\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("422TRANSLATION_LANGUAGE_NOT_CONFIGURED"));
        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json").content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        currentUser.changeLanguage(null);
        targetUser.changeLanguage("en");
        userRepository.flush();
        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json").content("{\"content\":\"hello\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("422TRANSLATION_LANGUAGE_NOT_CONFIGURED"));
        String oversized = "a".repeat(4001);
        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json").content("{\"content\":\"" + oversized + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessageWithoutTranslationDoesNotRequireLanguagesOrCallAi() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"hello\",\"translationUsed\":false,"
                                + "\"senderMemberId\":999,\"translatedContent\":\"tampered\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("201CREATED"))
                .andExpect(jsonPath("$.data.senderMemberId").value(currentMember.getId()))
                .andExpect(jsonPath("$.data.translatedContent").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.translationUsed").value(false));
        verify(translationClient, never()).translate(anyString(), anyString(), anyString());
        assertThat(messageRepository.findAll().getFirst().getSenderMemberId()).isEqualTo(currentMember.getId());
    }

    @Test
    void sendMessageTranslatesAndStoresServerGeneratedResult() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        currentUser.changeLanguage("ko");
        targetUser.changeLanguage("en");
        userRepository.flush();
        when(translationClient.translate("검토해주세요.", "ko", "en"))
                .thenReturn(new TranslationResult("Could you review this?", "정중하게 조정했습니다."));

        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"검토해주세요.\",\"translationUsed\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalContent").value("검토해주세요."))
                .andExpect(jsonPath("$.data.translatedContent").value("Could you review this?"))
                .andExpect(jsonPath("$.data.translationUsed").value(true));
        Message stored = messageRepository.findAll().getFirst();
        assertThat(stored.getTranslatedContent()).isEqualTo("Could you review this?");
        assertThat(stored.isTranslationUsed()).isTrue();
    }

    @Test
    void sendMessageRequestedTranslationSkipsAiForSameLanguage() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        currentUser.changeLanguage("en");
        targetUser.changeLanguage("en");
        userRepository.flush();
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"hello\",\"translationUsed\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.translatedContent").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.translationUsed").value(false));
        verify(translationClient, never()).translate(anyString(), anyString(), anyString());
    }

    @Test
    void sendMessageDoesNotPersistOnAiFailureOrTimeout() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        currentUser.changeLanguage("ko");
        targetUser.changeLanguage("en");
        userRepository.flush();
        when(translationClient.translate(anyString(), anyString(), anyString()))
                .thenThrow(new CustomException(ErrorCode.AI_TRANSLATION_FAILED));
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"hello\",\"translationUsed\":true}"))
                .andExpect(status().isBadGateway());
        assertThat(messageRepository.count()).isZero();

        reset(translationClient);
        when(translationClient.translate(anyString(), anyString(), anyString()))
                .thenThrow(new CustomException(ErrorCode.AI_TRANSLATION_TIMEOUT));
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"hello\",\"translationUsed\":true}"))
                .andExpect(status().isGatewayTimeout());
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void temporalPreviewResolvesTimezoneAndRewritesEvenForSameLanguage() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        currentUser.changeLanguage("ko"); targetUser.changeLanguage("ko");
        currentUser.changeTimezone("Asia/Seoul"); targetUser.changeTimezone("America/Los_Angeles");
        userRepository.flush();
        when(temporalExpressionClient.extract("내일 오후 3시까지 확인해주세요.", "ko"))
                .thenReturn(new TemporalExtraction(true, List.of(new TemporalExpression(
                        "내일 오후 3시까지", Type.RELATIVE_DATE_TIME, RelativeDateType.TOMORROW,
                        null, null, "15:00", true, Role.DEADLINE))));
        when(translationClient.translate(anyString(), anyString(), anyString(), any()))
                .thenReturn(new TranslationResult("2026년 8월 16일 오후 11시까지 확인해주세요.", "수신자 현지 시간으로 명확히 했습니다."));

        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json").content("{\"content\":\"내일 오후 3시까지 확인해주세요.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.translationRequired").value(true))
                .andExpect(jsonPath("$.data.translatedContent").value("2026년 8월 16일 오후 11시까지 확인해주세요."));
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void actualTemporalExpressionRequiresBothTimezonesAndDoesNotSaveMessage() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        currentUser.changeLanguage("ko"); targetUser.changeLanguage("en"); userRepository.flush();
        when(temporalExpressionClient.extract(anyString(), anyString())).thenReturn(new TemporalExtraction(true,
                List.of(new TemporalExpression("내일", Type.RELATIVE_DATE, RelativeDateType.TOMORROW,
                        null, null, null, false, Role.DEADLINE))));
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"내일까지 확인해주세요.\",\"translationUsed\":true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("422TEMPORAL_CONTEXT_NOT_CONFIGURED"));
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void previewAndSendRejectInactiveOrRemovedTarget() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        targetMember.suspend();
        memberRepository.flush();
        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json").content("{\"content\":\"hello\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("404MEMBER_NOT_FOUND"));
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"hello\",\"translationUsed\":false}"))
                .andExpect(status().isNotFound());

        memberRepository.delete(targetMember);
        memberRepository.flush();
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"hello\",\"translationUsed\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void translationEndpointsEnforceJwtWorkspaceAndParticipantAccess() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        mockMvc.perform(post(previewUrl(conversation)).contentType("application/json")
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(messagesUrl(conversation)).contentType("application/json")
                        .content("{\"originalContent\":\"hello\",\"translationUsed\":false}"))
                .andExpect(status().isUnauthorized());

        User thirdUser = saveUser("conversation-translation-third@example.com", "Third", ActivityStatus.ACTIVE);
        memberRepository.save(member(thirdUser, "Third", "Design", null));
        mockMvc.perform(post(previewUrl(conversation)).header("Authorization", bearer(thirdUser))
                        .contentType("application/json").content("{\"content\":\"hello\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403CONVERSATION_ACCESS_DENIED"));

        Workspace other = workspaceRepository.save(Workspace.create(
                "Translation Other Workspace", "TRANS-US-0099", "Other", "US", List.of()));
        Conversation otherConversation = conversationRepository.saveAndFlush(
                Conversation.create(other, currentMember.getId(), targetMember.getId()));
        mockMvc.perform(post(previewUrl(otherConversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json").content("{\"content\":\"hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404CONVERSATION_NOT_FOUND"));

        currentMember.suspend();
        memberRepository.flush();
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"hello\",\"translationUsed\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void sendMessageValidatesContentAndTranslationFlag() throws Exception {
        Conversation conversation = saveConversation(currentMember, targetMember);
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"   \",\"translationUsed\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"hello\"}"))
                .andExpect(status().isBadRequest());
        String oversized = "a".repeat(4001);
        mockMvc.perform(post(messagesUrl(conversation)).header("Authorization", bearer(currentUser))
                        .contentType("application/json")
                        .content("{\"originalContent\":\"" + oversized
                                + "\",\"translationUsed\":false}"))
                .andExpect(status().isBadRequest());
        assertThat(messageRepository.count()).isZero();
    }

    private User saveUser(String email, String name, ActivityStatus status) {
        User user = User.create(email, passwordEncoder.encode("password123!"), name);
        user.changeActivityStatus(status);
        return userRepository.save(user);
    }

    private WorkspaceMember member(User user, String name, String teamName, String jobTitle) {
        return WorkspaceMember.createInvitedMember(workspace, user.getId().toString(), name,
                user.getEmail(), "RelAI", teamName, jobTitle, WorkspaceRole.MEMBER);
    }

    private Conversation saveConversation(WorkspaceMember first, WorkspaceMember second) {
        return conversationRepository.saveAndFlush(
                Conversation.create(workspace, first.getId(), second.getId()));
    }

    private Message saveMessage(Conversation conversation, WorkspaceMember sender, String original,
                                String translated, boolean translationUsed) {
        return messageRepository.saveAndFlush(
                Message.create(conversation, sender.getId(), original, translated, translationUsed));
    }

    private void setCreatedAt(Message message, LocalDateTime createdAt) {
        jdbcTemplate.update("update messages set created_at = ? where id = ?",
                Timestamp.valueOf(createdAt), message.getId());
        entityManager.clear();
    }

    private org.springframework.test.web.servlet.ResultActions perform(User user, Long targetId) throws Exception {
        return mockMvc.perform(post(url()).header("Authorization", bearer(user))
                .contentType("application/json").content("{\"targetMemberId\":" + targetId + "}"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    private String url() {
        return "/api/v1/workspaces/" + workspace.getId() + "/conversations/direct";
    }

    private String messagesUrl(Conversation conversation) {
        return "/api/v1/workspaces/" + workspace.getId()
                + "/conversations/" + conversation.getId() + "/messages";
    }

    private String recentUrl() {
        return "/api/v1/workspaces/" + workspace.getId() + "/conversations/recent";
    }

    private String previewUrl(Conversation conversation) {
        return "/api/v1/workspaces/" + workspace.getId()
                + "/conversations/" + conversation.getId() + "/translation-preview";
    }
}
