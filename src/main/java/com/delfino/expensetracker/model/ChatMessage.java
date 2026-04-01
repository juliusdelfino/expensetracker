package com.delfino.expensetracker.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    private UUID id;

    private UUID userId;
    private String role; // "USER" or "BOT"

    @Column(length = 4000)
    private String text;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chat_message_linked_expenses", joinColumns = @JoinColumn(name = "chat_message_id"))
    @Column(name = "expense_id")
    private List<UUID> linkedExpenseIds = new ArrayList<>();

    private LocalDateTime createdAt;

    public ChatMessage() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<UUID> getLinkedExpenseIds() { return linkedExpenseIds; }
    public void setLinkedExpenseIds(List<UUID> linkedExpenseIds) { this.linkedExpenseIds = linkedExpenseIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
