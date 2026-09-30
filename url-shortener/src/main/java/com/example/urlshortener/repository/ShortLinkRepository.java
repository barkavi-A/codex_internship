package com.example.urlshortener.repository;

import com.example.urlshortener.entity.ShortLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByShortCode(String shortCode);

    Optional<ShortLink> findByIdAndUserId(Long id, Long userId);

    Optional<ShortLink> findByUserIdAndOriginalUrlAndCustomAliasFalse(Long userId, String originalUrl);

    boolean existsByShortCode(String shortCode);

    @Modifying
    @Query("UPDATE ShortLink s SET s.clickCount = s.clickCount + 1 WHERE s.id = :id AND (s.maxClicks IS NULL OR s.clickCount < s.maxClicks)")
    int incrementClickCountIfUnderLimit(@Param("id") Long id);

    @Query("SELECT s FROM ShortLink s WHERE s.user.id = :userId " +
            "AND (:search IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.originalUrl) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.shortCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:active IS NULL OR s.active = :active) " +
            "AND (:expired IS NULL OR (:expired = true AND s.expiresAt IS NOT NULL AND s.expiresAt <= :now) OR (:expired = false AND (s.expiresAt IS NULL OR s.expiresAt > :now)))")
    Page<ShortLink> findUserLinks(@Param("userId") Long userId,
                                  @Param("search") String search,
                                  @Param("active") Boolean active,
                                  @Param("expired") Boolean expired,
                                  @Param("now") LocalDateTime now,
                                  Pageable pageable);

    @Query("SELECT COUNT(s) FROM ShortLink s WHERE s.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM ShortLink s WHERE s.user.id = :userId AND s.active = true AND (s.expiresAt IS NULL OR s.expiresAt > :now)")
    long countActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
