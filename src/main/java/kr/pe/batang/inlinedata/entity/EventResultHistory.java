package kr.pe.batang.inlinedata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * {@link EventResult}의 모든 변경 기록을 append-only로 저장하는 감사 로그.
 * 저장/수정 시점마다 행 1개가 추가된다. FK 대신 event_result_id를 그대로 보관하여
 * 원본 행이 삭제돼도 이력은 유지된다.
 */
@Entity
@Table(name = "event_result_history",
        indexes = @Index(name = "idx_erh_result_id", columnList = "event_result_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EventResultHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_result_id", nullable = false)
    private Long eventResultId;

    @Column(name = "heat_entry_id", nullable = false)
    private Long heatEntryId;

    /** EventResult.source와 동일한 이유로 VARCHAR로 고정. */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(10) NOT NULL")
    private ResultSource source;

    private Integer ranking;

    @Column(length = 30)
    private String record;

    @Column(length = 20)
    private String newRecord;

    @Column(length = 10)
    private String qualification;

    @Column(length = 100)
    private String note;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime recordedAt;

    @Builder
    public EventResultHistory(Long eventResultId, Long heatEntryId, ResultSource source,
                              Integer ranking, String record, String newRecord,
                              String qualification, String note) {
        this.eventResultId = eventResultId;
        this.heatEntryId = heatEntryId;
        this.source = source;
        this.ranking = ranking;
        this.record = record;
        this.newRecord = newRecord;
        this.qualification = qualification;
        this.note = note;
    }
}