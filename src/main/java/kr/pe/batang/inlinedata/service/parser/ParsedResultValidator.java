package kr.pe.batang.inlinedata.service.parser;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * 파서가 만들어낸 {@link ParsedResult}를 DB 저장 전에 검증/세척.
 *
 * 정책:
 * - athleteName이 비거나 형식이 의심스러우면 결과 전체를 버린다 (phantom 엔트리 유입 차단).
 * - region이 알려진 시도/국가 목록에 없으면 region만 null로 지우고 결과는 유지한다.
 * - teamName, 나머지 필드는 그대로 통과.
 *
 * 여기서 "의심스러운 athleteName"이란 PDF 파싱 오류로 팀명 조각이나 숫자/기호만 들어간 경우.
 * 길이 제한은 CompetitionEntry.athleteName 컬럼(VARCHAR(50))을 기준으로 한다.
 */
@Slf4j
public final class ParsedResultValidator {

    private ParsedResultValidator() {}

    private static final int MIN_NAME_LENGTH = 1;
    private static final int MAX_NAME_LENGTH = 50;

    /** 숫자/기호만 포함된 이름은 거부. 최소 1개 이상의 한글 또는 라틴 글자가 있어야 한다. */
    private static final Pattern HAS_LETTER = Pattern.compile(".*[\\p{IsHangul}A-Za-z].*");

    /**
     * 결과가 유효하면 (지역이 정리된 사본을) 반환하고, 폐기 대상이면 null을 반환.
     */
    public static ParsedResult validate(ParsedResult pr) {
        if (pr == null) return null;
        String name = pr.athleteName();
        if (name == null) return drop(pr, "이름 null");
        String trimmed = name.trim();
        if (trimmed.length() < MIN_NAME_LENGTH) return drop(pr, "이름 빈 값");
        if (trimmed.length() > MAX_NAME_LENGTH) return drop(pr, "이름 길이 초과 (" + trimmed.length() + ")");
        if (!HAS_LETTER.matcher(trimmed).matches()) return drop(pr, "이름에 문자 없음");

        String region = pr.region();
        if (region != null && !RegionNormalizer.isValidRegion(region)) {
            log.debug("유효하지 않은 region 제거: bib={} name={} region='{}'",
                    pr.bibNumber(), trimmed, region);
            region = null;
        }

        // 이름만 trim하고 나머지는 그대로. 불변 record라 새 인스턴스 반환.
        return new ParsedResult(pr.bibNumber(), trimmed, region, pr.teamName(),
                pr.ranking(), pr.record(), pr.newRecord(), pr.qualification(), pr.note());
    }

    private static ParsedResult drop(ParsedResult pr, String reason) {
        log.warn("ParsedResult 폐기: {} — bib={} name='{}' team='{}'",
                reason, pr.bibNumber(), pr.athleteName(), pr.teamName());
        return null;
    }
}