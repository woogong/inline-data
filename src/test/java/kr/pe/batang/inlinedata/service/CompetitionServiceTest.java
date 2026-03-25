package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.CompetitionFormDto;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.CompetitionRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTest {

    @InjectMocks
    private CompetitionService competitionService;

    @Mock private CompetitionRepository competitionRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EventRoundRepository eventRoundRepository;
    @Mock private EventHeatRepository eventHeatRepository;
    @Mock private HeatEntryRepository heatEntryRepository;
    @Mock private EventResultRepository eventResultRepository;
    @Mock private CompetitionEntryRepository competitionEntryRepository;

    private CompetitionFormDto createDto() {
        CompetitionFormDto dto = new CompetitionFormDto();
        dto.setName("테스트 대회");
        dto.setShortName("테스트");
        dto.setStartDate(LocalDate.of(2025, 6, 20));
        dto.setEndDate(LocalDate.of(2025, 6, 22));
        dto.setDurationDays(3);
        dto.setVenue("나주시");
        dto.setHost("대한롤러스포츠연맹");
        dto.setOrganizer("전라남도롤러스포츠연맹");
        return dto;
    }

    private Competition createCompetition() {
        return Competition.builder()
                .name("테스트 대회")
                .shortName("테스트")
                .startDate(LocalDate.of(2025, 6, 20))
                .endDate(LocalDate.of(2025, 6, 22))
                .durationDays(3)
                .venue("나주시")
                .host("대한롤러스포츠연맹")
                .organizer("전라남도롤러스포츠연맹")
                .build();
    }

    @Test
    @DisplayName("전체 목록 조회")
    void findAll() {
        given(competitionRepository.findAll()).willReturn(List.of(createCompetition()));

        List<Competition> result = competitionService.findAll();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("ID로 조회 - 존재하는 경우")
    void findById() {
        Competition competition = createCompetition();
        given(competitionRepository.findById(1L)).willReturn(Optional.of(competition));

        Competition result = competitionService.findById(1L);

        assertThat(result.getName()).isEqualTo("테스트 대회");
    }

    @Test
    @DisplayName("ID로 조회 - 존재하지 않는 경우")
    void findByIdNotFound() {
        given(competitionRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("대회 생성")
    void create() {
        CompetitionFormDto dto = createDto();
        given(competitionRepository.save(any(Competition.class))).willReturn(createCompetition());

        Competition result = competitionService.create(dto);

        assertThat(result.getName()).isEqualTo("테스트 대회");
        then(competitionRepository).should().save(any(Competition.class));
    }

    @Test
    @DisplayName("대회 수정")
    void update() {
        Competition competition = createCompetition();
        given(competitionRepository.findById(1L)).willReturn(Optional.of(competition));

        CompetitionFormDto dto = createDto();
        dto.setName("수정된 대회명");

        Competition result = competitionService.update(1L, dto);

        assertThat(result.getName()).isEqualTo("수정된 대회명");
    }

    @Test
    @DisplayName("대회 삭제")
    void delete() {
        Competition competition = createCompetition();
        given(competitionRepository.findById(1L)).willReturn(Optional.of(competition));
        given(eventRepository.findByCompetitionIdOrderByFirstEventNumber(1L)).willReturn(List.of());
        given(competitionEntryRepository.findByCompetitionId(1L)).willReturn(List.of());

        competitionService.delete(1L);

        then(competitionRepository).should().delete(competition);
    }
}
