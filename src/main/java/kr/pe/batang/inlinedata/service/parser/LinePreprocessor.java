package kr.pe.batang.inlinedata.service.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF 결과 텍스트의 라인 전처리 유틸리티.
 * - 여러 줄로 쪼개진 "순위 등번호" 행을 한 줄로 병합
 * - 이벤트 헤더 다음에서 라운드 이름 탐지
 */
public final class LinePreprocessor {

    private LinePreprocessor() {}

    private static final Pattern RANK_BIB_ONLY = Pattern.compile(
            "^(?:\\d+|EL|DQ|DNF|DNS|DSQ|DF)\\s+\\d+\\s*$");
    private static final Pattern NEXT_RESULT_LINE = Pattern.compile(
            "^(?:\\d+|EL|DQ|DNF|DNS|DSQ|DF)\\s+\\d+(?:\\s.*)?$");
    private static final Pattern RECORD_VALUE = Pattern.compile(
            "(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})");

    /**
     * 이름이 너무 길어서 "순위 등번호"만 한 줄에 나오고 이름/소속/기록이 이어서 여러 줄로 나뉘는
     * PDF 줄바꿈을 한 줄로 합친다.
     */
    public static String[] mergeWrappedResultLines(String[] lines) {
        List<String> out = new ArrayList<>();
        boolean inData = false;
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();
            if (!inData) {
                out.add(line);
                if (trimmed.startsWith("순위")) inData = true;
                i++;
                continue;
            }
            if (RANK_BIB_ONLY.matcher(trimmed).matches()) {
                StringBuilder sb = new StringBuilder(line.stripTrailing());
                int j = i + 1;
                boolean recordFound = false;
                while (j < lines.length && !recordFound) {
                    String next = lines[j];
                    String nextTrim = next.trim();
                    if (nextTrim.isEmpty()) { j++; continue; }
                    if (isFooter(nextTrim)) break;
                    if (NEXT_RESULT_LINE.matcher(nextTrim).matches()) break;
                    sb.append("   ").append(nextTrim);
                    j++;
                    if (RECORD_VALUE.matcher(nextTrim).find()) recordFound = true;
                }
                out.add(sb.toString());
                i = j;
            } else {
                out.add(line);
                i++;
            }
        }
        return out.toArray(new String[0]);
    }

    /** 이벤트 헤더 라인 index 이후에서 "결승"/"준결승"/"준준결승"/"예선"/"조별결승" 찾기. */
    public static String parseRoundName(String[] lines, int startIdx) {
        for (int i = startIdx + 1; i < lines.length && i < startIdx + 10; i++) {
            String t = lines[i].trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("조별결승")) return "조별결승";
            if (t.startsWith("준준결승")) return "준준결승";
            if (t.startsWith("준결승")) return "준결승";
            if (t.startsWith("예선")) return "예선";
            if (t.startsWith("결승")) return "결승";
        }
        return null;
    }

    private static boolean isFooter(String trimmed) {
        return trimmed.startsWith("기록확인") || trimmed.startsWith("심판")
                || trimmed.startsWith("경기부장") || trimmed.startsWith("대한롤러")
                || trimmed.matches("^\\d{4}\\..*") || trimmed.startsWith("(");
    }
}