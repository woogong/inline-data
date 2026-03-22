package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntryService {

    private final CompetitionEntryRepository competitionEntryRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;

    @Transactional
    public Long addHeat(EventRound round, int heatNumber) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(round.getId());
        for (EventHeat h : heats) {
            if (h.getHeatNumber() == heatNumber) return h.getId();
        }
        return eventHeatRepository.save(EventHeat.builder()
                .eventRound(round).heatNumber(heatNumber).build()).getId();
    }

    @Transactional
    public void deleteHeat(Long heatId) {
        List<HeatEntry> entries = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(heatId);
        heatEntryRepository.deleteAll(entries);
        eventHeatRepository.deleteById(heatId);
    }

    @Transactional
    public HeatEntry saveEntry(Event event, Long heatId, int bibNumber, String athleteName,
                               String gender, String region, String teamName) {
        EventHeat heat = eventHeatRepository.findById(heatId)
                .orElseThrow(() -> new IllegalArgumentException("조를 찾을 수 없습니다. id=" + heatId));

        Competition competition = event.getCompetition();

        // 텍스트 기반으로 CompetitionEntry 찾거나 생성 (FK 없이)
        CompetitionEntry compEntry = competitionEntryRepository
                .findByCompetitionIdAndAthleteNameAndGenderAndTeamName(
                        competition.getId(), athleteName.trim(), gender, teamName != null ? teamName.trim() : "")
                .orElseGet(() -> competitionEntryRepository.save(CompetitionEntry.builder()
                        .competition(competition)
                        .athleteName(athleteName.trim())
                        .gender(gender)
                        .region(region != null ? region.trim() : null)
                        .teamName(teamName != null ? teamName.trim() : null)
                        .build()));

        Optional<HeatEntry> existing = heatEntryRepository
                .findByHeatIdOrderByBibNumberAsc(heat.getId()).stream()
                .filter(he -> he.getEntry().getId().equals(compEntry.getId()))
                .findFirst();
        if (existing.isPresent()) return existing.get();

        return heatEntryRepository.save(HeatEntry.builder()
                .heat(heat).entry(compEntry).bibNumber(bibNumber).build());
    }

    @Transactional
    public void deleteEntry(Long heatEntryId) {
        heatEntryRepository.deleteById(heatEntryId);
    }
}
