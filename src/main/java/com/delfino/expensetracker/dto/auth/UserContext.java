package com.delfino.expensetracker.dto.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped bean that carries the authenticated userId
 * from the controller layer into Spring AI @Tool methods
 * (which don't have direct access to HttpSession).
 */
@Component
@RequestScope
public class UserContext {

    private Long userId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
