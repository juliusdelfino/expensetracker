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
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> body, HttpSession session) {
        UUID userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if (body.containsKey("email")) user.setEmail(body.get("email"));
        if (body.containsKey("phoneNumber")) user.setPhoneNumber(body.get("phoneNumber"));
        if (body.containsKey("baseCurrency")) user.setBaseCurrency(body.get("baseCurrency"));
        if (body.containsKey("baseCity")) user.setBaseCity(body.get("baseCity"));
        if (body.containsKey("baseCountry")) user.setBaseCountry(body.get("baseCountry"));
        if (body.containsKey("password") && !body.get("password").isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(body.get("password")));
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Profile updated"));
    }

    private UUID getUserId(HttpSession session) {
        String id = (String) session.getAttribute("userId");
        return id != null ? UUID.fromString(id) : null;
    }
}

