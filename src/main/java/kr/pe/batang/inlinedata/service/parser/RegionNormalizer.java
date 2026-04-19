package kr.pe.batang.inlinedata.service.parser;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 지역(시도/국가) 이름 정규화.
 * IOC 3글자 국가 코드(TPE, HKG 등) → 한글 국명 변환,
 * 유효한 한국 시도 이름 목록 제공.
 */
public final class RegionNormalizer {

    private RegionNormalizer() {}

    public static final Set<String> KOREAN_REGIONS = Set.of(
            "서울","부산","대구","인천","광주","대전","울산","세종","경기","강원",
            "충북","충남","전북","전남","경북","경남","제주","전국");

    private static final Map<String, String> CODE_TO_KOREAN = Map.ofEntries(
            Map.entry("TPE", "대만"), Map.entry("HKG", "홍콩"), Map.entry("JPN", "일본"),
            Map.entry("AUS", "호주"), Map.entry("CHN", "중국"), Map.entry("CHINA", "중국"),
            Map.entry("IDN", "인도네시아"), Map.entry("CHI", "칠레"), Map.entry("KOR", "한국"),
            Map.entry("NZL", "뉴질랜드"), Map.entry("USA", "미국"), Map.entry("CAN", "캐나다"),
            Map.entry("MAC", "마카오"), Map.entry("SGP", "싱가포르"), Map.entry("MAS", "말레이시아"),
            Map.entry("THA", "태국"), Map.entry("VIE", "베트남"), Map.entry("INA", "인도네시아"),
            Map.entry("PHI", "필리핀"), Map.entry("IND", "인도"), Map.entry("GER", "독일"),
            Map.entry("FRA", "프랑스"), Map.entry("ITA", "이탈리아"), Map.entry("ESP", "스페인"),
            Map.entry("GBR", "영국"), Map.entry("NED", "네덜란드"), Map.entry("BEL", "벨기에"));

    /** 정규화된 유효 지역 전체 집합 (한국 시도 ∪ IOC 매핑의 한글 국명). */
    private static final Set<String> VALID_REGIONS;
    static {
        Set<String> s = new HashSet<>(KOREAN_REGIONS);
        s.addAll(CODE_TO_KOREAN.values());
        VALID_REGIONS = Set.copyOf(s);
    }

    /** IOC 코드면 한글 국명으로 변환, 아니면 입력값 그대로 반환. */
    public static String normalize(String region) {
        if (region == null) return null;
        String mapped = CODE_TO_KOREAN.get(region);
        return mapped != null ? mapped : region;
    }

    public static boolean isValidKoreanRegion(String region) {
        return region != null && KOREAN_REGIONS.contains(region);
    }

    /** 정규화된 지역명이 유효한지 (한국 시도 또는 알려진 국명) 확인. */
    public static boolean isValidRegion(String region) {
        return region != null && VALID_REGIONS.contains(region);
    }
}