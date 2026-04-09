package com.delfino.expensetracker.config;

import com.delfino.expensetracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserRepository userRepository;
    private final SessionAuthenticationFilter sessionAuthenticationFilter;

    public SecurityConfig(UserRepository userRepository, SessionAuthenticationFilter sessionAuthenticationFilter) {
        this.userRepository = userRepository;
        this.sessionAuthenticationFilter = sessionAuthenticationFilter;
    }

    /**
     * Expose a UserDetailsService backed by our user store.
     * Spring Security will use this to verify credentials on login,
     * but our controllers handle session manually via HttpSession.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsernameIgnoreCase(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            )
            .csrf(AbstractHttpConfigurer::disable)

            // Register session-based auth filter before UsernamePasswordAuthenticationFilter
            .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // All requests are allowed through; @PreAuthorize on individual methods enforces auth.
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )

            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // Return 401 JSON for unauthenticated requests blocked by @PreAuthorize
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Not authenticated\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied\"}");
                })
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
