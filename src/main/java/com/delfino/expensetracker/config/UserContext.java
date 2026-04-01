package com.delfino.expensetracker.config;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.UUID;

/**
 * Request-scoped bean that carries the authenticated userId
 * from the controller layer into Spring AI @Tool methods
 * (which don't have direct access to HttpSession).
 */
@Component
@RequestScope
public class UserContext {

    private UUID userId;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}

