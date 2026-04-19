package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.HeatEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HeatEntryRepository extends JpaRepository<HeatEntry, Long> {

    List<HeatEntry> findByHeatIdOrderByBibNumberAsc(Long heatId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM HeatEntry he WHERE he.entry.competition.id = :compId")
    void deleteByCompetitionId(@Param("compId") Long competitionId);

    long countByHeatIdIn(List<Long> heatIds);

    List<HeatEntry> findByEntryId(Long entryId);

    @Query("SELECT he FROM HeatEntry he " +
           "JOIN FETCH he.entry ce " +
           "LEFT JOIN FETCH ce.athlete " +
           "LEFT JOIN FETCH ce.team " +
           "WHERE he.heat.id IN :heatIds " +
           "ORDER BY he.heat.id, he.bibNumber")
    List<HeatEntry> findByHeatIdsWithDetails(@Param("heatIds") List<Long> heatIds);

    /**
     * 주어진 CompetitionEntry id 목록에 대해 (entryId, divisionName) 쌍을 한번에 반환.
     * N+1 제거용. 반환: [entryId(Long), divisionName(String)]
     */
    @Query("SELECT he.entry.id, e.divisionName " +
           "FROM HeatEntry he " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound r " +
           "JOIN r.event e " +
           "WHERE he.entry.id IN :entryIds")
    List<Object[]> findDivisionNamesByEntryIds(@Param("entryIds") List<Long> entryIds);

    /**
     * 이벤트에 속한 모든 HeatEntry를 CompetitionEntry까지 fetch join으로 조회.
     * suggestEntries 등에서 사용.
     */
    @Query("SELECT he FROM HeatEntry he " +
           "JOIN FETCH he.entry ce " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound r " +
           "WHERE r.event.id = :eventId " +
           "ORDER BY eh.id, he.bibNumber")
    List<HeatEntry> findByEventIdWithEntry(@Param("eventId") Long eventId);

    /**
     * 주어진 CompetitionEntry id 목록에 대해 HeatEntry를 event까지 fetch join으로 로드.
     * toHistoryDto 일괄 생성용.
     */
    @Query("SELECT he FROM HeatEntry he " +
           "JOIN FETCH he.heat eh " +
           "JOIN FETCH eh.eventRound r " +
           "JOIN FETCH r.event e " +
           "WHERE he.entry.id IN :entryIds")
    List<HeatEntry> findByEntryIdsWithEvent(@Param("entryIds") List<Long> entryIds);
}
