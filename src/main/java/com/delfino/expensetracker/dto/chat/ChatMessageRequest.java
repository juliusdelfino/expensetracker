package com.delfino.expensetracker.dto.chat;

import jakarta.validation.constraints.Size;

public record ChatMessageRequest(@Size(max = 500) String message) {}
