package com.delfino.expensetracker.service.mcp;

import com.delfino.expensetracker.config.UserContext;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;


/**
 * Provides @Tool-annotated methods for the LLM to view and modify
 * the authenticated user's own profile. Password changes are excluded
 * for security reasons.
 */
@Service
public class ProfileToolService {

    private static final Logger log = LoggerFactory.getLogger(ProfileToolService.class);

    private final UserRepository userRepository;
    private final UserContext userContext;

    public ProfileToolService(UserRepository userRepository, UserContext userContext) {
        this.userRepository = userRepository;
        this.userContext = userContext;
    }

    @Tool(description = "Get the current user's profile information including email, phone, base currency, base city, and base country. " +
            "Use this when the user asks about their own profile settings.")
    public String getProfile() {
        log.info("Tool call: getProfile(userId={})", userContext.getUserId());
        User user = userRepository.findById(userContext.getUserId()).orElse(null);
        if (user == null) return "User not found.";

        return "Your profile:\n"
                + "- Username: " + user.getUsername() + "\n"
                + "- Email: " + orNotSet(user.getEmail()) + "\n"
                + "- Phone: " + orNotSet(user.getPhoneNumber()) + "\n"
                + "- Base Currency: " + orNotSet(user.getBaseCurrency()) + "\n"
                + "- Base City: " + orNotSet(user.getBaseCity()) + "\n"
                + "- Base Country: " + orNotSet(user.getBaseCountry()) + "\n";
    }

    @Tool(description = "Update the current user's profile. Only updates fields that are provided (non-empty). " +
            "Can update email, phone number, base currency, base city, and base country. " +
            "Password changes are not supported through chat for security reasons.")
    public String updateProfile(
            @ToolParam(description = "New email address. Pass empty string to skip.") String email,
            @ToolParam(description = "New phone number. Pass empty string to skip.") String phoneNumber,
            @ToolParam(description = "New base currency code, e.g. 'USD', 'SGD'. Pass empty string to skip.") String baseCurrency,
            @ToolParam(description = "New base city, e.g. 'Singapore'. Pass empty string to skip.") String baseCity,
            @ToolParam(description = "New base country code, e.g. 'SG'. Pass empty string to skip.") String baseCountry) {

        log.info("Tool call: updateProfile(userId={})", userContext.getUserId());
        User user = userRepository.findById(userContext.getUserId()).orElse(null);
        if (user == null) return "User not found.";

        StringBuilder changes = new StringBuilder();
        if (StringUtils.hasText(email)) { user.setEmail(email); changes.append("- Email → ").append(email).append("\n"); }
        if (StringUtils.hasText(phoneNumber)) { user.setPhoneNumber(phoneNumber); changes.append("- Phone → ").append(phoneNumber).append("\n"); }
        if (StringUtils.hasText(baseCurrency)) { user.setBaseCurrency(baseCurrency.toUpperCase()); changes.append("- Base Currency → ").append(baseCurrency.toUpperCase()).append("\n"); }
        if (StringUtils.hasText(baseCity)) { user.setBaseCity(baseCity); changes.append("- Base City → ").append(baseCity).append("\n"); }
        if (StringUtils.hasText(baseCountry)) { user.setBaseCountry(baseCountry.toUpperCase()); changes.append("- Base Country → ").append(baseCountry.toUpperCase()).append("\n"); }

        if (changes.isEmpty()) return "No changes specified.";

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return "Profile updated:\n" + changes;
    }

    private static String orNotSet(String value) {
        return StringUtils.hasText(value) ? value : "not set";
    }
}
