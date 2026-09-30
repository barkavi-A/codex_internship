package com.example.urlshortener.repository;

import com.example.urlshortener.dto.projection.LinkStatsProjection;
import com.example.urlshortener.entity.DailyLinkStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyLinkStatsRepository extends JpaRepository<DailyLinkStats, Long> {

    Optional<DailyLinkStats> findByLinkIdAndStatDate(Long linkId, LocalDate statDate);

    @Query("SELECT SUM(d.clicks) AS totalClicks, SUM(d.uniqueVisitors) AS uniqueVisitors, SUM(d.botClicks) AS botClicks " +
           "FROM DailyLinkStats d WHERE d.link.id = :linkId AND d.statDate >= :from AND d.statDate <= :to")
    LinkStatsProjection getRolledUpLinkStats(@Param("linkId") Long linkId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    @Query("SELECT SUM(d.clicks) AS totalClicks, SUM(d.uniqueVisitors) AS uniqueVisitors, SUM(d.botClicks) AS botClicks " +
           "FROM DailyLinkStats d WHERE d.link.user.id = :userId AND d.statDate >= :from AND d.statDate <= :to")
    LinkStatsProjection getRolledUpUserStats(@Param("userId") Long userId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    List<DailyLinkStats> findByLinkIdAndStatDateBetweenOrderByStatDateAsc(Long linkId, LocalDate from, LocalDate to);
}
