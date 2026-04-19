package kr.pe.batang.inlinedata.service.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 종별(division) 이름 정규화.
 * PDF의 "여자중학부" → DB의 "여중부" 형태로 변환.
 * 학년 suffix "(5,6학년)"는 보존.
 */
public final class DivisionNormalizer {

    private DivisionNormalizer() {}

    private static final Pattern GRADE_SUFFIX = Pattern.compile("(\\(.+\\))$");

    /** PDF 원본 종별명을 DB 규격으로 변환. gender는 "여" 또는 "남". */
    public static String normalize(String gender, String raw) {
        String suffix = "";
        Matcher g = GRADE_SUFFIX.matcher(raw);
        if (g.find()) {
            suffix = g.group(1);
            raw = raw.substring(0, g.start()).trim();
        }
        String base = switch (raw) {
            case "중학부" -> "중부";
            case "고등부" -> "고부";
            case "초등부" -> "초부";
            case "대학일반부" -> "대일";
            default -> raw;
        };
        return gender + base + suffix;
    }
}