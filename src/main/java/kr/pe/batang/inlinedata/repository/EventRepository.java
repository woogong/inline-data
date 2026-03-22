package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e LEFT JOIN EventRound r ON r.event = e " +
           "WHERE e.competition.id = :compId " +
           "GROUP BY e " +
           "ORDER BY MIN(r.eventNumber) ASC NULLS LAST")
    List<Event> findByCompetitionIdOrderByFirstEventNumber(@Param("compId") Long competitionId);

    Optional<Event> findByCompetitionIdAndDivisionNameAndGenderAndEventName(
            Long competitionId, String divisionName, String gender, String eventName);
}
