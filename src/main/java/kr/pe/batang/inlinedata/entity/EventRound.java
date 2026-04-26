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
@Table(name = "event_round",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "round"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EventRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 20)
    private String round;

    private Integer eventNumber;

    private Integer dayNumber;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public EventRound(Event event, String round, Integer eventNumber, Integer dayNumber) {
        this.event = event;
        this.round = round;
        this.eventNumber = eventNumber;
        this.dayNumber = dayNumber;
    }

    public void update(String round, Integer eventNumber, Integer dayNumber) {
        this.round = round;
        this.eventNumber = eventNumber;
        this.dayNumber = dayNumber;
    }

    public void updateEventNumber(Integer eventNumber) {
        this.eventNumber = eventNumber;
    }
}
