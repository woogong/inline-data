package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.EventFormDto;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;
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
