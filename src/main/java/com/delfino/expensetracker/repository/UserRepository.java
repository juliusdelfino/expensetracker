package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.UserRole;
import com.delfino.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByRole(UserRole role);

    long countByRole(UserRole role);
}
