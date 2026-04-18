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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "competition_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CompetitionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    // PDF 원본 텍스트 (import 시 채워짐)
    @Column(nullable = false, length = 50)
    private String athleteName;

    @Column(length = 1)
    private String gender;

    @Column(length = 100)
    private String region;

    @Column(length = 100)
    private String teamName;

    private Integer grade;

    // 매핑된 FK (수기 작업으로 연결, nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "athlete_id")
    private Athlete athlete;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public CompetitionEntry(Competition competition, String athleteName, String gender,
                            String region, String teamName, Integer grade,
                            Athlete athlete, Team team) {
        this.competition = competition;
        this.athleteName = athleteName;
        this.gender = gender;
        this.region = region;
        this.teamName = teamName;
        this.grade = grade;
        this.athlete = athlete;
        this.team = team;
    }

    public void updateFromParsed(String region, String teamName) {
        // 기존 값이 비어있을 때만 채운다. 이미 값이 있으면 보존 (수기 수정 데이터 유지)
        if ((this.region == null || this.region.isBlank()) && region != null && !region.isBlank()) {
            this.region = region;
        }
        if ((this.teamName == null || this.teamName.isBlank()) && teamName != null && !teamName.isBlank()) {
            this.teamName = teamName;
        }
    }

    public void mapAthlete(Athlete athlete) {
        this.athlete = athlete;
    }

    public void mapTeam(Team team) {
        this.team = team;
    }

    public void unmapAthlete() {
        this.athlete = null;
    }

    public void unmapTeam() {
        this.team = null;
    }

    public boolean isMapped() {
        return this.athlete != null;
    }
}
