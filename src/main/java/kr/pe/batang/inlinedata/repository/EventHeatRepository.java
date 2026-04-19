package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.EventHeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventHeatRepository extends JpaRepository<EventHeat, Long> {

    List<EventHeat> findByEventRoundIdOrderByHeatNumberAsc(Long eventRoundId);

    @Modifying
    @Query("DELETE FROM EventHeat eh WHERE eh.eventRound.event.competition.id = :compId")
    void deleteByCompetitionId(@Param("compId") Long competitionId);
}
