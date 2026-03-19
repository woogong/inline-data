package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.Competition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CompetitionRepositoryTest {

    @Autowired
    private CompetitionRepository competitionRepository;

    private Competition createCompetition(String name) {
        return Competition.builder()
                .name(name)
                .startDate(LocalDate.of(2025, 6, 20))
                .endDate(LocalDate.of(2025, 6, 22))
                .durationDays(3)
                .venue("나주시")
                .venueDetail("나주롤러경기장/200m 트랙")
                .host("대한롤러스포츠연맹")
                .organizer("전라남도롤러스포츠연맹")
                .build();
    }

    @Test
    @DisplayName("대회 저장 및 조회")
    void saveAndFind() {
        Competition competition = createCompetition("제45회 전국 남녀 종별 인라인 스피드대회");
        Competition saved = competitionRepository.save(competition);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("제45회 전국 남녀 종별 인라인 스피드대회");
    }

    @Test
    @DisplayName("전체 목록 조회")
    void findAll() {
        competitionRepository.save(createCompetition("대회1"));
        competitionRepository.save(createCompetition("대회2"));

        List<Competition> list = competitionRepository.findAll();
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("대회 삭제")
    void delete() {
        Competition saved = competitionRepository.save(createCompetition("삭제 대회"));
        Long id = saved.getId();

        competitionRepository.delete(saved);

        Optional<Competition> found = competitionRepository.findById(id);
        assertThat(found).isEmpty();
    }
}
