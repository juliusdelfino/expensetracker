package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.ShareAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ShareAccessLogRepository extends JpaRepository<ShareAccessLog, Long> {

    long deleteByAccessedAtBefore(LocalDateTime cutoff);
}

