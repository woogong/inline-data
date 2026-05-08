package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {

    @Query("select s from ScheduleEntry s left join fetch s.eventRound er left join fetch er.event " +
            "where s.competition.id = :compId order by s.dayNumber asc, s.id asc")
    List<ScheduleEntry> findByCompetitionIdOrderByDayNumberAscIdAsc(@Param("compId") Long competitionId);

    @Query("select s from ScheduleEntry s left join fetch s.eventRound er left join fetch er.event " +
            "where s.competition.id = :compId and s.dayNumber = :day order by s.id asc")
    List<ScheduleEntry> findByCompetitionIdAndDayNumberOrderByIdAsc(@Param("compId") Long competitionId,
                                                                     @Param("day") Integer dayNumber);

    boolean existsByCompetitionId(Long competitionId);

    void deleteByCompetitionId(Long competitionId);
}
