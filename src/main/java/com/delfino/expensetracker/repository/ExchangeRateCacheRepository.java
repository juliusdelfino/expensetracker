package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.ExchangeRateCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExchangeRateCacheRepository extends JpaRepository<ExchangeRateCache, String> {

    Optional<ExchangeRateCache> findByKey(String key);
}
