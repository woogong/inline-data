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

// (competition_id, athlete_name, gender, team_name) UK는 기존 레거시 데이터에 35건의 중복 그룹이
// 있어 DB 레벨로는 걸지 않는다. findOrCreateCompetitionEntry가 "가장 오래된 id를 재사용"하는 방식으로
// 신규 중복을 방지한다.
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

    @Column(length = 100, nullable = false)
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
        this.teamName = normalizeTeamName(teamName);
        this.grade = grade;
        this.athlete = athlete;
        this.team = team;
    }

    public void updateFromParsed(String region, String teamName) {
        // 기존 값이 비어있을 때만 채운다. 이미 값이 있으면 보존 (수기 수정 데이터 유지)
        if ((this.region == null || this.region.isBlank()) && region != null && !region.isBlank()) {
            this.region = region;
        }
        if (this.teamName.isBlank() && teamName != null && !teamName.isBlank()) {
            this.teamName = teamName;
        }
    }

    /**
     * teamName을 DB 저장 및 조회에 일관된 형태로 정규화.
     * UK (competition_id, athlete_name, gender, team_name)에서 NULL이 중복 허용되는 것을 막기 위해 빈 문자열로 통일.
     */
    public static String normalizeTeamName(String teamName) {
        return teamName == null ? "" : teamName.trim();
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
