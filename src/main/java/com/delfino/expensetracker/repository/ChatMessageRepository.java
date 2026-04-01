package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<ChatMessage> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT m FROM ChatMessage m WHERE m.userId = :userId ORDER BY m.createdAt DESC")
    List<ChatMessage> findByUserIdPageable(@Param("userId") UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
