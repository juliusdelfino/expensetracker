package com.delfino.expensetracker.config;

import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * Static helper: extract the authenticated userId from the SecurityContext.
     * Returns null if not authenticated.
     */
    public static Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
    }
}
