package kr.pe.batang.inlinedata.service.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParsedResultValidatorTest {

    private static ParsedResult make(String name, String region, String team) {
        return new ParsedResult(10, name, region, team, 1, "46.993", null, "Q", null);
    }

    @Test
    @DisplayName("정상 결과는 이름 trim만 거쳐 통과")
    void validNameTrimmed() {
        ParsedResult out = ParsedResultValidator.validate(make("  길동이  ", "경기", "서울클럽"));
        assertThat(out).isNotNull();
        assertThat(out.athleteName()).isEqualTo("길동이");
        assertThat(out.region()).isEqualTo("경기");
        assertThat(out.teamName()).isEqualTo("서울클럽");
    }

    @Test
    @DisplayName("이름이 null이면 폐기")
    void nullNameDropped() {
        assertThat(ParsedResultValidator.validate(make(null, "경기", "서울"))).isNull();
    }

    @Test
    @DisplayName("이름이 공백이면 폐기")
    void blankNameDropped() {
        assertThat(ParsedResultValidator.validate(make("   ", "경기", "서울"))).isNull();
    }

    @Test
    @DisplayName("이름이 숫자/기호뿐이면 폐기 (PDF 파싱 오류로 들어온 쓰레기 차단)")
    void numericOnlyNameDropped() {
        assertThat(ParsedResultValidator.validate(make("123", "경기", "서울"))).isNull();
        assertThat(ParsedResultValidator.validate(make("---", "경기", "서울"))).isNull();
    }

    @Test
    @DisplayName("이름이 너무 길면 폐기 (DB 컬럼 길이 초과)")
    void overlongNameDropped() {
        String longName = "가".repeat(51);
        assertThat(ParsedResultValidator.validate(make(longName, "경기", "서울"))).isNull();
    }

    @Test
    @DisplayName("유효하지 않은 region은 null로 제거하되 결과는 유지")
    void invalidRegionStripped() {
        ParsedResult out = ParsedResultValidator.validate(make("길동이", "김가람", "서울"));
        assertThat(out).isNotNull();
        assertThat(out.athleteName()).isEqualTo("길동이");
        assertThat(out.region()).isNull();
    }

    @Test
    @DisplayName("한국 시도는 유효")
    void koreanRegionPreserved() {
        assertThat(ParsedResultValidator.validate(make("길동", "서울", null)).region()).isEqualTo("서울");
        assertThat(ParsedResultValidator.validate(make("길동", "전북", null)).region()).isEqualTo("전북");
    }

    @Test
    @DisplayName("정규화된 국명은 유효")
    void countryNamePreserved() {
        assertThat(ParsedResultValidator.validate(make("길동", "대만", null)).region()).isEqualTo("대만");
        assertThat(ParsedResultValidator.validate(make("길동", "홍콩", null)).region()).isEqualTo("홍콩");
    }

    @Test
    @DisplayName("region이 null이면 그대로 null 반환")
    void nullRegionOk() {
        ParsedResult out = ParsedResultValidator.validate(make("길동", null, "서울"));
        assertThat(out).isNotNull();
        assertThat(out.region()).isNull();
    }
}