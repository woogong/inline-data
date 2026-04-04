package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.pe.batang.inlinedata.repository.EventRoundRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntryService {

    private final CompetitionEntryRepository competitionEntryRepository;
    private final EventHeatRepository eventHeatRepository;
    private final EventRoundRepository eventRoundRepository;
    private final HeatEntryRepository heatEntryRepository;
    private final EventResultRepository eventResultRepository;

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
        for (HeatEntry he : entries) {
            eventResultRepository.findByHeatEntryId(he.getId()).ifPresent(eventResultRepository::delete);
        }
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
        eventResultRepository.findByHeatEntryId(heatEntryId).ifPresent(eventResultRepository::delete);
        heatEntryRepository.deleteById(heatEntryId);
    }

    /**
     * 같은 종목에 출전한 선수 중 이름이 매칭되는 후보를 반환한다.
     */
    public List<Map<String, Object>> suggestEntries(Long eventId, String query) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.trim();

        // 같은 종목의 모든 라운드 → 모든 조 → 모든 엔트리
        List<EventRound> rounds = eventRoundRepository.findByEventIdOrderByEventNumberAsc(eventId);
        List<Long> heatIds = new ArrayList<>();
        for (EventRound r : rounds) {
            for (EventHeat h : eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(r.getId())) {
                heatIds.add(h.getId());
            }
        }
        if (heatIds.isEmpty()) return List.of();

        // 중복 제거를 위해 athleteName 기준으로 그룹화
        Map<String, Map<String, Object>> seen = new LinkedHashMap<>();
        for (Long heatId : heatIds) {
            for (HeatEntry he : heatEntryRepository.findByHeatIdOrderByBibNumberAsc(heatId)) {
                CompetitionEntry ce = he.getEntry();
                if (ce.getAthleteName().contains(q) && !seen.containsKey(ce.getAthleteName() + "|" + ce.getTeamName())) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("bibNumber", he.getBibNumber());
                    item.put("name", ce.getAthleteName());
                    item.put("region", ce.getRegion());
                    item.put("teamName", ce.getTeamName());
                    seen.put(ce.getAthleteName() + "|" + ce.getTeamName(), item);
                }
            }
        }
        return new ArrayList<>(seen.values());
    }
}
