package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.ResultImportFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResultImportFileRepository extends JpaRepository<ResultImportFile, Long> {

    boolean existsByCompetitionIdAndFileHash(Long competitionId, String fileHash);

    Optional<ResultImportFile> findByCompetitionIdAndFileHash(Long competitionId, String fileHash);

    List<ResultImportFile> findTop20ByCompetitionIdOrderByCreatedAtDesc(Long competitionId);
}
