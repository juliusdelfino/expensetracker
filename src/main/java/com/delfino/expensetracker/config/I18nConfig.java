package com.delfino.expensetracker.config;

import com.delfino.expensetracker.repository.UserRepository;
import com.delfino.expensetracker.util.LocaleSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver(UserRepository userRepository) {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                String lang = request.getParameter("lang");
                if (lang != null && !lang.isBlank()) {
                    return LocaleSupport.normalizeToSupportedLocale(lang);
                }

                HttpSession session = request.getSession(false);
                if (session != null) {
                    Object userIdValue = session.getAttribute("userId");
                    if (userIdValue instanceof Long userId) {
                        return userRepository.findById(userId)
                                .map(user -> LocaleSupport.normalizeToSupportedLocale(user.getLocale()))
                                .orElse(LocaleSupport.DEFAULT_LOCALE);
                    }
                }

                Locale requestLocale = request.getLocale();
                if (requestLocale != null) {
                    return LocaleSupport.normalizeToSupportedLocale(requestLocale.toLanguageTag());
                }
                return LocaleSupport.DEFAULT_LOCALE;
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                // Locale is resolved from request/user preference; not mutated here.
            }
        };
    }
}

