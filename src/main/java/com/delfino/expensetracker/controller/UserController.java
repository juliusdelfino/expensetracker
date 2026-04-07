package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.UserRepository;
import com.delfino.expensetracker.service.SupportedCurrencyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;


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
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if (body.containsKey("email")) user.setEmail(body.get("email"));
        if (body.containsKey("phoneNumber")) user.setPhoneNumber(body.get("phoneNumber"));
        if (body.containsKey("baseCurrency")) {
            String bc = body.get("baseCurrency");
            if (bc != null && !bc.isBlank() && !supportedCurrencyService.isSupported(bc)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unsupported base currency: " + bc));
            }
            user.setBaseCurrency(bc);
        }
        if (body.containsKey("baseCity")) user.setBaseCity(body.get("baseCity"));
        if (body.containsKey("baseCountry")) user.setBaseCountry(body.get("baseCountry"));
        if (body.containsKey("password") && !body.get("password").isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(body.get("password")));
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Profile updated"));
    }

    private Long getUserId(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }
}
