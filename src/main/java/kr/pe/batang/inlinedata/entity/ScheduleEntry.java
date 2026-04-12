package kr.pe.batang.inlinedata.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "schedule_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @Column(nullable = false)
    private Integer dayNumber;

    private LocalDate dayDate;

    private Integer orderNumber;

    @Column(length = 10)
    private String startTime;

    @Column(length = 100)
    private String divisionName;

    @Column(length = 50)
    private String eventName;

    @Column(length = 50)
    private String roundType;

    @Column(length = 50)
    private String heatInfo;

    private Integer quarterFinalRef;

    private Integer semiFinalRef;

    private Integer finalRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_round_id")
    private EventRound eventRound;

    @Column(length = 20, nullable = false)
    private String entryType; // race, warmup, lunch, ceremony

    @Column(length = 200)
    private String notes;

    @Builder
    public ScheduleEntry(Competition competition, Integer dayNumber, LocalDate dayDate,
                          Integer orderNumber, String startTime, String divisionName,
                          String eventName, String roundType, String heatInfo,
                          Integer quarterFinalRef, Integer semiFinalRef, Integer finalRef,
                          EventRound eventRound, String entryType, String notes) {
        this.competition = competition;
        this.dayNumber = dayNumber;
        this.dayDate = dayDate;
        this.orderNumber = orderNumber;
        this.startTime = startTime;
        this.divisionName = divisionName;
        this.eventName = eventName;
        this.roundType = roundType;
        this.heatInfo = heatInfo;
        this.quarterFinalRef = quarterFinalRef;
        this.semiFinalRef = semiFinalRef;
        this.finalRef = finalRef;
        this.eventRound = eventRound;
        this.entryType = entryType;
        this.notes = notes;
    }
}
