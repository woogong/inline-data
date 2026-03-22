package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionEntryRepository extends JpaRepository<CompetitionEntry, Long> {

    List<CompetitionEntry> findByCompetitionId(Long competitionId);

    List<CompetitionEntry> findByAthleteId(Long athleteId);

    Optional<CompetitionEntry> findByCompetitionIdAndAthleteId(Long competitionId, Long athleteId);
}
