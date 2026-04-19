package kr.pe.batang.inlinedata.service.parser;

/**
 * 결과 PDF 한 행(=선수/팀 한 명)을 파싱한 결과.
 * 개인전의 경우 athleteName은 선수명, teamName은 소속.
 * 단체전의 경우 athleteName과 teamName 모두 팀명을 갖는다.
 */
public record ParsedResult(int bibNumber, String athleteName, String region, String teamName,
                           Integer ranking, String record, String newRecord,
                           String qualification, String note) {}