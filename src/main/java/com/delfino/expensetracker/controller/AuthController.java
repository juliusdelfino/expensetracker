package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.auth.UserToken;
import com.delfino.expensetracker.dto.auth.LoginRequest;
import com.delfino.expensetracker.dto.auth.LoginResponse;
import com.delfino.expensetracker.dto.auth.RegisterRequest;
import com.delfino.expensetracker.dto.auth.RegisterResponse;
import com.delfino.expensetracker.dto.auth.UserProfileResponse;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.dto.common.MessageResponse;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.model.UserRole;
import com.delfino.expensetracker.repository.UserRepository;
import com.delfino.expensetracker.service.I18nService;
import com.delfino.expensetracker.service.SupportedCurrencyService;
import com.delfino.expensetracker.util.LocaleSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
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
    private final I18nService i18nService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          SupportedCurrencyService supportedCurrencyService, I18nService i18nService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.supportedCurrencyService = supportedCurrencyService;
        this.i18nService = i18nService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest body) {
        String username = body.username();
        String password = body.password();
        String email = body.email();
        String phone = body.phoneNumber();
        String baseCurrency = body.baseCurrency() != null ? body.baseCurrency() : "USD";
        String locale = LocaleSupport.normalizeLanguageTag(body.locale());

        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(
                    i18nService.message("auth.error.username_password_required", "Username and password required"),
                    "INVALID_REQUEST"));
        }
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(
                    i18nService.message("auth.error.username_exists", "Username already exists"),
                    "USERNAME_EXISTS"));
        }

        if (!baseCurrency.isBlank() && !supportedCurrencyService.isSupported(baseCurrency)) {
            return ResponseEntity.badRequest().body(new ErrorResponse(
                    i18nService.message("auth.error.unsupported_base_currency", "Unsupported base currency: {0}", baseCurrency),
                    "UNSUPPORTED_BASE_CURRENCY"));
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setBaseCurrency(baseCurrency);
        user.setLocale(locale);
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(new RegisterResponse(
                i18nService.message("auth.register.success", "Registration successful"),
                user.getId()));
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
                            i18nService.message("auth.login.success", "Login successful"),
                            u.getId(),
                            u.getUsername(),
                            u.getBaseCurrency()
                    ));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
                        i18nService.message("auth.error.invalid_credentials", "Invalid credentials"),
                        "INVALID_CREDENTIALS")));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(new MessageResponse(i18nService.message("auth.logout.success", "Logged out")));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> me(UserToken user) {
        return userRepository.findById(user.getUserId())
                .map(u -> ResponseEntity.ok((Object) new UserProfileResponse(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getPhoneNumber(),
                        u.getBaseCurrency(),
                        u.getBaseCity(),
                        u.getBaseCountry(),
                        u.getLocale(),
                        u.getRole(),
                        u.getAiModel()
                )))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
                        i18nService.message("auth.error.user_not_found", "User not found"),
                        "USER_NOT_FOUND")));
    }
}
