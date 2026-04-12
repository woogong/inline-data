package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.EventResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventResultRepository extends JpaRepository<EventResult, Long> {

    Optional<EventResult> findByHeatEntryId(Long heatEntryId);

    @Query("SELECT COUNT(er) FROM EventResult er WHERE er.heatEntry.heat.id IN :heatIds")
    long countByHeatIds(@Param("heatIds") List<Long> heatIds);

    @Query("SELECT er FROM EventResult er " +
           "JOIN FETCH er.heatEntry he " +
           "JOIN FETCH he.entry ce " +
           "LEFT JOIN FETCH ce.athlete " +
           "LEFT JOIN FETCH ce.team " +
           "WHERE he.heat.id IN :heatIds " +
           "ORDER BY he.heat.id, er.ranking ASC NULLS LAST")
    List<EventResult> findByHeatIdsWithDetails(@Param("heatIds") List<Long> heatIds);

    @Query("SELECT er FROM EventResult er " +
           "JOIN FETCH er.heatEntry he " +
           "JOIN FETCH he.entry ce " +
           "LEFT JOIN FETCH ce.athlete " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound r " +
           "JOIN r.event e " +
           "WHERE e.competition.id = :compId " +
           "AND r.round = '결승' " +
           "AND er.ranking IN (1, 2, 3) " +
           "ORDER BY e.id, er.ranking")
    List<EventResult> findMedalResultsByCompetitionId(@Param("compId") Long competitionId);

    @Query("SELECT er FROM EventResult er " +
           "JOIN FETCH er.heatEntry he " +
           "JOIN FETCH he.entry ce " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound r " +
           "JOIN r.event e " +
           "WHERE e.competition.id = :compId " +
           "AND r.round = '결승' " +
           "AND er.ranking BETWEEN 1 AND 6 " +
           "ORDER BY e.id, er.ranking")
    List<EventResult> findScoringResultsByCompetitionId(@Param("compId") Long competitionId);

    @Query("SELECT er FROM EventResult er " +
           "JOIN FETCH er.heatEntry he " +
           "JOIN FETCH he.entry ce " +
           "LEFT JOIN FETCH ce.athlete " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound r " +
           "JOIN r.event e " +
           "WHERE e.competition.id = :compId " +
           "AND er.newRecord IS NOT NULL " +
           "ORDER BY er.newRecord, e.divisionName, e.eventName, r.round")
    List<EventResult> findNewRecordsByCompetitionId(@Param("compId") Long competitionId);

    @Query("SELECT er FROM EventResult er " +
           "JOIN FETCH er.heatEntry he " +
           "JOIN FETCH he.entry ce " +
           "JOIN FETCH ce.competition c " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound r " +
           "JOIN r.event e " +
           "WHERE ce.athleteName = :athleteName " +
           "AND r.round = '결승' " +
           "AND er.ranking IN (1, 2, 3) " +
           "ORDER BY c.startDate DESC, e.divisionName, e.eventName")
    List<EventResult> findMedalsByAthleteName(@Param("athleteName") String athleteName);

    @Query("SELECT er FROM EventResult er " +
           "JOIN FETCH er.heatEntry he " +
           "JOIN FETCH he.entry ce " +
           "JOIN FETCH ce.competition c " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound r " +
           "JOIN r.event e " +
           "WHERE r.round = '결승' " +
           "AND er.ranking IN (1, 2, 3) " +
           "ORDER BY c.startDate, e.id, er.ranking")
    List<EventResult> findAllMedalResults();
}
