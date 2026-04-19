package kr.pe.batang.inlinedata.service.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * pdftotext -raw 모드 출력을 파싱.
 * 공백 겹침이 없어서 대부분의 개인전 PDF 포맷을 처리 가능.
 *
 * 지원 포맷:
 *  - 일반 (시간/정수 기록): 팀 [rank]? bib 이름 [Q]? [기록]? [시도]?
 *  - DTT 시간 경기: 팀 rank bib [Q]? 시간기록 [신기록]? 시도 이름   (이름이 맨 뒤)
 *  - DTT EL(기록 없음): [EL]? 팀 rank bib 시도 이름
 *  - DTT 포인트 경기: [EL]? 팀 rank bib [Q]? 정수점수 시도 이름
 *
 * 경기 타입(시간 vs 포인트)은 PDF 전체에 시간 포맷이 하나라도 등장하는지로 자동 감지.
 */
public final class RawResultLineParser {

    private RawResultLineParser() {}

    private static final Pattern TIME_RECORD = Pattern.compile("\\d+:\\d+\\.\\d+|\\d+\\.\\d{3}");

    /** 일반 포맷: 이름이 bib 다음, 기록이 뒤. rank/기록 모두 optional. */
    private static final Pattern RAW_LINE = Pattern.compile(
            "^(?:(EL|El|DF|DQ|DNS|DNF|DSQ)\\s+)?" +
            "(.+?)\\s+" +
            "(?:(\\d+|EL|DF|DQ|DNS|DNF|DSQ)\\s+)?" +
            "(\\d+)\\s+" +
            "(\\D.*?)" +            // 이름은 숫자로 시작하지 않음 (팀명 끝 숫자와 구분)
            "(?:\\s+(Q))?" +
            "(?:\\s+(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3}|\\d+))?" +
            "(?:\\s+(세계신|한국신|부별신|대회신))?" +
            "(?:\\s+([A-Z]{3}|[가-힣]{2,3}))?" +
            "(?:\\s+(W))?" +
            "\\s*$"
    );

    /** DTT 시간 경기 비-EL: 시간 기록 필수. "팀 rank bib [Q]? 시간기록 [신기록]? 시도 이름" */
    private static final Pattern DTT_TIME = Pattern.compile(
            "^(.+?)\\s+" +
            "(\\d+|EL|DF|DQ|DNS|DNF|DSQ)\\s+" +
            "(\\d+)\\s+" +
            "(?:(Q)\\s+)?" +
            "(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})\\s+" +
            "(?:(세계신|한국신|부별신|대회신)\\s+)?" +
            "([A-Z]{3}|[가-힣]{2,3})\\s+" +
            "(\\D.+?)" +
            "\\s*$"
    );

    /** DTT 시간 경기 EL/기록없음: team greedy로 "X 1" 꼴도 지원. "[EL]? 팀 rank bib 시도 이름" */
    private static final Pattern DTT_NOREC = Pattern.compile(
            "^(?:(EL|El|DF|DQ|DNS|DNF|DSQ)\\s+)?" +
            "(.+)\\s+" +
            "(\\d+|EL|DF|DQ|DNS|DNF|DSQ)\\s+" +
            "(\\d+)\\s+" +
            "([A-Z]{3}|[가-힣]{2,3})\\s+" +
            "(\\D.+?)" +
            "\\s*$"
    );

    /** DTT 포인트 경기: 정수 기록 필수. "[EL]? 팀 rank bib [Q]? 정수점수 시도 이름" */
    private static final Pattern DTT_INT = Pattern.compile(
            "^(?:(EL|El|DF|DQ|DNS|DNF|DSQ)\\s+)?" +
            "(.+?)\\s+" +
            "(\\d+|EL|DF|DQ|DNS|DNF|DSQ)\\s+" +
            "(\\d+)\\s+" +
            "(?:(Q)\\s+)?" +
            "(\\d+)\\s+" +
            "([A-Z]{3}|[가-힣]{2,3})\\s+" +
            "(\\D.+?)" +
            "\\s*$"
    );

    /** 여러 줄로 쪼개져 나온 결과 행을 누적 병합하며 파싱. */
    public static List<ParsedResult> parse(String[] lines) {
        boolean isTimeRace = detectTimeRace(lines);
        List<ParsedResult> results = new ArrayList<>();
        boolean inData = false;
        StringBuilder buffer = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("순위")) { inData = true; continue; }
            if (!inData) continue;
            if (trimmed.isEmpty()) continue;
            if (shouldSkipHeaderLine(trimmed)) continue;

            String candidate = buffer.length() > 0 ? buffer + " " + trimmed : trimmed;
            ParsedResult pr = tryParseDttOrNormal(candidate, isTimeRace);
            if (pr != null) {
                results.add(pr);
                buffer.setLength(0);
            } else {
                if (buffer.length() > 0) buffer.append(" ");
                buffer.append(trimmed);
                if (buffer.length() > 500) buffer.setLength(0); // 안전 한계
            }
        }
        return results;
    }

    private static boolean detectTimeRace(String[] lines) {
        for (String line : lines) {
            if (TIME_RECORD.matcher(line).find()) return true;
        }
        return false;
    }

    /** 경기 타입에 맞는 DTT 패턴 시도. 실패 시 RAW_LINE으로 fallback. */
    private static ParsedResult tryParseDttOrNormal(String candidate, boolean isTimeRace) {
        // 1) 시간 경기 비-EL DTT 행: 시간 기록 필수
        Matcher mt = DTT_TIME.matcher(candidate);
        if (mt.matches()) return toDttTime(mt);

        // 2) 일반 포맷 (이름이 bib 다음, 기록이 뒤): 단 DTT 라인 오매칭 검증
        Matcher m = RAW_LINE.matcher(candidate);
        if (m.matches() && !looksLikeMisparsedDttByRawLine(m)) {
            return toRaw(m);
        }

        // 3) 시간 경기의 DTT EL/기록없는 행
        if (isTimeRace) {
            Matcher mn = DTT_NOREC.matcher(candidate);
            if (mn.matches()) return toDttNoRec(mn);
        }

        // 4) 포인트 경기 DTT 행: 정수 기록
        if (!isTimeRace) {
            Matcher mi = DTT_INT.matcher(candidate);
            if (mi.matches()) return toDttInt(mi);
        }

        // 5) RAW_LINE 매칭 결과가 의심스럽더라도 최후 수단으로 사용
        if (m.matches()) return toRaw(m);
        return null;
    }

    /**
     * RAW_LINE 매칭 결과가 DTT 행을 잘못 먹었는지 확인:
     * 1) 이름이 시도 코드로 시작 + region이 비어있음
     * 2) region이 한글 2-3자인데 실제 시도 목록에 없음 (이름이 region으로 잘못 잡힘)
     */
    private static boolean looksLikeMisparsedDttByRawLine(Matcher m) {
        String name = m.group(5);
        String region = m.group(9);
        if (region == null && name != null
                && (name.matches("^[A-Z]{3}\\s+\\S.+") || name.matches("^[가-힣]{2,3}\\s+\\S.+"))) {
            return true;
        }
        if (region != null && region.matches("[가-힣]{2,3}") && !RegionNormalizer.isValidKoreanRegion(region)) {
            return true;
        }
        return false;
    }

    private static boolean shouldSkipHeaderLine(String trimmed) {
        if (trimmed.startsWith("(")) return true;
        // 이벤트 헤더 ("18 남자고등부(Men.High School) E10,000m")
        if (trimmed.matches("^\\d+(?:-\\d+)?\\s+(여|남).+")) return true;
        // 라운드 표시
        if (trimmed.startsWith("예선") || trimmed.startsWith("준결승") || trimmed.startsWith("준준결승")
                || trimmed.startsWith("결승") || trimmed.startsWith("조별결승")) return true;
        // 컬럼 라벨
        if (trimmed.matches("^(비고|이름|등번호|소속|기록|신기록|진출여부|시도|순위)(?:\\s+[가-힣]+)?\\s*$")) return true;
        // 푸터
        if (trimmed.startsWith("기록확인") || trimmed.startsWith("심판") || trimmed.startsWith("경기부장")
                || trimmed.startsWith("경기이사") || trimmed.startsWith("심판이사")
                || trimmed.startsWith("대 한") || trimmed.startsWith("대한롤러")) return true;
        if (trimmed.matches("^\\d{4}\\.\\d{2}.*")) return true;
        return false;
    }

    // ============================================================
    // Matcher → ParsedResult 변환 helpers
    // ============================================================

    private static ParsedResult toDttTime(Matcher m) {
        return buildDtt(null, m.group(1), m.group(2), m.group(3),
                m.group(4), m.group(5), m.group(6), m.group(7), m.group(8));
    }

    private static ParsedResult toDttNoRec(Matcher m) {
        return buildDtt(m.group(1), m.group(2), m.group(3), m.group(4),
                null, null, null, m.group(5), m.group(6));
    }

    private static ParsedResult toDttInt(Matcher m) {
        return buildDtt(m.group(1), m.group(2), m.group(3), m.group(4),
                m.group(5), m.group(6), null, m.group(7), m.group(8));
    }

    private static ParsedResult buildDtt(String notePrefix, String teamNameRaw, String rankStr, String bibStr,
                                         String qualification, String record, String newRecord,
                                         String region, String athleteNameRaw) {
        int bib;
        try { bib = Integer.parseInt(bibStr); } catch (NumberFormatException e) { return null; }
        region = RegionNormalizer.normalize(region);
        Integer ranking = null;
        try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}
        String note = composeNote(notePrefix, rankStr, ranking, null);
        return new ParsedResult(bib, athleteNameRaw.trim(), region, teamNameRaw.trim(),
                ranking, record, newRecord, qualification, note);
    }

    private static ParsedResult toRaw(Matcher m) {
        String notePrefix = m.group(1);
        String teamName = m.group(2).trim();
        String rankStr = m.group(3);
        int bib;
        try { bib = Integer.parseInt(m.group(4)); } catch (NumberFormatException e) { return null; }
        String athleteName = m.group(5).trim();
        String qualification = m.group(6);
        String record = m.group(7);
        String newRecord = m.group(8);
        String region = RegionNormalizer.normalize(m.group(9));
        String wMarker = m.group(10);

        Integer ranking = null;
        try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}
        String note = composeNote(notePrefix, rankStr, ranking, wMarker);

        return new ParsedResult(bib, athleteName, region, teamName, ranking, record, newRecord, qualification, note);
    }

    /** prefix(EL 등), rank 상태 문자열, W 마커 를 종합한 note 문자열 생성. */
    private static String composeNote(String notePrefix, String rankStr, Integer parsedRanking, String wMarker) {
        String note = null;
        if (notePrefix != null) {
            note = mapStatus(notePrefix);
        }
        if (rankStr != null && parsedRanking == null) {
            String sn = mapStatus(rankStr);
            note = note != null ? note + " " + sn : sn;
        }
        if (wMarker != null) {
            note = note != null ? note + " W" : "W";
        }
        return note;
    }

    private static String mapStatus(String code) {
        return switch (code) {
            case "EL", "El" -> "제외";
            case "DF", "DQ", "DSQ" -> "실격";
            case "DNF" -> "미완주";
            case "DNS" -> "미출전";
            default -> code;
        };
    }
}