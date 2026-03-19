package kr.pe.batang.inlinedata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(name = "competition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Competition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer durationDays;

    @Column(length = 200)
    private String venue;

    @Column(length = 200)
    private String venueDetail;

    @Column(length = 100)
    private String host;

    @Column(length = 100)
    private String organizer;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Competition(String name, LocalDate startDate, LocalDate endDate, Integer durationDays,
                       String venue, String venueDetail, String host,
                       String organizer, String notes) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.durationDays = durationDays;
        this.venue = venue;
        this.venueDetail = venueDetail;
        this.host = host;
        this.organizer = organizer;
        this.notes = notes;
    }

    public void update(String name, LocalDate startDate, LocalDate endDate, Integer durationDays,
                       String venue, String venueDetail, String host,
                       String organizer, String notes) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.durationDays = durationDays;
        this.venue = venue;
        this.venueDetail = venueDetail;
        this.host = host;
        this.organizer = organizer;
        this.notes = notes;
    }
}
