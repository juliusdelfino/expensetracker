package com.delfino.expensetracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;


@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true, nullable = false)
    @Size(max = 100)
    private String username;

    private String passwordHash;

    @Size(max = 100)
    private String email;

    @Size(max = 30)
    private String phoneNumber;

    @Size(max = 3)
    private String baseCurrency;

    @Size(max = 100)
    private String baseCity;

    @Size(max = 2)
    private String baseCountry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private UserRole role = UserRole.USER;

    @Size(max = 255)
    @Column(name = "ai_model")
    private String aiModel;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }

    public String getBaseCity() { return baseCity; }
    public void setBaseCity(String baseCity) { this.baseCity = baseCity; }

    public String getBaseCountry() { return baseCountry; }
    public void setBaseCountry(String baseCountry) { this.baseCountry = baseCountry; }

    public UserRole getRole() { return role != null ? role : UserRole.USER; }
    public void setRole(UserRole role) { this.role = role != null ? role : UserRole.USER; }

    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) {
        this.aiModel = aiModel == null || aiModel.isBlank() ? null : aiModel.trim();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    @PreUpdate
    void applyDefaults() {
        if (role == null) {
            role = UserRole.USER;
        }
        if (aiModel != null && aiModel.isBlank()) {
            aiModel = null;
        }
    }
}
