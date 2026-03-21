package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompetitionEntryRepository extends JpaRepository<CompetitionEntry, Long> {

    List<CompetitionEntry> findByCompetitionId(Long competitionId);

    List<CompetitionEntry> findByAthleteId(Long athleteId);
}
