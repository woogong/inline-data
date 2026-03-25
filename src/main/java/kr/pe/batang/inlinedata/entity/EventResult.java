package kr.pe.batang.inlinedata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public EventResult(HeatEntry heatEntry, Integer ranking, String record,
                       String newRecord, String qualification, String note) {
        this.heatEntry = heatEntry;
        this.ranking = ranking;
        this.record = record;
        this.newRecord = newRecord;
        this.qualification = qualification;
        this.note = note;
    }

    public void updateResult(Integer ranking, String record, String newRecord,
                             String qualification, String note) {
        this.ranking = ranking;
        this.record = record;
        this.newRecord = newRecord;
        this.qualification = qualification;
        this.note = note;
    }
}
