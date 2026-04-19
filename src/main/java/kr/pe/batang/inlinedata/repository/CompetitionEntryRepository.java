package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompetitionEntryRepository extends JpaRepository<CompetitionEntry, Long> {

    List<CompetitionEntry> findByCompetitionId(Long competitionId);

    @Modifying
    @Query("DELETE FROM CompetitionEntry ce WHERE ce.competition.id = :compId")
    void deleteByCompetitionId(@Param("compId") Long competitionId);

    @Query("SELECT ce FROM CompetitionEntry ce " +
           "LEFT JOIN FETCH ce.athlete " +
           "WHERE ce.competition.id = :compId " +
           "ORDER BY ce.athleteName")
    List<CompetitionEntry> findByCompetitionIdWithAthlete(@Param("compId") Long competitionId);

    @Query("SELECT DISTINCT ce FROM CompetitionEntry ce " +
           "LEFT JOIN FETCH ce.athlete " +
           "JOIN HeatEntry he ON he.entry = ce " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound er " +
           "JOIN er.event e " +
           "WHERE ce.competition.id = :compId AND e.teamEvent = false " +
           "ORDER BY ce.athleteName")
    List<CompetitionEntry> findIndividualEntriesWithAthlete(@Param("compId") Long competitionId);

    @Query("SELECT DISTINCT ce FROM CompetitionEntry ce " +
           "JOIN HeatEntry he ON he.entry = ce " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound er " +
           "JOIN er.event e " +
           "WHERE ce.competition.id = :compId AND e.teamEvent = false AND ce.athlete IS NULL")
    List<CompetitionEntry> findIndividualUnmappedEntries(@Param("compId") Long competitionId);

    List<CompetitionEntry> findByAthleteId(Long athleteId);

    Optional<CompetitionEntry> findByCompetitionIdAndAthleteId(Long competitionId, Long athleteId);

    Optional<CompetitionEntry> findByCompetitionIdAndAthleteNameAndGenderAndTeamName(
            Long competitionId, String athleteName, String gender, String teamName);

    List<CompetitionEntry> findAllByCompetitionIdAndAthleteNameAndGenderAndTeamName(
            Long competitionId, String athleteName, String gender, String teamName);

    List<CompetitionEntry> findByCompetitionIdAndAthleteIsNull(Long competitionId);

    // 지역별 개인전 참가 CE 수 집계 (B조 여부 포함). N+1 해소용.
    // 반환: [region(String), ceId(Long), isBGroup(0|1)]
    @Query("SELECT ce.region, ce.id, " +
           "MAX(CASE WHEN e.divisionName LIKE '%일반(B조)%' THEN 1 ELSE 0 END) " +
           "FROM CompetitionEntry ce " +
           "JOIN HeatEntry he ON he.entry = ce " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound er " +
           "JOIN er.event e " +
           "WHERE ce.competition.id = :compId AND e.teamEvent = false " +
           "AND ce.region IS NOT NULL AND ce.region <> '' " +
           "GROUP BY ce.region, ce.id")
    List<Object[]> findRegionEntryBGroupInfo(@Param("compId") Long competitionId);

    List<CompetitionEntry> findByAthleteNameAndGender(String athleteName, String gender);

    List<CompetitionEntry> findByCompetitionIdAndTeamIsNull(Long competitionId);

    @Query("SELECT DISTINCT ce FROM CompetitionEntry ce " +
           "LEFT JOIN FETCH ce.team " +
           "JOIN HeatEntry he ON he.entry = ce " +
           "JOIN he.heat eh " +
           "JOIN eh.eventRound er " +
           "JOIN er.event e " +
           "WHERE ce.competition.id = :compId AND e.teamEvent = false " +
           "ORDER BY ce.teamName")
    List<CompetitionEntry> findIndividualEntriesWithTeam(@Param("compId") Long competitionId);
}
