package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompetitionEntryRepository extends JpaRepository<CompetitionEntry, Long> {

    List<CompetitionEntry> findByCompetitionId(Long competitionId);

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

    List<CompetitionEntry> findByCompetitionIdAndAthleteIsNull(Long competitionId);

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
