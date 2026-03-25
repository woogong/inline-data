package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.EventFormDto;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.EventRoundRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final EventRoundRepository eventRoundRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;
    private final EventResultRepository eventResultRepository;
    private final CompetitionService competitionService;

    // --- Event (종목) ---

    public record MedalInfo(String gold, String silver, String bronze) {}

    public List<Event> findByCompetitionId(Long competitionId) {
        return eventRepository.findByCompetitionIdOrderByFirstEventNumber(competitionId);
    }

    public Map<Long, MedalInfo> findMedalsByCompetitionId(Long competitionId) {
        List<EventResult> medalResults = eventResultRepository.findMedalResultsByCompetitionId(competitionId);

        Map<Long, MedalInfo> medals = new LinkedHashMap<>();
        // eventId → ranking → name
        Map<Long, Map<Integer, String>> byEvent = new LinkedHashMap<>();
        for (EventResult er : medalResults) {
            Long eventId = er.getHeatEntry().getHeat().getEventRound().getEvent().getId();
            byEvent.computeIfAbsent(eventId, k -> new LinkedHashMap<>())
                    .putIfAbsent(er.getRanking(), er.getHeatEntry().getEntry().getAthleteName());
        }

        List<Event> events = findByCompetitionId(competitionId);
        for (Event event : events) {
            Map<Integer, String> m = byEvent.get(event.getId());
            if (m == null) { medals.put(event.getId(), new MedalInfo(null, null, null)); continue; }
            medals.put(event.getId(), new MedalInfo(m.get(1), m.get(2), m.get(3)));
        }
        return medals;
    }

    public Event findById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("종목을 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public Event create(Long competitionId, EventFormDto dto) {
        Competition competition = competitionService.findById(competitionId);
        return eventRepository.save(dto.toEntity(competition));
    }

    @Transactional
    public Event update(Long id, EventFormDto dto) {
        Event event = findById(id);
        event.update(dto.getDivisionName(), dto.getGender(), dto.getEventName(), dto.isTeamEvent());
        return event;
    }

    @Transactional
    public void delete(Long id) {
        Event event = findById(id);
        List<EventRound> rounds = eventRoundRepository.findByEventIdOrderByEventNumberAsc(id);
        for (EventRound round : rounds) {
            List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(round.getId());
            for (EventHeat heat : heats) {
                List<HeatEntry> heatEntries = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(heat.getId());
                for (HeatEntry he : heatEntries) {
                    eventResultRepository.findByHeatEntryId(he.getId()).ifPresent(eventResultRepository::delete);
                }
                heatEntryRepository.deleteAll(heatEntries);
            }
            eventHeatRepository.deleteAll(heats);
        }
        eventRoundRepository.deleteAll(rounds);
        eventRepository.delete(event);
    }

    // --- EventRound (경기/라운드) ---

    public record RoundWithStatus(EventRound round, String status, int entryCount, int resultCount) {}

    public List<EventRound> findRoundsByEventId(Long eventId) {
        return eventRoundRepository.findByEventIdOrderByEventNumberAsc(eventId);
    }

    public List<RoundWithStatus> findRoundsWithStatus(Long eventId) {
        List<EventRound> rounds = eventRoundRepository.findByEventIdOrderByEventNumberAsc(eventId);
        // 모든 라운드의 heat을 한번에 조회
        List<EventHeat> allHeats = new java.util.ArrayList<>();
        for (EventRound r : rounds) {
            allHeats.addAll(eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(r.getId()));
        }
        Map<Long, List<EventHeat>> heatsByRound = allHeats.stream()
                .collect(Collectors.groupingBy(h -> h.getEventRound().getId()));

        return rounds.stream().map(r -> {
            List<EventHeat> heats = heatsByRound.getOrDefault(r.getId(), List.of());
            int entryCount = 0, resultCount = 0;
            if (!heats.isEmpty()) {
                List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
                entryCount = (int) heatEntryRepository.countByHeatIdIn(heatIds);
                resultCount = (int) eventResultRepository.countByHeatIds(heatIds);
            }
            String status;
            if (resultCount > 0 && resultCount >= entryCount) status = "완료";
            else if (resultCount > 0) status = "일부";
            else if (entryCount > 0) status = "엔트리만";
            else status = "없음";
            return new RoundWithStatus(r, status, entryCount, resultCount);
        }).toList();
    }

    public EventRound findRoundById(Long id) {
        return eventRoundRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("경기를 찾을 수 없습니다. id=" + id));
    }

    // --- EventHeat + HeatEntry (조/출전) ---

    public Map<EventHeat, List<HeatEntry>> findHeatsWithEntries(Long eventRoundId) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(eventRoundId);
        if (heats.isEmpty()) return Map.of();
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        List<HeatEntry> entries = heatEntryRepository.findByHeatIdsWithDetails(heatIds);

        // 결과가 있는 heatEntry ID를 수집
        List<EventResult> allResults = eventResultRepository.findByHeatIdsWithDetails(heatIds);
        Map<Long, EventResult> resultByEntryId = allResults.stream()
                .collect(Collectors.toMap(er -> er.getHeatEntry().getId(), er -> er, (a, b) -> a));
        Set<Long> heatsWithResults = allResults.stream()
                .map(er -> er.getHeatEntry().getHeat().getId())
                .collect(Collectors.toSet());

        // 결과가 있는 조는 순위 순, 없는 조는 배번 순
        Map<Long, List<HeatEntry>> entriesByHeatId = entries.stream()
                .collect(Collectors.groupingBy(he -> he.getHeat().getId()));
        for (Map.Entry<Long, List<HeatEntry>> e : entriesByHeatId.entrySet()) {
            Long heatId = e.getKey();
            if (heatsWithResults.contains(heatId)) {
                e.getValue().sort(Comparator.comparingInt(he -> {
                    EventResult r = resultByEntryId.get(he.getId());
                    return r != null && r.getRanking() != null ? r.getRanking() : Integer.MAX_VALUE;
                }));
            }
        }
        Map<EventHeat, List<HeatEntry>> result = new LinkedHashMap<>();
        for (EventHeat heat : heats) {
            result.put(heat, entriesByHeatId.getOrDefault(heat.getId(), List.of()));
        }
        return result;
    }

    public Map<Long, EventResult> findResultsByEntryId(Long eventRoundId) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(eventRoundId);
        if (heats.isEmpty()) return Map.of();
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        List<EventResult> results = eventResultRepository.findByHeatIdsWithDetails(heatIds);
        return results.stream()
                .collect(Collectors.toMap(er -> er.getHeatEntry().getId(), er -> er));
    }

    public Map<EventHeat, List<EventResult>> findHeatsWithResults(Long eventRoundId) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(eventRoundId);
        if (heats.isEmpty()) return Map.of();
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        List<EventResult> results = eventResultRepository.findByHeatIdsWithDetails(heatIds);
        Map<Long, List<EventResult>> resultsByHeatId = results.stream()
                .collect(Collectors.groupingBy(er -> er.getHeatEntry().getHeat().getId()));
        Map<EventHeat, List<EventResult>> result = new LinkedHashMap<>();
        for (EventHeat heat : heats) {
            result.put(heat, resultsByHeatId.getOrDefault(heat.getId(), List.of()));
        }
        return result;
    }

    public boolean hasResults(Long eventRoundId) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(eventRoundId);
        if (heats.isEmpty()) return false;
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        return !eventResultRepository.findByHeatIdsWithDetails(heatIds).isEmpty();
    }

    @Transactional
    public void saveResult(Long heatEntryId, Integer ranking, String record,
                           String newRecord, String qualification, String note) {
        HeatEntry heatEntry = heatEntryRepository.findById(heatEntryId)
                .orElseThrow(() -> new IllegalArgumentException("출전 엔트리를 찾을 수 없습니다. id=" + heatEntryId));
        Optional<EventResult> existing = eventResultRepository.findByHeatEntryId(heatEntryId);
        if (existing.isPresent()) {
            existing.get().updateResult(ranking, record, newRecord, qualification, note);
        } else {
            eventResultRepository.save(EventResult.builder()
                    .heatEntry(heatEntry)
                    .ranking(ranking)
                    .record(record)
                    .newRecord(newRecord)
                    .qualification(qualification)
                    .note(note)
                    .build());
        }
    }
}
