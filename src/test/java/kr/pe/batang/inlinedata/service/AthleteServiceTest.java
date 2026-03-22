package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.AthleteFormDto;
import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.repository.AthleteRepository;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
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
class AthleteServiceTest {

    @InjectMocks
    private AthleteService athleteService;

    @Mock private AthleteRepository athleteRepository;
    @Mock private CompetitionEntryRepository competitionEntryRepository;
    @Mock private HeatEntryRepository heatEntryRepository;
    @Mock private EventResultRepository eventResultRepository;

    private AthleteFormDto createDto() {
        AthleteFormDto dto = new AthleteFormDto();
        dto.setName("구예림");
        dto.setGender("F");
        return dto;
    }

    private Athlete createAthlete() {
        return Athlete.builder().name("구예림").gender("F").build();
    }

    @Test
    @DisplayName("선수 생성")
    void create() {
        given(athleteRepository.save(any(Athlete.class))).willReturn(createAthlete());

        Athlete result = athleteService.create(createDto());

        assertThat(result.getName()).isEqualTo("구예림");
        then(athleteRepository).should().save(any(Athlete.class));
    }

    @Test
    @DisplayName("선수 수정")
    void update() {
        Athlete athlete = createAthlete();
        given(athleteRepository.findById(1L)).willReturn(Optional.of(athlete));

        AthleteFormDto dto = createDto();
        dto.setName("김선수");

        Athlete result = athleteService.update(1L, dto);
        assertThat(result.getName()).isEqualTo("김선수");
    }

    @Test
    @DisplayName("선수 삭제")
    void delete() {
        Athlete athlete = createAthlete();
        given(athleteRepository.findById(1L)).willReturn(Optional.of(athlete));
        given(competitionEntryRepository.findByAthleteId(1L)).willReturn(List.of());

        athleteService.delete(1L);

        then(athleteRepository).should().delete(athlete);
    }

    @Test
    @DisplayName("존재하지 않는 선수 조회")
    void findByIdNotFound() {
        given(athleteRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> athleteService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
