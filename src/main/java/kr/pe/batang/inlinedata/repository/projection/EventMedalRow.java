package kr.pe.batang.inlinedata.repository.projection;

/**
 * 종목 탭에 표시할 메달(1~3위) 프로젝션.
 * 엔티티 전체를 로드하지 않고 필요한 컬럼만 가져와 N+1 lazy load를 회피한다.
 */
public record EventMedalRow(Long eventId, Integer ranking, String athleteName, Long athleteId) {
}