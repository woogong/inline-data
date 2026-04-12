package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {

    List<ScheduleEntry> findByCompetitionIdOrderByDayNumberAscIdAsc(Long competitionId);

    List<ScheduleEntry> findByCompetitionIdAndDayNumberOrderByIdAsc(Long competitionId, Integer dayNumber);

    boolean existsByCompetitionId(Long competitionId);

    void deleteByCompetitionId(Long competitionId);
}
