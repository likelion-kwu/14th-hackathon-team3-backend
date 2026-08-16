package com.example.likelionhackathon.domain.conversation.repository;

import com.example.likelionhackathon.domain.conversation.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Slice<Message> findByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId, Pageable pageable);
}
