package kr.pe.batang.inlinedata.service.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RawResultLineParser}에 대한 회귀 테스트 모음.
 * 최근 수정에서 발생했던 각 PDF 포맷의 대표 샘플을 모두 포함한다.
 */
class RawResultLineParserTest {

    /** pdftotext -raw 출력을 모사한 텍스트 header. 모든 테스트에서 공통 prefix로 사용. */
    private static String withHeader(String... dataLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("2026 남원 코리아 오픈 (스피드 트랙)\n");
        sb.append("전북특별자치도 남원시\n");
        sb.append("순위\n(Rank)\n등번호\n(No.)\n");
        sb.append("이름\n(Name)\n소속\n(Team)\n기록\n(Record)\n시도\n(Reg.)\n");
        for (String line : dataLines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private static List<ParsedResult> parse(String... dataLines) {
        return RawResultLineParser.parse(withHeader(dataLines).split("\n"));
    }

    @Nested
    @DisplayName("시간 경기 (time race)")
    class TimeRace {

        @Test
        @DisplayName("일반 포맷 - 4-7 예선1조 500m+D")
        void normalTimeRaceNoRegion() {
            List<ParsedResult> r = parse(
                    "PB PORSEROSI 1 31 SYUHADA AL HAFIZH ARVAND DANISH Q 46.993",
                    "세종시PLS클럽 2 45 이채훈 51.671"
            );
            assertThat(r).hasSize(2);
            assertThat(r.get(0).bibNumber()).isEqualTo(31);
            assertThat(r.get(0).athleteName()).isEqualTo("SYUHADA AL HAFIZH ARVAND DANISH");
            assertThat(r.get(0).teamName()).isEqualTo("PB PORSEROSI");
            assertThat(r.get(0).ranking()).isEqualTo(1);
            assertThat(r.get(0).qualification()).isEqualTo("Q");
            assertThat(r.get(0).record()).isEqualTo("46.993");
        }

        @Test
        @DisplayName("시간+신기록+시도+W — 18 결승 E10000m")
        void timeWithAllExtras() {
            List<ParsedResult> r = parse(
                    "TAKINO TEAM TAIWAN - A 1 10 PAN CHENG-YU 15:19.333 대회신 TPE W"
            );
            assertThat(r).hasSize(1);
            ParsedResult pr = r.get(0);
            assertThat(pr.teamName()).isEqualTo("TAKINO TEAM TAIWAN - A");
            assertThat(pr.ranking()).isEqualTo(1);
            assertThat(pr.bibNumber()).isEqualTo(10);
            assertThat(pr.athleteName()).isEqualTo("PAN CHENG-YU");
            assertThat(pr.record()).isEqualTo("15:19.333");
            assertThat(pr.newRecord()).isEqualTo("대회신");
            assertThat(pr.region()).isEqualTo("대만"); // TPE → 대만 정규화
            assertThat(pr.note()).isEqualTo("W");
        }

        @Test
        @DisplayName("EL prefix + 기록 없음 — 18 결승 EL 행")
        void elRowNoRecord() {
            List<ParsedResult> r = parse(
                    "El TAKINO TEAM TAIWAN - A 6 15 CHANG TSEN-TANG"
            );
            assertThat(r).hasSize(1);
            assertThat(r.get(0).athleteName()).isEqualTo("CHANG TSEN-TANG");
            assertThat(r.get(0).ranking()).isEqualTo(6);
            assertThat(r.get(0).bibNumber()).isEqualTo(15);
            assertThat(r.get(0).note()).contains("제외");
        }

        @Test
        @DisplayName("DTT 포맷 (이름이 뒤) — 61 E3000 결승")
        void dttTimeRaceNameAtEnd() {
            List<ParsedResult> r = parse(
                    "national sports 1 65 5:08.858 경기 김은민",
                    "TAKINO TEAM TAIWAN - D 2 11 5:09.025 TPE HSU CHIH-CHING"
            );
            assertThat(r).hasSize(2);
            assertThat(r.get(0).athleteName()).isEqualTo("김은민");
            assertThat(r.get(0).teamName()).isEqualTo("national sports");
            assertThat(r.get(0).record()).isEqualTo("5:08.858");
            assertThat(r.get(0).region()).isEqualTo("경기");

            assertThat(r.get(1).athleteName()).isEqualTo("HSU CHIH-CHING");
            assertThat(r.get(1).teamName()).isEqualTo("TAKINO TEAM TAIWAN - D");
            assertThat(r.get(1).region()).isEqualTo("대만");
        }

        @Test
        @DisplayName("DTT + 숫자로 끝나는 팀명 — 'Powerslide China 1 5 40 ...'")
        void dttTeamEndsWithDigit() {
            List<ParsedResult> r = parse(
                    "Powerslide China 1 5 40 5:10.016 CHN CHENG YUE RU"
            );
            assertThat(r).hasSize(1);
            assertThat(r.get(0).teamName()).isEqualTo("Powerslide China 1");  // "1"이 팀명 끝
            assertThat(r.get(0).ranking()).isEqualTo(5);
            assertThat(r.get(0).bibNumber()).isEqualTo(40);
            assertThat(r.get(0).athleteName()).isEqualTo("CHENG YUE RU");
            assertThat(r.get(0).region()).isEqualTo("중국");  // CHN → 중국
        }

        @Test
        @DisplayName("DTT EL 기록 없음 + 숫자 팀명 — '61 EL Powerslide China 1 10 43 CHN ...'")
        void dttElWithDigitTeam() {
            // 같은 이벤트에 시간 기록이 있어야 isTimeRace=true → DTT_NOREC 경로로 빠짐
            List<ParsedResult> r = parse(
                    "TAKINO TEAM TAIWAN - A 1 10 PAN CHENG-YU 15:19.333 TPE",
                    "EL Powerslide China 1 10 43 CHN ZHOU LE QIAN"
            );
            assertThat(r).hasSize(2);
            ParsedResult el = r.get(1);
            assertThat(el.teamName()).isEqualTo("Powerslide China 1");
            assertThat(el.ranking()).isEqualTo(10);
            assertThat(el.bibNumber()).isEqualTo(43);
            assertThat(el.athleteName()).isEqualTo("ZHOU LE QIAN");
            assertThat(el.note()).contains("제외");
        }

        @Test
        @DisplayName("DTT + Q 마커 — 43-4 DTT200m")
        void dttWithQualification() {
            List<ParsedResult> r = parse(
                    "THE LAP 1 48 Q 22.094 부산 김윤슬",
                    "전주인라인클럽(김민규) 2 6 22.985 전북 이루희"
            );
            assertThat(r).hasSize(2);
            assertThat(r.get(0).qualification()).isEqualTo("Q");
            assertThat(r.get(0).record()).isEqualTo("22.094");
            assertThat(r.get(0).region()).isEqualTo("부산");
            assertThat(r.get(0).athleteName()).isEqualTo("김윤슬");

            assertThat(r.get(1).teamName()).isEqualTo("전주인라인클럽(김민규)"); // 괄호 포함 팀명
        }
    }

    @Nested
    @DisplayName("포인트 경기 (points race)")
    class PointsRace {

        @Test
        @DisplayName("일반 포맷 P5000 예선 — 이름이 bib 다음, 정수 점수")
        void pointsRaceNonDtt() {
            List<ParsedResult> r = parse(
                    "강릉시청 1 41 임성욱 Q 13 강원",
                    "TAKINO TEAM TAIWAN - A 13 15 HUANG SHAO YUNG 0 TPE"
            );
            assertThat(r).hasSize(2);
            assertThat(r.get(0).athleteName()).isEqualTo("임성욱");
            assertThat(r.get(0).record()).isEqualTo("13");
            assertThat(r.get(0).region()).isEqualTo("강원");

            assertThat(r.get(1).athleteName()).isEqualTo("HUANG SHAO YUNG");
            assertThat(r.get(1).record()).isEqualTo("0");
            assertThat(r.get(1).region()).isEqualTo("대만");
        }

        @Test
        @DisplayName("DTT 포맷 P5000 결승 — 이름이 뒤, 정수 점수")
        void pointsRaceDtt() {
            List<ParsedResult> r = parse(
                    "TAKINO TEAM TAIWAN - A 1 10 25 TPE PAN CHENG-YU",
                    "EL 부산광역시롤러스포츠연맹 EL 32 0 부산 박민제"
            );
            assertThat(r).hasSize(2);
            ParsedResult top = r.get(0);
            assertThat(top.teamName()).isEqualTo("TAKINO TEAM TAIWAN - A");
            assertThat(top.ranking()).isEqualTo(1);
            assertThat(top.bibNumber()).isEqualTo(10);
            assertThat(top.record()).isEqualTo("25");
            assertThat(top.region()).isEqualTo("대만");
            assertThat(top.athleteName()).isEqualTo("PAN CHENG-YU");

            ParsedResult el = r.get(1);
            assertThat(el.teamName()).isEqualTo("부산광역시롤러스포츠연맹");
            assertThat(el.ranking()).isNull();       // rank=EL → null
            assertThat(el.bibNumber()).isEqualTo(32);
            assertThat(el.record()).isEqualTo("0");
            assertThat(el.region()).isEqualTo("부산");
            assertThat(el.note()).contains("제외");
        }
    }

    @Nested
    @DisplayName("예선 통과/탈락만 (rank 없음, 기록 없음)")
    class QualOnly {

        @Test
        @DisplayName("33-1 여초부 E3000 예선 — Q/EL만 표기")
        void qualOnlyNoRankNoRec() {
            List<ParsedResult> r = parse(
                    "경기씨더블유 68 강리원 Q 경기",
                    "EL 남원월락초등학교 3 진은희 전북"
            );
            assertThat(r).hasSize(2);
            assertThat(r.get(0).ranking()).isNull();
            assertThat(r.get(0).bibNumber()).isEqualTo(68);
            assertThat(r.get(0).athleteName()).isEqualTo("강리원");
            assertThat(r.get(0).qualification()).isEqualTo("Q");
            assertThat(r.get(0).region()).isEqualTo("경기");
            assertThat(r.get(0).record()).isNull();

            assertThat(r.get(1).bibNumber()).isEqualTo(3);
            assertThat(r.get(1).athleteName()).isEqualTo("진은희");
            assertThat(r.get(1).note()).contains("제외");
        }

        @Test
        @DisplayName("DNS prefix + rank=DNS")
        void dnsPrefix() {
            List<ParsedResult> r = parse(
                    "DNS 성호중학교 DNS 53 김동준 경기"
            );
            assertThat(r).hasSize(1);
            assertThat(r.get(0).teamName()).isEqualTo("성호중학교");
            assertThat(r.get(0).bibNumber()).isEqualTo(53);
            assertThat(r.get(0).athleteName()).isEqualTo("김동준");
            assertThat(r.get(0).note()).contains("미출전");
        }
    }

    @Nested
    @DisplayName("팀명이 여러 줄로 쪼개진 -raw 출력")
    class WrappedTeamNames {

        @Test
        @DisplayName("라인 누적 병합으로 팀명 재구성")
        void teamSpansMultipleLines() {
            // pdftotext -raw가 긴 팀명을 여러 줄로 쪼개서 내는 경우
            List<ParsedResult> r = parse(
                    "TAKINO TEAM TAIWAN -",
                    "A",
                    "1 10 PAN CHENG-YU 15:19.333 대회신 TPE W"
            );
            assertThat(r).hasSize(1);
            assertThat(r.get(0).teamName()).isEqualTo("TAKINO TEAM TAIWAN - A");
            assertThat(r.get(0).athleteName()).isEqualTo("PAN CHENG-YU");
        }

        @Test
        @DisplayName("3줄 누적 — Hong Kong China Federation of Roller")
        void threeLineTeamAccumulation() {
            List<ParsedResult> r = parse(
                    "EL Hong Kong China",
                    "Federation of Roller",
                    "22 47 MAN KENNETH HKG"
            );
            assertThat(r).hasSize(1);
            assertThat(r.get(0).teamName()).isEqualTo("Hong Kong China Federation of Roller");
            assertThat(r.get(0).athleteName()).isEqualTo("MAN KENNETH");
            assertThat(r.get(0).region()).isEqualTo("홍콩");
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("팀명 악센트 문자 포함 — 'CATALINA ANTONIA PASTÉN LE-FORT'")
        void accentInName() {
            List<ParsedResult> r = parse(
                    "TEAM SUGATTI ELITE 6 32 1:34.586 CHI CATALINA ANTONIA PASTÉN LE-FORT"
            );
            assertThat(r).hasSize(1);
            assertThat(r.get(0).athleteName()).isEqualTo("CATALINA ANTONIA PASTÉN LE-FORT");
            assertThat(r.get(0).region()).isEqualTo("칠레");
        }

        @Test
        @DisplayName("이름에 하이픈 + 공백 — 'TENG SHU- JUNG'")
        void nameWithHyphenSpace() {
            List<ParsedResult> r = parse(
                    "POWERSLIDE TEAM TAIWAN - B 3 14 57.823 TPE TENG SHU- JUNG"
            );
            assertThat(r).hasSize(1);
            assertThat(r.get(0).athleteName()).isEqualTo("TENG SHU- JUNG");
            assertThat(r.get(0).teamName()).isEqualTo("POWERSLIDE TEAM TAIWAN - B");
        }

        @Test
        @DisplayName("푸터와 머리말은 스킵")
        void headersAndFootersSkipped() {
            List<ParsedResult> r = parse(
                    "경기씨더블유 68 강리원 Q 경기",
                    "심판이사",
                    "기록확인",
                    "대 한 롤 러 스 포 츠 연 맹",
                    "2026.04.17 10:47"
            );
            assertThat(r).hasSize(1);
            assertThat(r.get(0).athleteName()).isEqualTo("강리원");
        }

        @Test
        @DisplayName("이름이 region 코드로 시작하는 RAW 오매칭 방지 — DTT fallback")
        void misparseGuard() {
            // 시간 기록이 있는 라인과 함께 있어야 isTimeRace=true → NOREC 경로로 fallback
            List<ParsedResult> r = parse(
                    "TAKINO TEAM TAIWAN - A 1 10 PAN CHENG-YU 15:19.333 TPE",
                    "EL Powerslide China 1 10 43 CHN ZHOU LE QIAN"
            );
            assertThat(r).hasSize(2);
            assertThat(r.get(1).athleteName()).isEqualTo("ZHOU LE QIAN");
            assertThat(r.get(1).region()).isEqualTo("중국");
        }
    }
}