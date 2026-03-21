package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.EventHeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventHeatRepository extends JpaRepository<EventHeat, Long> {

    List<EventHeat> findByEventIdOrderByHeatNumberAsc(Long eventId);
}
