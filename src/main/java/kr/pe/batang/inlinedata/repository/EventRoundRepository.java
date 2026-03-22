package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.EventRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRoundRepository extends JpaRepository<EventRound, Long> {

    List<EventRound> findByEventIdOrderByEventNumberAsc(Long eventId);

    Optional<EventRound> findByEventIdAndRound(Long eventId, String round);

    Optional<EventRound> findByEventIdAndEventNumber(Long eventId, Integer eventNumber);

    List<EventRound> findByEvent_CompetitionIdOrderByEventNumberAsc(Long competitionId);
}
