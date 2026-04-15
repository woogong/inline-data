package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.ResultImportFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResultImportFileRepository extends JpaRepository<ResultImportFile, Long> {

    boolean existsByCompetitionIdAndFileHash(Long competitionId, String fileHash);

    List<ResultImportFile> findTop20ByCompetitionIdOrderByCreatedAtDesc(Long competitionId);
}
