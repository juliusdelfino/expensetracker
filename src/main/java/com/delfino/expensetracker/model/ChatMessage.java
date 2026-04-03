package com.delfino.expensetracker.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private long userId;
    private String role; // "USER" or "BOT"

    @Column(length = 4000)
    private String text;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chat_message_linked_expenses", joinColumns = @JoinColumn(name = "chat_message_id"))
    @Column(name = "expense_id")
    private List<Long> linkedExpenseIds = new ArrayList<>(); // store original Long strings

    private LocalDateTime createdAt;

    public ChatMessage() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<Long> getLinkedExpenseIds() { return linkedExpenseIds; }
    public void setLinkedExpenseIds(List<Long> linkedExpenseIds) { this.linkedExpenseIds = linkedExpenseIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
