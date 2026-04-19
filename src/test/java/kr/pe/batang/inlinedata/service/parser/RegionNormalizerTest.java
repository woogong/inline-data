package kr.pe.batang.inlinedata.service.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegionNormalizerTest {

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource({
            "TPE, 대만",
            "HKG, 홍콩",
            "JPN, 일본",
            "AUS, 호주",
            "CHN, 중국",
            "CHINA, 중국",
            "IDN, 인도네시아",
            "INA, 인도네시아",
            "CHI, 칠레",
            "KOR, 한국",
            "NZL, 뉴질랜드",
    })
    @DisplayName("IOC 코드는 한글 국명으로 정규화")
    void normalizeIocCode(String input, String expected) {
        assertThat(RegionNormalizer.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "한국 시도 {0}은 그대로")
    @CsvSource({"서울", "부산", "경기", "전북", "충남", "제주"})
    @DisplayName("한국 시도는 변환 없이 그대로")
    void koreanRegionUnchanged(String region) {
        assertThat(RegionNormalizer.normalize(region)).isEqualTo(region);
    }

    @Test
    @DisplayName("알 수 없는 값은 그대로 반환")
    void unknownReturnsAsIs() {
        assertThat(RegionNormalizer.normalize("XXX")).isEqualTo("XXX");
        assertThat(RegionNormalizer.normalize("임의문자열")).isEqualTo("임의문자열");
    }

    @Test
    @DisplayName("null 입력은 null 반환")
    void nullSafe() {
        assertThat(RegionNormalizer.normalize(null)).isNull();
    }

    @Test
    @DisplayName("유효한 한국 시도 판별")
    void isValidKoreanRegion() {
        assertThat(RegionNormalizer.isValidKoreanRegion("경기")).isTrue();
        assertThat(RegionNormalizer.isValidKoreanRegion("전국")).isTrue();
        assertThat(RegionNormalizer.isValidKoreanRegion("김가람")).isFalse(); // 이름
        assertThat(RegionNormalizer.isValidKoreanRegion("대만")).isFalse();   // 국가는 시도 아님
        assertThat(RegionNormalizer.isValidKoreanRegion(null)).isFalse();
    }
}