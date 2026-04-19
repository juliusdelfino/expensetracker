package com.delfino.expensetracker.config;

import com.delfino.expensetracker.dto.auth.UserContext;
import com.delfino.expensetracker.dto.auth.UserToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the userId from the HttpSession and populates the SecurityContext
 * so that @PreAuthorize("isAuthenticated()") works for session-based auth.
 * Also sets the request-scoped UserContext for @Tool services.
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final UserContext userContext;

    public SessionAuthenticationFilter(UserContext userContext) {
        this.userContext = userContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = (Long) session.getAttribute("userId");
            if (userId != null) {
                var auth = new UserToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
                userContext.setUserId(userId);
            }
        }
        filterChain.doFilter(request, response);
    }
}

