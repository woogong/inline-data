package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.CompetitionRepository;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.EventRoundRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * {@link ResultParsingService} end-to-end 통합 테스트.
 *
 * 전략: 실제 PDF 바이너리 대신 pdftotext의 출력을 모사한 fixture 텍스트 파일
 * ({@code src/test/resources/pdfs/*.txt})을 사용. {@link PdfTextExtractor}는 mock으로 교체.
 * 각 PDF 포맷(일반/DTT 포인트/단체전)당 대표 시나리오 1개씩.
 */
@DataJpaTest
@Import(ResultParsingService.class)
class ResultParsingServiceIT {

    @Autowired private ResultParsingService resultParsingService;
    @Autowired private CompetitionRepository competitionRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventRoundRepository eventRoundRepository;
    @Autowired private EventHeatRepository eventHeatRepository;
    @Autowired private HeatEntryRepository heatEntryRepository;
    @Autowired private EventResultRepository eventResultRepository;
    @Autowired private CompetitionEntryRepository competitionEntryRepository;

    @MockitoBean private PdfTextExtractor pdfTextExtractor;

    private Competition competition;

    @BeforeEach
    void seedCompetition() {
        competition = competitionRepository.save(Competition.builder()
                .name("2026 테스트 오픈 인라인 대회")
                .shortName("테스트")
                .startDate(LocalDate.of(2026, 4, 17))
                .endDate(LocalDate.of(2026, 4, 19))
                .durationDays(3)
                .venue("테스트시")
                .host("대한롤러스포츠연맹")
                .organizer("테스트시롤러스포츠연맹")
                .build());
    }

    @Test
    @DisplayName("일반 시간 경기(500m+D 예선) — 결과 저장 및 CompetitionEntry 자동 생성")
    void individualTimeRace() throws IOException {
        // given
        Event event = eventRepository.save(Event.builder()
                .competition(competition).divisionName("남중부").gender("M")
                .eventName("500m+D").teamEvent(false).build());
        mockPdfExtraction("individual_time_race");

        // when
        ResultParsingService.ImportResult result = resultParsingService.parseResultPdf(
                dummyPath("individual_time_race.pdf"), competition.getId());

        // then
        assertThat(result.results()).isEqualTo(3);
        assertThat(result.newEntries()).isEqualTo(3);
        assertThat(result.filesProcessed()).isEqualTo(1);

        // EventRound 자동 생성됨 (eventNumber=4, round="예선")
        List<EventRound> rounds = eventRoundRepository.findByEvent_CompetitionIdOrderByEventNumberAsc(competition.getId());
        assertThat(rounds).hasSize(1);
        assertThat(rounds.get(0).getRound()).isEqualTo("예선");
        assertThat(rounds.get(0).getEventNumber()).isEqualTo(4);

        // EventHeat (heatNumber=7) 자동 생성
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(rounds.get(0).getId());
        assertThat(heats).hasSize(1);
        assertThat(heats.get(0).getHeatNumber()).isEqualTo(7);

        // HeatEntry 및 결과
        List<HeatEntry> entries = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(heats.get(0).getId());
        assertThat(entries).hasSize(3);
        HeatEntry topEntry = entries.stream()
                .filter(e -> e.getBibNumber() == 31).findFirst().orElseThrow();
        assertThat(topEntry.getEntry().getAthleteName()).isEqualTo("길동이");
        assertThat(topEntry.getEntry().getTeamName()).isEqualTo("서울테스트클럽");

        EventResult topResult = eventResultRepository.findByHeatEntryId(topEntry.getId()).orElseThrow();
        assertThat(topResult.getRanking()).isEqualTo(1);
        assertThat(topResult.getRecord()).isEqualTo("46.993");
        assertThat(topResult.getQualification()).isEqualTo("Q");

        // CompetitionEntry 자동 생성 확인
        List<CompetitionEntry> competitionEntries = competitionEntryRepository.findAll();
        assertThat(competitionEntries).hasSize(3);
    }

    @Test
    @DisplayName("DTT 포인트 경기 — 이름이 뒤에, 정수 기록")
    void dttPointsRace() throws IOException {
        // given
        Event event = eventRepository.save(Event.builder()
                .competition(competition).divisionName("여중부").gender("F")
                .eventName("DTT500m").teamEvent(false).build());
        mockPdfExtraction("dtt_points_race");

        // when
        ResultParsingService.ImportResult result = resultParsingService.parseResultPdf(
                dummyPath("dtt_points_race.pdf"), competition.getId());

        // then
        assertThat(result.results()).isEqualTo(3);

        List<EventRound> rounds = eventRoundRepository.findByEvent_CompetitionIdOrderByEventNumberAsc(competition.getId());
        assertThat(rounds).hasSize(1);
        assertThat(rounds.get(0).getRound()).isEqualTo("결승");

        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(rounds.get(0).getId());
        assertThat(heats).hasSize(1);

        List<HeatEntry> entries = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(heats.get(0).getId());
        assertThat(entries).extracting(he -> he.getEntry().getAthleteName())
                .containsExactlyInAnyOrder("지연이", "수진이", "유나이");

        // 1위: 지연이, 기록=25, 시도=경기
        HeatEntry winner = entries.stream()
                .filter(e -> "지연이".equals(e.getEntry().getAthleteName())).findFirst().orElseThrow();
        EventResult winnerResult = eventResultRepository.findByHeatEntryId(winner.getId()).orElseThrow();
        assertThat(winnerResult.getRecord()).isEqualTo("25");
        assertThat(winner.getEntry().getRegion()).isEqualTo("경기");
        assertThat(winner.getEntry().getTeamName()).isEqualTo("테스트A팀");
    }

    @Test
    @DisplayName("단체전 계주 — layout parser만 사용, 팀명=athleteName=teamName")
    void teamRelay() throws IOException {
        // given
        Event event = eventRepository.save(Event.builder()
                .competition(competition).divisionName("남고부").gender("M")
                .eventName("3000m 계주").teamEvent(true).build());
        String layout = readFixture("team_relay.layout.txt");
        given(pdfTextExtractor.extractText(any())).willReturn(layout);

        // when
        ResultParsingService.ImportResult result = resultParsingService.parseResultPdf(
                dummyPath("team_relay.pdf"), competition.getId());

        // then
        assertThat(result.results()).isEqualTo(3);

        List<EventRound> rounds = eventRoundRepository.findByEvent_CompetitionIdOrderByEventNumberAsc(competition.getId());
        assertThat(rounds).hasSize(1);
        assertThat(rounds.get(0).getEventNumber()).isEqualTo(52);

        List<HeatEntry> entries = heatEntryRepository
                .findByHeatIdOrderByBibNumberAsc(eventHeatRepository
                        .findByEventRoundIdOrderByHeatNumberAsc(rounds.get(0).getId()).get(0).getId());
        assertThat(entries).hasSize(3);

        HeatEntry winner = entries.stream()
                .filter(e -> "테스트A고등학교".equals(e.getEntry().getAthleteName())).findFirst().orElseThrow();
        EventResult winnerResult = eventResultRepository.findByHeatEntryId(winner.getId()).orElseThrow();
        assertThat(winnerResult.getRanking()).isEqualTo(1);
        assertThat(winnerResult.getRecord()).isEqualTo("4:55.912");
        assertThat(winnerResult.getNewRecord()).isEqualTo("대회신");
        // 단체전: athleteName == teamName
        assertThat(winner.getEntry().getAthleteName()).isEqualTo(winner.getEntry().getTeamName());
    }

    @Test
    @DisplayName("재임포트 시 결과에 없는 사전 HeatEntry는 제거됨")
    void reimportRemovesUnmatchedEntries() throws IOException {
        // given: 첫 파싱으로 3명 등록
        eventRepository.save(Event.builder()
                .competition(competition).divisionName("남중부").gender("M")
                .eventName("500m+D").teamEvent(false).build());
        mockPdfExtraction("individual_time_race");
        resultParsingService.parseResultPdf(dummyPath("individual_time_race.pdf"), competition.getId());
        assertThat(heatEntryRepository.findAll()).hasSize(3);

        // when: 2명만 있는 줄어든 결과로 재파싱
        String reducedRaw = """
                2026 테스트 오픈 인라인 대회
                테스트시
                4-7 남자중학부(Men.Middle School) 500m+D
                예선7조(Heat Series - 7 Heats)
                순위
                등번호
                이름
                소속
                기록
                서울테스트클럽 1 31 길동이 Q 46.993
                세종테스트클럽 2 45 채훈이 51.671
                """;
        String reducedLayout = readFixture("individual_time_race.layout.txt")
                .replace(" 3     12    민수이               부산테스트클럽     52.001\n", "");
        given(pdfTextExtractor.extractText(any())).willReturn(reducedLayout);
        given(pdfTextExtractor.extractTextRaw(any())).willReturn(reducedRaw);
        resultParsingService.parseResultPdf(dummyPath("individual_time_race.pdf"), competition.getId());

        // then: 매칭 안 된 bib=12 엔트리는 제거됨
        assertThat(heatEntryRepository.findAll()).hasSize(2);
        assertThat(heatEntryRepository.findAll())
                .extracting(HeatEntry::getBibNumber)
                .containsExactlyInAnyOrder(31, 45);
    }

    // ============================================================
    // helpers
    // ============================================================

    private void mockPdfExtraction(String baseName) throws IOException {
        given(pdfTextExtractor.extractText(any())).willReturn(readFixture(baseName + ".layout.txt"));
        given(pdfTextExtractor.extractTextRaw(any())).willReturn(readFixture(baseName + ".raw.txt"));
    }

    private String readFixture(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("pdfs/" + filename);
        return Files.readString(resource.getFile().toPath());
    }

    private Path dummyPath(String filename) {
        return Paths.get(filename);
    }
}