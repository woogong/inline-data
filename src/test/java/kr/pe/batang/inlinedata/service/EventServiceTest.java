package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.EventFormDto;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.EventRoundRepository;
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

    @Mock private EventRepository eventRepository;
    @Mock private EventRoundRepository eventRoundRepository;
    @Mock private EventHeatRepository eventHeatRepository;
    @Mock private HeatEntryRepository heatEntryRepository;
    @Mock private EventResultRepository eventResultRepository;
    @Mock private CompetitionService competitionService;

    private Competition createCompetition() {
        return Competition.builder().name("테스트 대회").shortName("테스트").build();
    }

    private Event createEvent(Competition competition) {
        return Event.builder().competition(competition)
                .divisionName("여초부 5,6학년").gender("F").eventName("500m+D").build();
    }

    private EventFormDto createDto() {
        EventFormDto dto = new EventFormDto();
        dto.setDivisionName("여초부 5,6학년");
        dto.setGender("F");
        dto.setEventName("500m+D");
        return dto;
    }

    @Test
    @DisplayName("대회별 종목 조회")
    void findByCompetitionId() {
        given(eventRepository.findByCompetitionIdOrderByFirstEventNumber(1L))
                .willReturn(List.of(createEvent(createCompetition())));
        assertThat(eventService.findByCompetitionId(1L)).hasSize(1);
    }

    @Test
    @DisplayName("종목 생성")
    void create() {
        Competition comp = createCompetition();
        given(competitionService.findById(1L)).willReturn(comp);
        given(eventRepository.save(any(Event.class))).willReturn(createEvent(comp));
        assertThat(eventService.create(1L, createDto()).getEventName()).isEqualTo("500m+D");
    }

    @Test
    @DisplayName("종목 수정")
    void update() {
        Event event = createEvent(createCompetition());
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        EventFormDto dto = createDto();
        dto.setEventName("300m");
        assertThat(eventService.update(1L, dto).getEventName()).isEqualTo("300m");
    }

    @Test
    @DisplayName("종목 삭제")
    void delete() {
        Event event = createEvent(createCompetition());
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        eventService.delete(1L);
        then(eventRepository).should().delete(event);
    }

    @Test
    @DisplayName("존재하지 않는 종목 조회")
    void findByIdNotFound() {
        given(eventRepository.findById(999L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> eventService.findById(999L)).isInstanceOf(IllegalArgumentException.class);
    }
}
