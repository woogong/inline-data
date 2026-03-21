package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TeamRepositoryTest {

    @Autowired
    private TeamRepository teamRepository;

    @Test
    @DisplayName("소속 저장 및 조회")
    void saveAndFind() {
        Team team = Team.builder().name("팀에스").region("경기").build();
        Team saved = teamRepository.save(team);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("팀에스");
        assertThat(saved.getRegion()).isEqualTo("경기");
    }

    @Test
    @DisplayName("시도별 조회")
    void findByRegion() {
        teamRepository.save(Team.builder().name("팀에스").region("경기").build());
        teamRepository.save(Team.builder().name("THE LAP").region("부산").build());

        List<Team> result = teamRepository.findByRegion("경기");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("팀에스");
    }

    @Test
    @DisplayName("전체 목록 정렬 조회")
    void findAllOrdered() {
        teamRepository.save(Team.builder().name("B팀").region("부산").build());
        teamRepository.save(Team.builder().name("A팀").region("경기").build());

        List<Team> result = teamRepository.findAllByOrderByRegionAscNameAsc();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRegion()).isEqualTo("경기");
    }
}
