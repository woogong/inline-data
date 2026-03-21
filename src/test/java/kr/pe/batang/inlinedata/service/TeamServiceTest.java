package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.TeamFormDto;
import kr.pe.batang.inlinedata.entity.Team;
import kr.pe.batang.inlinedata.repository.TeamRepository;
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
class TeamServiceTest {

    @InjectMocks
    private TeamService teamService;

    @Mock
    private TeamRepository teamRepository;

    private TeamFormDto createDto() {
        TeamFormDto dto = new TeamFormDto();
        dto.setName("팀에스");
        dto.setRegion("경기");
        return dto;
    }

    private Team createTeam() {
        return Team.builder().name("팀에스").region("경기").build();
    }

    @Test
    @DisplayName("소속 생성")
    void create() {
        given(teamRepository.save(any(Team.class))).willReturn(createTeam());

        Team result = teamService.create(createDto());

        assertThat(result.getName()).isEqualTo("팀에스");
        then(teamRepository).should().save(any(Team.class));
    }

    @Test
    @DisplayName("소속 수정")
    void update() {
        Team team = createTeam();
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));

        TeamFormDto dto = createDto();
        dto.setName("수정된팀");

        Team result = teamService.update(1L, dto);
        assertThat(result.getName()).isEqualTo("수정된팀");
    }

    @Test
    @DisplayName("소속 삭제")
    void delete() {
        Team team = createTeam();
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));

        teamService.delete(1L);

        then(teamRepository).should().delete(team);
    }

    @Test
    @DisplayName("존재하지 않는 소속 조회")
    void findByIdNotFound() {
        given(teamRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
