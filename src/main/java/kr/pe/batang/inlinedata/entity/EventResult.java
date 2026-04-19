package kr.pe.batang.inlinedata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_result",
        uniqueConstraints = @UniqueConstraint(columnNames = {"heat_entry_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EventResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "heat_entry_id", nullable = false)
    private HeatEntry heatEntry;

    private Integer ranking;

    @Column(length = 30)
    private String record;

    @Column(length = 20)
    private String newRecord;

    @Column(length = 10)
    private String qualification;

    @Column(length = 100)
    private String note;

    /**
     * 이 행을 마지막으로 기록/수정한 출처. 덮어쓰기 우선순위 판정에 사용.
     * columnDefinition을 명시하는 이유:
     *  - Hibernate 7이 @Enumerated(STRING)을 MariaDB ENUM 타입으로 생성하는 것을 피해
     *    VARCHAR로 고정 (enum 값 추가/삭제 시 ALTER TABLE 비용 감소)
     *  - 기존 행이 있는 테이블에 NOT NULL 컬럼 추가 시 DEFAULT 값 제공
     */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(10) NOT NULL DEFAULT 'UPLOAD'")
    private ResultSource source;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public EventResult(HeatEntry heatEntry, Integer ranking, String record,
                       String newRecord, String qualification, String note,
                       ResultSource source) {
        this.heatEntry = heatEntry;
        this.ranking = ranking;
        this.record = record;
        this.newRecord = newRecord;
        this.qualification = qualification;
        this.note = note;
        this.source = source != null ? source : ResultSource.UPLOAD;
    }

    public void updateResult(Integer ranking, String record, String newRecord,
                             String qualification, String note, ResultSource source) {
        this.ranking = ranking;
        this.record = record;
        this.newRecord = newRecord;
        this.qualification = qualification;
        this.note = note;
        if (source != null) this.source = source;
    }
}
