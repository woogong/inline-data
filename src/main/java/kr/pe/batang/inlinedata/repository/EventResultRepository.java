package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.repository.projection.EventMedalRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventResultRepository extends JpaRepository<EventResult, Long> {

    Optional<EventResult> findByHeatEntryId(Long heatEntryId);

    List<EventResult> findByHeatEntryIdIn(List<Long> heatEntryIds);

    void deleteByHeatEntryIdIn(List<Long> heatEntryIds);

    /** 대회 단위 cascade 삭제용: 해당 competition에 속한 모든 EventResult 삭제. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM EventResult er WHERE er.heatEntry.id IN (" +
           "SELECT he.id FROM HeatEntry he WHERE he.entry.competition.id = :compId)")
    void deleteByCompetitionId(@Param("compId") Long competitionId);

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

    /**
     * 종목 탭 전용 메달 프로젝션. 엔티티 전체를 로드하지 않고 필요한 컬럼만 가져와 lazy load를 원천 제거.
     */
    @Query("SELECT new kr.pe.batang.inlinedata.repository.projection.EventMedalRow(" +
           "e.id, er.ranking, ce.athleteName, a.id) " +
           "FROM EventResult er " +
           "JOIN er.heatEntry he " +
           "JOIN he.entry ce " +
           "LEFT JOIN ce.athlete a " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound r " +
           "JOIN r.event e " +
           "WHERE e.competition.id = :compId " +
           "AND r.round = '결승' " +
           "AND er.ranking IN (1, 2, 3) " +
           "ORDER BY e.id, er.ranking")
    List<EventMedalRow> findMedalRowsByCompetitionId(@Param("compId") Long competitionId);

    @Query("SELECT er FROM EventResult er " +
           "JOIN FETCH er.heatEntry he " +
           "JOIN FETCH he.entry ce " +
           "JOIN FETCH he.heat eh " +
           "JOIN FETCH eh.eventRound r " +
           "JOIN FETCH r.event e " +
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
