package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.AiUsage;
import com.delfino.expensetracker.model.AiUsageType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

    Optional<AiUsage> findByUserIdAndTypeAndMonthYear(long userId, AiUsageType type, String monthYear);

    List<AiUsage> findByUserIdAndMonthYear(long userId, String monthYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AiUsage a where a.userId = :userId and a.type = :type and a.monthYear = :monthYear")
    Optional<AiUsage> findForUpdate(@Param("userId") long userId,
                                    @Param("type") AiUsageType type,
                                    @Param("monthYear") String monthYear);

    void deleteByUserId(long userId);
}


