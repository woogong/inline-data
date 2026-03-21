package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByCompetitionIdOrderByEventNumberAsc(Long competitionId);

    List<Event> findByCompetitionIdAndDayNumber(Long competitionId, Integer dayNumber);
}
