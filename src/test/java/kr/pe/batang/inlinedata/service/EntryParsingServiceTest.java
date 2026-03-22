package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.repository.AthleteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EntryParsingServiceTest {

    @InjectMocks
    private EntryParsingService entryParsingService;

    @Mock
    private AthleteRepository athleteRepository;

    @Test
    @DisplayName("선수 행을 파싱하여 이름, 성별, 소속 정보를 추출한다")
    void parseAthleteLines_basic() {
        String[] lines = {
                "                1 여초부 5,6학년 500m+D 예선",
                "< 1조 >",
                "   4   구예림 (경기 경기 팀에스6)",
                "  30   김나은 (충남 논산내동초등학교5)",
                "",
                "                2 남초부 5,6학년 500m+D 예선",
                "< 1조 >",
                "   1   우재원 (전남 여수롤러클럽6)",
        };

        List<EntryParsingService.ParsedAthlete> result = entryParsingService.parseAthleteLines(lines);

        assertThat(result).hasSize(3);

        assertThat(result.get(0).name()).isEqualTo("구예림");
        assertThat(result.get(0).gender()).isEqualTo("F");
        assertThat(result.get(0).notes()).isEqualTo("경기 경기 팀에스");

        assertThat(result.get(1).name()).isEqualTo("김나은");
        assertThat(result.get(1).gender()).isEqualTo("F");
        assertThat(result.get(1).notes()).isEqualTo("충남 논산내동초등학교");

        assertThat(result.get(2).name()).isEqualTo("우재원");
        assertThat(result.get(2).gender()).isEqualTo("M");
        assertThat(result.get(2).notes()).isEqualTo("전남 여수롤러클럽");
    }

    @Test
    @DisplayName("영문 이름도 파싱한다")
    void parseAthleteLines_englishName() {
        String[] lines = {
                "                1 여초부 5,6학년 500m+D 예선",
                "  68   CHENHUILIN (전북 남원월락초등학교6)",
        };

        List<EntryParsingService.ParsedAthlete> result = entryParsingService.parseAthleteLines(lines);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("CHENHUILIN");
        assertThat(result.get(0).gender()).isEqualTo("F");
        assertThat(result.get(0).notes()).isEqualTo("전북 남원월락초등학교");
    }

    @Test
    @DisplayName("학년 숫자가 없는 소속도 처리한다")
    void parseAthleteLines_noGradeNumber() {
        String[] lines = {
                "                5 여중부 E10,000m 결승",
                "   3   이시은 (경기 LHJ인라인클럽2)",
                "  18   김윤슬 (부산 THE LAP5)",
        };

        List<EntryParsingService.ParsedAthlete> result = entryParsingService.parseAthleteLines(lines);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).notes()).isEqualTo("경기 LHJ인라인클럽");
        assertThat(result.get(1).notes()).isEqualTo("부산 THE LAP");
    }

    @Test
    @DisplayName("동일 선수가 여러 종목에 출전해도 중복 제거된다")
    void parseAthleteLines_dedup() {
        String[] lines = {
                "                1 여초부 5,6학년 500m+D 예선",
                "   4   구예림 (경기 경기 팀에스6)",
                "                7 여초부 5,6학년 500m+D 준준결승",
                "   4   구예림 (경기 경기 팀에스6)",
        };

        List<EntryParsingService.ParsedAthlete> result = entryParsingService.parseAthleteLines(lines);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("비선수 행(워밍업, 제목, 일차 등)은 무시한다")
    void parseAthleteLines_ignoreNonAthleteLines() {
        String[] lines = {
                "           2026 나주 방문의 해 기념 중흥그룹•대우건설",
                "            제45회 전국 남녀 종별 인라인 스피드대회",
                "                   제1일차(2026. 3.20.금)",
                "                워밍업",
                "< 1조 >",
                "                1 여초부 5,6학년 500m+D 예선",
                "   4   구예림 (경기 경기 팀에스6)",
        };

        List<EntryParsingService.ParsedAthlete> result = entryParsingService.parseAthleteLines(lines);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("구예림");
    }
}
