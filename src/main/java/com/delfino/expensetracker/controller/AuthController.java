package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.config.UserContext;
import com.delfino.expensetracker.dto.auth.LoginRequest;
import com.delfino.expensetracker.dto.auth.LoginResponse;
import com.delfino.expensetracker.dto.auth.RegisterRequest;
import com.delfino.expensetracker.dto.auth.RegisterResponse;
import com.delfino.expensetracker.dto.auth.UserProfileResponse;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.dto.common.MessageResponse;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.UserRepository;
import com.delfino.expensetracker.service.SupportedCurrencyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupportedCurrencyService supportedCurrencyService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, SupportedCurrencyService supportedCurrencyService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.supportedCurrencyService = supportedCurrencyService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest body) {
        String username = body.username();
        String password = body.password();
        String email = body.email();
        String phone = body.phoneNumber();
        String baseCurrency = body.baseCurrency() != null ? body.baseCurrency() : "USD";

        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Username and password required"));
        }
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Username already exists"));
        }

        if (!baseCurrency.isBlank() && !supportedCurrencyService.isSupported(baseCurrency)) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Unsupported base currency: " + baseCurrency));
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setBaseCurrency(baseCurrency);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(new RegisterResponse("Registration successful", user.getId()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body, HttpSession session) {
        String username = body.username();
        String password = body.password();

        return userRepository.findByUsernameIgnoreCase(username)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .map(u -> {
                    session.setAttribute("userId", u.getId());
                    return ResponseEntity.ok((Object) new LoginResponse(
                            "Login successful",
                            u.getId(),
                            u.getUsername(),
                            u.getBaseCurrency() != null ? u.getBaseCurrency() : "USD"
                    ));
                })
                .orElse(ResponseEntity.status(401).body(new ErrorResponse("Invalid credentials")));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(new MessageResponse("Logged out"));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> me() {
        Long userId = UserContext.currentUserId();
        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok((Object) new UserProfileResponse(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getPhoneNumber(),
                        u.getBaseCurrency(),
                        u.getBaseCity(),
                        u.getBaseCountry()
                )))
                .orElse(ResponseEntity.status(401).body(new ErrorResponse("User not found")));
    }
}
