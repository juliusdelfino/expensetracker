package com.delfino.expensetracker.config;

import com.delfino.expensetracker.model.UserRole;
import com.delfino.expensetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;

    @Value("${app.admin.bootstrap-username:}")
    private String bootstrapUsername;

    public AdminBootstrapRunner(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapUsername == null || bootstrapUsername.isBlank()) {
            return;
        }

        String normalizedUsername = bootstrapUsername.trim();
        userRepository.findByUsernameIgnoreCase(normalizedUsername).ifPresentOrElse(user -> {
            if (user.getRole() == UserRole.ADMIN) {
                log.info("Bootstrap admin user '{}' already has ADMIN role", user.getUsername());
                return;
            }
            user.setRole(UserRole.ADMIN);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("Granted ADMIN role to bootstrap user '{}'", user.getUsername());
        }, () -> log.warn("Bootstrap admin username '{}' was configured but no matching user exists", normalizedUsername));
    }
}

