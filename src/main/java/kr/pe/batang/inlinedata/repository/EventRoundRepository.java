package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.EventRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRoundRepository extends JpaRepository<EventRound, Long> {

    List<EventRound> findByEventIdOrderByEventNumberAsc(Long eventId);

    Optional<EventRound> findByEventIdAndRound(Long eventId, String round);

    Optional<EventRound> findByEventIdAndEventNumber(Long eventId, Integer eventNumber);

    List<EventRound> findByEvent_CompetitionIdOrderByEventNumberAsc(Long competitionId);

    @Modifying
    @Query("DELETE FROM EventRound er WHERE er.event.competition.id = :compId")
    void deleteByCompetitionId(@Param("compId") Long competitionId);
}
