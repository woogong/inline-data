package kr.pe.batang.inlinedata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "result_import_file",
        indexes = {
                @Index(name = "idx_result_import_comp_created", columnList = "competition_id, created_at"),
                @Index(name = "idx_result_import_status", columnList = "status"),
                @Index(name = "idx_result_import_comp_hash", columnList = "competition_id, file_hash")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ResultImportFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 1000)
    private String filePath;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(nullable = false)
    private long fileSize;

    private LocalDateTime sourceLastModifiedAt;

    @Column(nullable = false, length = 20)
    private String status;

    private Integer resultsCount;

    private Integer newEntriesCount;

    @Column(length = 500)
    private String message;

    private LocalDateTime processedAt;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public ResultImportFile(Long competitionId, String fileName, String filePath, String fileHash, long fileSize,
                            LocalDateTime sourceLastModifiedAt, String status, Integer resultsCount,
                            Integer newEntriesCount, String message, LocalDateTime processedAt) {
        this.competitionId = competitionId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
        this.sourceLastModifiedAt = sourceLastModifiedAt;
        this.status = status;
        this.resultsCount = resultsCount;
        this.newEntriesCount = newEntriesCount;
        this.message = message;
        this.processedAt = processedAt;
    }

    public void markSuccess(Integer resultsCount, Integer newEntriesCount, String message) {
        this.status = "SUCCESS";
        this.resultsCount = resultsCount;
        this.newEntriesCount = newEntriesCount;
        this.message = message;
        this.processedAt = LocalDateTime.now();
    }

    public void markSkipped(String message) {
        this.status = "SKIPPED";
        this.resultsCount = 0;
        this.newEntriesCount = 0;
        this.message = message;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(String message) {
        this.status = "FAILED";
        this.message = message;
        this.processedAt = LocalDateTime.now();
    }
}
