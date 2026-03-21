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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "event",
        uniqueConstraints = @UniqueConstraint(columnNames = {"competition_id", "event_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @Column(name = "event_number", nullable = false)
    private Integer eventNumber;

    @Column(nullable = false, length = 50)
    private String divisionName;

    @Column(nullable = false, length = 1)
    private String gender;

    @Column(nullable = false, length = 30)
    private String eventName;

    @Column(length = 20)
    private String round;

    private Integer dayNumber;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Event(Competition competition, Integer eventNumber, String divisionName,
                 String gender, String eventName, String round, Integer dayNumber) {
        this.competition = competition;
        this.eventNumber = eventNumber;
        this.divisionName = divisionName;
        this.gender = gender;
        this.eventName = eventName;
        this.round = round;
        this.dayNumber = dayNumber;
    }

    public void update(Integer eventNumber, String divisionName, String gender,
                       String eventName, String round, Integer dayNumber) {
        this.eventNumber = eventNumber;
        this.divisionName = divisionName;
        this.gender = gender;
        this.eventName = eventName;
        this.round = round;
        this.dayNumber = dayNumber;
    }
}
