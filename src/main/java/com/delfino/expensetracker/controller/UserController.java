package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.config.UserContext;
import com.delfino.expensetracker.dto.auth.UpdateProfileRequest;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.dto.common.MessageResponse;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.UserRepository;
import com.delfino.expensetracker.service.SupportedCurrencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupportedCurrencyService supportedCurrencyService;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, SupportedCurrencyService supportedCurrencyService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.supportedCurrencyService = supportedCurrencyService;
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest body) {
        Long userId = UserContext.currentUserId();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if (body.email() != null) user.setEmail(body.email());
        if (body.phoneNumber() != null) user.setPhoneNumber(body.phoneNumber());
        if (body.baseCurrency() != null) {
            String bc = body.baseCurrency();
            if (!bc.isBlank() && !supportedCurrencyService.isSupported(bc)) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Unsupported base currency: " + bc));
            }
            user.setBaseCurrency(bc);
        }
        if (body.baseCity() != null) user.setBaseCity(body.baseCity());
        if (body.baseCountry() != null) user.setBaseCountry(body.baseCountry());
        if (body.password() != null && !body.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(body.password()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Profile updated"));
    }
}
