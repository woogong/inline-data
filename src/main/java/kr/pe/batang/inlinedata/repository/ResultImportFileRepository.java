package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.ResultImportFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResultImportFileRepository extends JpaRepository<ResultImportFile, Long> {

    boolean existsByCompetitionIdAndFileHash(Long competitionId, String fileHash);

    Optional<ResultImportFile> findByCompetitionIdAndFileHash(Long competitionId, String fileHash);

    List<ResultImportFile> findTop20ByCompetitionIdOrderByCreatedAtDesc(Long competitionId);

    /**
     * 이미 처리(SUCCESS/SKIPPED/FAILED) 완료된 파일의 (fileName, fileSize) 쌍을 반환.
     * 재스캔 시 hashing 전에 cheap 매칭으로 걸러내기 위한 용도.
     * "name|size" 포맷 문자열을 반환해 Set에 담기 쉽게 한다.
     */
    @Query("SELECT CONCAT(r.fileName, '|', r.fileSize) FROM ResultImportFile r " +
           "WHERE r.competitionId = :compId AND r.status <> 'PENDING'")
    List<String> findProcessedFileKeys(@Param("compId") Long competitionId);
}
