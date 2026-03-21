package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.EventFormDto;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @InjectMocks
    private EventService eventService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventHeatRepository eventHeatRepository;

    @Mock
    private HeatEntryRepository heatEntryRepository;

    @Mock
    private CompetitionService competitionService;

    private Competition createCompetition() {
        return Competition.builder().name("테스트 대회").shortName("테스트").build();
    }

    private Event createEvent(Competition competition) {
        return Event.builder()
                .competition(competition)
                .eventNumber(1)
                .divisionName("여초부 5,6학년")
                .gender("F")
                .eventName("500m+D")
                .round("예선")
                .dayNumber(1)
                .build();
    }

    private EventFormDto createDto() {
        EventFormDto dto = new EventFormDto();
        dto.setEventNumber(1);
        dto.setDivisionName("여초부 5,6학년");
        dto.setGender("F");
        dto.setEventName("500m+D");
        dto.setRound("예선");
        dto.setDayNumber(1);
        return dto;
    }

    @Test
    @DisplayName("대회별 종목 조회")
    void findByCompetitionId() {
        Competition comp = createCompetition();
        given(eventRepository.findByCompetitionIdOrderByEventNumberAsc(1L))
                .willReturn(List.of(createEvent(comp)));

        List<Event> result = eventService.findByCompetitionId(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("종목 생성")
    void create() {
        Competition comp = createCompetition();
        given(competitionService.findById(1L)).willReturn(comp);
        given(eventRepository.save(any(Event.class))).willReturn(createEvent(comp));

        Event result = eventService.create(1L, createDto());

        assertThat(result.getEventName()).isEqualTo("500m+D");
        then(eventRepository).should().save(any(Event.class));
    }

    @Test
    @DisplayName("종목 수정")
    void update() {
        Competition comp = createCompetition();
        Event event = createEvent(comp);
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));

        EventFormDto dto = createDto();
        dto.setEventName("300m");

        Event result = eventService.update(1L, dto);
        assertThat(result.getEventName()).isEqualTo("300m");
    }

    @Test
    @DisplayName("종목 삭제")
    void delete() {
        Competition comp = createCompetition();
        Event event = createEvent(comp);
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));

        eventService.delete(1L);

        then(eventRepository).should().delete(event);
    }

    @Test
    @DisplayName("존재하지 않는 종목 조회")
    void findByIdNotFound() {
        given(eventRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
