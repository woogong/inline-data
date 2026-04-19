package kr.pe.batang.inlinedata.service.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinePreprocessorTest {

    @Test
    @DisplayName("rank+bib만 있는 줄 다음의 이름/소속/기록을 한 줄로 병합")
    void mergeWrappedResultLinesWithLongName() {
        String[] input = {
                " 순위 등번호                이름                    소속            기록",
                " 1      31",
                "         SYUHADA AL HAFIZH ARVAND DANISH",
                "                                       PB PORSEROSI       46.993              Q",
                " 2      45            이채훈              세종시PLS클럽           51.671"
        };

        String[] out = LinePreprocessor.mergeWrappedResultLines(input);

        // 3줄로 쪼개졌던 1위가 한 줄로 합쳐짐
        assertThat(out[1]).contains("1").contains("31").contains("SYUHADA")
                .contains("PB PORSEROSI").contains("46.993");
        // 이미 한 줄에 있던 2위는 그대로
        assertThat(out[2]).contains("이채훈").contains("51.671");
    }

    @Test
    @DisplayName("정상적으로 한 줄인 라인은 병합되지 않고 그대로 유지")
    void noMergeForNormalLines() {
        String[] input = {
                " 순위 등번호                이름           소속       기록",
                " 1      8         CHEN YU LUN         NL-Racing Team     15:59.956    대회신",
                " 2      24      CHIN-TENG TSAI       TEAM SUGATTI PRO    16:00.381    대회신"
        };
        String[] out = LinePreprocessor.mergeWrappedResultLines(input);

        assertThat(out).hasSize(3);
        assertThat(out[1]).contains("CHEN YU LUN").contains("15:59.956");
        assertThat(out[2]).contains("CHIN-TENG TSAI").contains("16:00.381");
    }

    @Test
    @DisplayName("헤더 '순위' 이전 줄들은 병합 대상 아님")
    void beforeHeaderLinesUntouched() {
        String[] input = {
                "2026 남원 코리아 오픈",
                "전북특별자치도 남원시",
                " 순위 등번호",
                " 1     31",
                "         SYUHADA AL HAFIZH ARVAND DANISH",
                "                                       PB PORSEROSI       46.993              Q"
        };
        String[] out = LinePreprocessor.mergeWrappedResultLines(input);
        assertThat(out[0]).isEqualTo("2026 남원 코리아 오픈");
        assertThat(out[1]).isEqualTo("전북특별자치도 남원시");
    }

    @Test
    @DisplayName("라운드 이름 - 결승")
    void parseRoundNameFinal() {
        String[] lines = {
                "18 남자고등부(Men.High School) E10,000m",
                "결승(Final)"
        };
        assertThat(LinePreprocessor.parseRoundName(lines, 0)).isEqualTo("결승");
    }

    @Test
    @DisplayName("라운드 이름 - 준준결승/준결승 구분")
    void parseRoundNameSemiQuarter() {
        assertThat(LinePreprocessor.parseRoundName(
                new String[]{"11 여중부 500m+D", "준준결승경기1(Quarter Final 1)"}, 0))
                .isEqualTo("준준결승");
        assertThat(LinePreprocessor.parseRoundName(
                new String[]{"19 여중부 500m+D", "준결승경기1(Semi Final 1)"}, 0))
                .isEqualTo("준결승");
    }

    @Test
    @DisplayName("라운드 이름 - 예선/조별결승")
    void parseRoundNameHeats() {
        assertThat(LinePreprocessor.parseRoundName(
                new String[]{"4-7 남자중학부 500m+D", "예선7조(Heat Series - 7 Heats)"}, 0))
                .isEqualTo("예선");
        assertThat(LinePreprocessor.parseRoundName(
                new String[]{"60 여초부 300m", "조별결승"}, 0))
                .isEqualTo("조별결승");
    }

    @Test
    @DisplayName("라운드 이름을 못 찾으면 null")
    void parseRoundNameNotFound() {
        assertThat(LinePreprocessor.parseRoundName(
                new String[]{"some line", "another line"}, 0)).isNull();
    }
}