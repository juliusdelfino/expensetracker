package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String email = body.get("email");
        String phone = body.get("phoneNumber");
        String baseCurrency = body.getOrDefault("baseCurrency", "USD");

        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password required"));
        }
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setBaseCurrency(baseCurrency);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Registration successful", "userId", user.getId().toString()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.get("username");
        String password = body.get("password");

        return userRepository.findByUsernameIgnoreCase(username)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .map(u -> {
                    session.setAttribute("userId", u.getId().toString());
                    return ResponseEntity.ok(Map.of(
                            "message", "Login successful",
                            "userId", u.getId().toString(),
                            "username", u.getUsername(),
                            "baseCurrency", u.getBaseCurrency() != null ? u.getBaseCurrency() : "USD"
                    ));
                })
                .orElse(ResponseEntity.status(401).body(Map.of("error", "Invalid credentials")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        return userRepository.findById(UUID.fromString(userId))
                .map(u -> {
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("id", u.getId().toString());
                    result.put("username", u.getUsername());
                    result.put("email", u.getEmail());
                    result.put("phoneNumber", u.getPhoneNumber());
                    result.put("baseCurrency", u.getBaseCurrency());
                    result.put("baseCity", u.getBaseCity());
                    result.put("baseCountry", u.getBaseCountry());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.status(401).body(Map.of("error", "User not found")));
    }
}

