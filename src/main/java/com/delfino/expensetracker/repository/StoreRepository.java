package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByUserId(Long userId);

    @Query("SELECT s FROM Store s WHERE s.userId = :userId AND " +
           "LOWER(COALESCE(s.name,'')) = LOWER(COALESCE(:name,'')) AND " +
           "LOWER(COALESCE(s.address,'')) = LOWER(COALESCE(:address,'')) AND " +
           "LOWER(COALESCE(s.city,'')) = LOWER(COALESCE(:city,'')) AND " +
           "LOWER(COALESCE(s.country,'')) = LOWER(COALESCE(:country,'')) AND " +
           "LOWER(COALESCE(s.postalCode,'')) = LOWER(COALESCE(:postalCode,''))")
    Optional<Store> findMatchingStore(@Param("userId") Long userId,
                                      @Param("name") String name,
                                      @Param("address") String address,
                                      @Param("city") String city,
                                      @Param("country") String country,
                                      @Param("postalCode") String postalCode);

    @Query("SELECT s FROM Store s WHERE s.latitude IS NULL OR s.longitude IS NULL")
    List<Store> findStoresWithoutCoordinates();
}
