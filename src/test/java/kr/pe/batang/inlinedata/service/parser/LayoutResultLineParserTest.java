package kr.pe.batang.inlinedata.service.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LayoutResultLineParser}에 대한 회귀 테스트.
 * pdftotext -layout 출력 기반. 컬럼 구분은 2+ 연속 공백.
 */
class LayoutResultLineParserTest {

    @Nested
    @DisplayName("단체전 (parseTeam)")
    class Team {

        @Test
        @DisplayName("기본 계주 결승 — 순위/레인/팀명/시도/기록")
        void basicRelay() {
            String[] lines = {
                    "52 남자고등부 3000m 계주",
                    "결승",
                    " 순위    레인           팀명                시도           기록              비고",
                    " 1       3      대구성산고등학교         대구         4:55.912              대회신",
                    " 2       1      남원공업고등학교         전북         4:56.123                   ",
                    " 3       2      경기스케이팅클럽         경기         5:00.001                    ",
                    "기록확인",
                    "2026.04.17"
            };

            List<ParsedResult> r = LayoutResultLineParser.parseTeam(lines);

            assertThat(r).hasSize(3);
            assertThat(r.get(0).ranking()).isEqualTo(1);
            assertThat(r.get(0).bibNumber()).isEqualTo(3);        // lane 번호
            assertThat(r.get(0).teamName()).isEqualTo("대구성산고등학교");
            assertThat(r.get(0).athleteName()).isEqualTo("대구성산고등학교");
            assertThat(r.get(0).region()).isEqualTo("대구");
            assertThat(r.get(0).record()).isEqualTo("4:55.912");
            assertThat(r.get(0).newRecord()).isEqualTo("대회신");

            assertThat(r.get(1).ranking()).isEqualTo(2);
            assertThat(r.get(1).teamName()).isEqualTo("남원공업고등학교");
            assertThat(r.get(1).region()).isEqualTo("전북");
        }

        @Test
        @DisplayName("EL 행 — ranking=null, note에 '제외'")
        void elStatusRow() {
            String[] lines = {
                    " 순위 레인 팀명 시도 기록",
                    " EL   4   부산스피드팀     부산"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseTeam(lines);
            assertThat(r).hasSize(1);
            assertThat(r.get(0).ranking()).isNull();
            assertThat(r.get(0).bibNumber()).isEqualTo(4);
            assertThat(r.get(0).teamName()).isEqualTo("부산스피드팀");
            assertThat(r.get(0).note()).contains("제외");
        }

        @Test
        @DisplayName("푸터/헤더 스킵")
        void skipFooter() {
            String[] lines = {
                    " 순위 레인 팀명 시도 기록",
                    " 1  3   A팀  서울    1:00.000",
                    "심판이사  홍길동",
                    "기록확인",
                    "대한롤러스포츠연맹",
                    "2026.04.17 10:47"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseTeam(lines);
            assertThat(r).hasSize(1);
        }

        @Test
        @DisplayName("행 맨 앞에 장식 Q 마커가 있는 포맷 — 119-1 여자대학일반부 계주3,000m")
        void leadingQMark() {
            String[] lines = {
                    " 순위          팀명          시도        기록      진출여부",
                    "Q     1      안동시청              경북      4:30.555      Q",
                    "             (34박민정,35양도이,36이유진)",
                    "Q     2      TEAM SUGATTI PRO   TPE     4:30.633      Q",
                    "             (14MENG-CHU LI,15YUN-CHENG LEE)",
                    "      3      전북특별자치도롤러스포츠연맹전북                4:33.625",
                    "             (3황서연,2이다연,1박진유)"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseTeam(lines);
            assertThat(r).hasSize(3);

            // 1위: 안동시청 (진출여부 Q 포함)
            assertThat(r.get(0).ranking()).isEqualTo(1);
            assertThat(r.get(0).teamName()).isEqualTo("안동시청");
            assertThat(r.get(0).region()).isEqualTo("경북");
            assertThat(r.get(0).record()).isEqualTo("4:30.555");
            assertThat(r.get(0).qualification()).isEqualTo("Q");

            // 2위: 외국 팀
            assertThat(r.get(1).ranking()).isEqualTo(2);
            assertThat(r.get(1).teamName()).isEqualTo("TEAM SUGATTI PRO");
            assertThat(r.get(1).region()).isEqualTo("TPE");
            assertThat(r.get(1).record()).isEqualTo("4:30.633");

            // 3위: 팀명+시도가 공백 없이 붙은 형태 → 분리됨
            assertThat(r.get(2).ranking()).isEqualTo(3);
            assertThat(r.get(2).teamName()).isEqualTo("전북특별자치도롤러스포츠연맹");
            assertThat(r.get(2).region()).isEqualTo("전북");
            assertThat(r.get(2).record()).isEqualTo("4:33.625");
            assertThat(r.get(2).qualification()).isNull();
        }

        @Test
        @DisplayName("순위 컬럼만 있고 레인 없는 단일숫자 포맷")
        void singleNumberRankOnly() {
            String[] lines = {
                    " 순위       팀명      시도      기록",
                    "  1   서울팀     서울    5:00.000",
                    "  2   부산팀     부산    5:05.000"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseTeam(lines);
            assertThat(r).hasSize(2);
            assertThat(r.get(0).ranking()).isEqualTo(1);
            assertThat(r.get(0).teamName()).isEqualTo("서울팀");
            assertThat(r.get(1).ranking()).isEqualTo(2);
            assertThat(r.get(1).teamName()).isEqualTo("부산팀");
        }
    }

    @Nested
    @DisplayName("개인전 layout fallback (parseIndividual)")
    class Individual {

        @Test
        @DisplayName("시도 컬럼 있는 포맷 — 이름/시도/소속/기록")
        void withRegionColumn() {
            String[] lines = {
                    " 순위 등번호         이름          시도       소속            기록         비고",
                    " 1     31    홍길동              경기      서울클럽       46.993       Q",
                    " 2     45    이채훈              세종      세종시PLS클럽   51.671"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseIndividual(lines);
            assertThat(r).hasSize(2);
            assertThat(r.get(0).ranking()).isEqualTo(1);
            assertThat(r.get(0).bibNumber()).isEqualTo(31);
            assertThat(r.get(0).athleteName()).isEqualTo("홍길동");
            assertThat(r.get(0).region()).isEqualTo("경기");
            assertThat(r.get(0).teamName()).isEqualTo("서울클럽");
            assertThat(r.get(0).record()).isEqualTo("46.993");
            assertThat(r.get(0).qualification()).isEqualTo("Q");
        }

        @Test
        @DisplayName("시도 컬럼 없는 포맷 — 이름/소속/기록")
        void withoutRegionColumn() {
            String[] lines = {
                    " 순위 등번호         이름           소속            기록",
                    " 1     31    홍길동              서울클럽       46.993       Q",
                    " 2     45    이채훈              세종시PLS클럽   51.671"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseIndividual(lines);
            assertThat(r).hasSize(2);
            assertThat(r.get(0).region()).isNull();
            assertThat(r.get(0).teamName()).isEqualTo("서울클럽");
            assertThat(r.get(0).record()).isEqualTo("46.993");
        }

        @Test
        @DisplayName("기록만 있는 다음 줄은 이전 결과에 병합 + 진출여부/신기록 흡수")
        void recordOnlyContinuation() {
            String[] lines = {
                    " 순위 등번호         이름           소속            기록",
                    " 1     31    홍길동              서울클럽",
                    "                                                     46.993   대회신    Q"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseIndividual(lines);
            assertThat(r).hasSize(1);
            assertThat(r.get(0).record()).isEqualTo("46.993");
            assertThat(r.get(0).newRecord()).isEqualTo("대회신");
            assertThat(r.get(0).qualification()).isEqualTo("Q");
        }

        @Test
        @DisplayName("팀명에 TEAM/RACING 키워드 — 공백 1개로 붙은 이름/팀 분리")
        void teamKeywordSplit() {
            String[] lines = {
                    " 순위 등번호         이름           소속            기록",
                    " 1     10    HUNG YUN-CHEN POWERSLIDE TEAM TAIWAN - A    16:03.085"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseIndividual(lines);
            assertThat(r).hasSize(1);
            assertThat(r.get(0).athleteName()).isEqualTo("HUNG YUN-CHEN");
            assertThat(r.get(0).teamName()).isEqualTo("POWERSLIDE TEAM TAIWAN - A");
            assertThat(r.get(0).record()).isEqualTo("16:03.085");
        }

        @Test
        @DisplayName("EL prefix — ranking=null, note=제외")
        void elRanking() {
            String[] lines = {
                    " 순위 등번호         이름           소속            기록",
                    " EL    32    박민제              부산클럽"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseIndividual(lines);
            assertThat(r).hasSize(1);
            assertThat(r.get(0).ranking()).isNull();
            assertThat(r.get(0).bibNumber()).isEqualTo(32);
            assertThat(r.get(0).note()).contains("제외");
        }

        @Test
        @DisplayName("팀명 끝 점수(정수) 분리")
        void teamNameTrailingScore() {
            String[] lines = {
                    " 순위 등번호         이름           소속            기록",
                    " 1     31    홍길동              한국국제조리고등학교 11"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseIndividual(lines);
            assertThat(r).hasSize(1);
            assertThat(r.get(0).teamName()).isEqualTo("한국국제조리고등학교");
            assertThat(r.get(0).record()).isEqualTo("11");
        }

        @Test
        @DisplayName("푸터/헤더 스킵")
        void skipFooter() {
            String[] lines = {
                    " 순위 등번호 이름 소속 기록",
                    " 1  31  홍길동  서울클럽   46.993",
                    "기록확인",
                    "심판이사 홍길동",
                    "경기부장 김철수",
                    "대한롤러스포츠연맹",
                    "2026.04.17 10:47",
                    "(Roller Sports)"
            };
            List<ParsedResult> r = LayoutResultLineParser.parseIndividual(lines);
            assertThat(r).hasSize(1);
        }
    }
}