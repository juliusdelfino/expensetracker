package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Report> findByIdAndUserId(Long id, Long userId);
}

