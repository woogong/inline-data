package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.EventFormDto;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;
    private final EventResultRepository eventResultRepository;
    private final CompetitionService competitionService;

    public List<Event> findByCompetitionId(Long competitionId) {
        return eventRepository.findByCompetitionIdOrderByEventNumberAsc(competitionId);
    }

    public Event findById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("종목을 찾을 수 없습니다. id=" + id));
    }

    public List<EventHeat> findHeatsByEventId(Long eventId) {
        return eventHeatRepository.findByEventIdOrderByHeatNumberAsc(eventId);
    }

    public List<HeatEntry> findEntriesByHeatId(Long heatId) {
        return heatEntryRepository.findByHeatIdOrderByBibNumberAsc(heatId);
    }

    public Map<EventHeat, List<HeatEntry>> findHeatsWithEntries(Long eventId) {
        List<EventHeat> heats = eventHeatRepository.findByEventIdOrderByHeatNumberAsc(eventId);
        if (heats.isEmpty()) {
            return Map.of();
        }
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        List<HeatEntry> entries = heatEntryRepository.findByHeatIdsWithDetails(heatIds);
        Map<Long, List<HeatEntry>> entriesByHeatId = entries.stream()
                .collect(Collectors.groupingBy(he -> he.getHeat().getId()));
        Map<EventHeat, List<HeatEntry>> result = new LinkedHashMap<>();
        for (EventHeat heat : heats) {
            result.put(heat, entriesByHeatId.getOrDefault(heat.getId(), List.of()));
        }
        return result;
    }

    public Map<EventHeat, List<EventResult>> findHeatsWithResults(Long eventId) {
        List<EventHeat> heats = eventHeatRepository.findByEventIdOrderByHeatNumberAsc(eventId);
        if (heats.isEmpty()) {
            return Map.of();
        }
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

    public Map<Long, EventResult> findResultsByEntryId(Long eventId) {
        List<EventHeat> heats = eventHeatRepository.findByEventIdOrderByHeatNumberAsc(eventId);
        if (heats.isEmpty()) return Map.of();
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        List<EventResult> results = eventResultRepository.findByHeatIdsWithDetails(heatIds);
        return results.stream()
                .collect(Collectors.toMap(er -> er.getHeatEntry().getId(), er -> er));
    }

    public boolean hasResults(Long eventId) {
        List<EventHeat> heats = eventHeatRepository.findByEventIdOrderByHeatNumberAsc(eventId);
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

    @Transactional
    public Event create(Long competitionId, EventFormDto dto) {
        Competition competition = competitionService.findById(competitionId);
        return eventRepository.save(dto.toEntity(competition));
    }

    @Transactional
    public Event update(Long id, EventFormDto dto) {
        Event event = findById(id);
        event.update(
                dto.getEventNumber(),
                dto.getDivisionName(),
                dto.getGender(),
                dto.getEventName(),
                dto.getRound(),
                dto.getDayNumber()
        );
        return event;
    }

    @Transactional
    public void delete(Long id) {
        Event event = findById(id);
        eventRepository.delete(event);
    }
}
