package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByCompetitionIdOrderByEventNumberAsc(Long competitionId);

    List<Event> findByCompetitionIdAndDayNumber(Long competitionId, Integer dayNumber);

    Optional<Event> findByCompetitionIdAndEventNumber(Long competitionId, Integer eventNumber);
}
