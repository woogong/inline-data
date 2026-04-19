package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.EventFormDto;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.EventRoundRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import kr.pe.batang.inlinedata.repository.projection.EventMedalRow;
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
    @DisplayName("종목 탭 메달 프로젝션 — eventId별로 gold/silver/bronze 매핑")
    void findMedalsByCompetitionId() {
        given(eventResultRepository.findMedalRowsByCompetitionId(1L)).willReturn(List.of(
                new EventMedalRow(10L, 1, "길동이", 100L),
                new EventMedalRow(10L, 2, "채훈이", null),
                new EventMedalRow(10L, 3, "민수이", 102L),
                new EventMedalRow(20L, 1, "지연이", 200L),
                new EventMedalRow(20L, 3, "유나이", 203L) // 은메달 누락 (EL 등으로 공석)
        ));

        var medals = eventService.findMedalsByCompetitionId(1L);

        assertThat(medals).hasSize(2);
        EventService.MedalInfo e10 = medals.get(10L);
        assertThat(e10.gold()).isEqualTo("길동이");
        assertThat(e10.goldId()).isEqualTo(100L);
        assertThat(e10.silver()).isEqualTo("채훈이");
        assertThat(e10.silverId()).isNull();
        assertThat(e10.bronze()).isEqualTo("민수이");

        EventService.MedalInfo e20 = medals.get(20L);
        assertThat(e20.gold()).isEqualTo("지연이");
        assertThat(e20.silver()).isNull();      // 은메달 없음
        assertThat(e20.bronze()).isEqualTo("유나이");
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
