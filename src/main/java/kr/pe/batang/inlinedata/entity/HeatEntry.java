package kr.pe.batang.inlinedata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// (heat_id, bib_number) UK는 기존 레거시 데이터(bib=0 다수)와 충돌해 DB 레벨로는 걸지 않는다.
// 파서는 evictBibConflict로 중복 bib을 방지하므로 신규 데이터 정합성은 유지됨.
// (heat_id, entry_id) UK는 Hibernate가 생성한 auto-name으로 이미 DB에 존재하므로 이름 지정 없이 유지.
@Entity
@Table(name = "heat_entry",
        uniqueConstraints = @UniqueConstraint(columnNames = {"heat_id", "entry_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HeatEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "heat_id", nullable = false)
    private EventHeat heat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private CompetitionEntry entry;

    @Column(nullable = false)
    private Integer bibNumber;

    @Builder
    public HeatEntry(EventHeat heat, CompetitionEntry entry, Integer bibNumber) {
        this.heat = heat;
        this.entry = entry;
        this.bibNumber = bibNumber;
    }

    public void updateBib(Integer bibNumber) {
        this.bibNumber = bibNumber;
    }
}
