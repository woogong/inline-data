package kr.pe.batang.inlinedata.service.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DivisionNormalizerTest {

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "여, 중학부,         여중부",
            "남, 중학부,         남중부",
            "여, 고등부,         여고부",
            "남, 고등부,         남고부",
            "여, 초등부,         여초부",
            "남, 초등부,         남초부",
            "남, 대학일반부,     남대일",
            "여, 대학일반부,     여대일",
            "'여', '초등부(5,6학년)',  '여초부(5,6학년)'",
            "'남', '초등부(1,2학년)',  '남초부(1,2학년)'",
    })
    @DisplayName("PDF 종별명을 DB 규격으로 변환 (학년 suffix 보존)")
    void normalize(String gender, String raw, String expected) {
        assertThat(DivisionNormalizer.normalize(gender, raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "여, 시니어, 여시니어",
            "남, 마스터, 남마스터",
    })
    @DisplayName("매핑에 없는 종별은 그대로 붙임")
    void unknownBaseUnchanged(String gender, String raw, String expected) {
        assertThat(DivisionNormalizer.normalize(gender, raw)).isEqualTo(expected);
    }
}