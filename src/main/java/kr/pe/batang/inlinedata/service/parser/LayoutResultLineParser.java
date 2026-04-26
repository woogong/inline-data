package kr.pe.batang.inlinedata.service.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * pdftotext -layout 모드 출력을 파싱.
 * 2+ 공백 기반 컬럼 분리. 단체전(계주/팀DTT) 전용.
 * 개인전은 {@link RawResultLineParser}를 사용하지만 raw 추출 실패 시 fallback으로 사용.
 */
public final class LayoutResultLineParser {

    private LayoutResultLineParser() {}

    private static final Pattern RECORD_ONLY_LINE = Pattern.compile(
            "^\\s+(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})(.*)$");
    private static final Pattern RESULT_ROW = Pattern.compile(
            "^(?:(\\d+|EL|DQ|DNF|DNS|DSQ|DF)\\s+)?(\\d+)\\s+(.+)$");
    private static final Pattern TIME_RECORD = Pattern.compile(
            "(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})");
    private static final Pattern TRAILING_RECORD = Pattern.compile(
            "\\s*(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3}).*$");

    /** 개인전 결과 행을 layout 모드로 파싱. */
    public static List<ParsedResult> parseIndividual(String[] lines) {
        List<ParsedResult> results = new ArrayList<>();
        boolean inData = false;
        boolean hasRegionColumn = detectRegionColumn(lines);

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("순위")) { inData = true; continue; }
            if (!inData) continue;
            if (trimmed.isEmpty()) continue;
            if (isFooter(trimmed)) continue;

            // 기록만 있는 줄 → 이전 결과에 병합
            Matcher contMatch = RECORD_ONLY_LINE.matcher(lines[i]);
            if (contMatch.matches() && !results.isEmpty()) {
                ParsedResult prev = results.removeLast();
                String record = contMatch.group(1);
                String extra = contMatch.group(2).trim();
                String newRecord = prev.newRecord();
                String qualification = prev.qualification();
                String note = prev.note();
                if (extra.contains("세계신")) newRecord = "세계신";
                else if (extra.contains("한국신")) newRecord = "한국신";
                else if (extra.contains("부별신")) newRecord = "부별신";
                else if (extra.contains("대회신")) newRecord = "대회신";
                if (extra.contains("Q")) qualification = "Q";
                results.add(new ParsedResult(prev.bibNumber(), prev.athleteName(), prev.region(),
                        prev.teamName(), prev.ranking(), record, newRecord, qualification, note));
                continue;
            }

            Matcher m = RESULT_ROW.matcher(trimmed);
            if (!m.matches()) continue;

            String rankStr = m.group(1);
            int bib = Integer.parseInt(m.group(2));
            String rest = m.group(3).trim();

            Integer ranking = null;
            if (rankStr != null) {
                try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}
            }

            String athleteName = null, region = null, teamName = null;
            String record = null, newRecord = null, qualification = null, note = null;

            String[] parts = rest.split("\\s{2,}");
            // 외국 팀은 이름과 소속이 공백 1개로 붙어있어 2+ 공백 split이 실패. "TEAM"/"RACING" 키워드로 분리
            if (parts.length >= 1 && containsTeamKeyword(parts[0])) {
                String[] altSplit = trySplitNameTeamByKeyword(parts[0]);
                if (altSplit != null) {
                    String[] merged = new String[parts.length + 1];
                    merged[0] = altSplit[0];
                    merged[1] = altSplit[1];
                    if (parts.length > 1) {
                        System.arraycopy(parts, 1, merged, 2, parts.length - 1);
                    }
                    parts = merged;
                }
            }
            if (parts.length >= 1) athleteName = parts[0].trim();
            if (hasRegionColumn) {
                if (parts.length >= 2) region = parts[1].trim();
                if (parts.length >= 3) teamName = parts[2].trim();
            } else {
                // 시도 컬럼이 없는 PDF: parts[1]이 소속. parts[2]+ 는 regex로 추출되는 신기록/진출여부/비고
                if (parts.length >= 2) teamName = parts[1].trim();
            }

            // 기록: 시간 또는 정수 점수
            Matcher rm = TIME_RECORD.matcher(rest);
            if (rm.find()) {
                record = rm.group(1);
            } else if (parts.length >= 4) {
                String candidate = parts[3].trim();
                if (candidate.matches("^\\d+$")) record = candidate;
            }
            if (rest.contains("세계신")) newRecord = "세계신";
            else if (rest.contains("한국신")) newRecord = "한국신";
            else if (rest.contains("부별신")) newRecord = "부별신";
            else if (rest.contains("대회신")) newRecord = "대회신";
            if (rest.contains("Q")) qualification = "Q";
            Matcher nm = Pattern.compile("(제외|실격|점수줌|점수안줌|낙차|경고|주의|\\(점수줌\\)제외|점수안줌\\)실격)").matcher(rest);
            if (nm.find()) note = nm.group(1);
            if (Pattern.compile("\\bEl\\b").matcher(rest).find()) {
                note = note != null ? note : "제외";
            }
            if (rankStr != null && ranking == null) {
                String sn = mapStatus(rankStr);
                note = note != null ? note + " " + sn : sn;
            }

            if (teamName != null) {
                teamName = teamName.replaceAll("\\d+[:\\.]\\d+.*", "").trim();
                // 팀명 끝 점수(정수) 분리: "한국국제조리고등학교 11" → "한국국제조리고등학교", record=11
                if (record == null) {
                    Matcher scoreSuffix = Pattern.compile("^(.+?)\\s+(\\d+)$").matcher(teamName);
                    if (scoreSuffix.matches()) {
                        teamName = scoreSuffix.group(1).trim();
                        record = scoreSuffix.group(2);
                    }
                }
                if (teamName.isEmpty()) teamName = null;
            }

            // 이름/소속에 기록이 이어붙은 경우(공백 1개) 제거
            athleteName = stripRecordSuffix(athleteName);
            region = stripRecordSuffix(region);
            if (region != null && region.isEmpty()) region = null;

            results.add(new ParsedResult(bib, athleteName, region, teamName,
                    ranking, record, newRecord, qualification, note));
        }
        return results;
    }

    /** 단체전(계주/팀DTT) 결과 행을 layout 모드로 파싱. */
    public static List<ParsedResult> parseTeam(String[] lines) {
        List<ParsedResult> results = new ArrayList<>();
        boolean inData = false;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("순위")) { inData = true; continue; }
            if (!inData) continue;
            if (trimmed.isEmpty()) continue;
            if (isFooter(trimmed)) continue;

            // 일부 PDF는 진출여부 Q를 행 맨 앞에 장식 표시로 찍는다 ("Q     1   안동시청 ...").
            // 뒤에 오는 진출여부 컬럼의 Q는 그대로 두고, 앞쪽의 중복 Q만 제거해 regex가 먹히게 한다.
            Matcher leadingMark = LEADING_QUAL_MARK.matcher(trimmed);
            if (leadingMark.matches()) {
                trimmed = leadingMark.group(1);
            }

            Matcher m = RESULT_ROW.matcher(trimmed);
            if (!m.matches()) continue;

            String rankStr = m.group(1);
            int laneOrRank = Integer.parseInt(m.group(2));
            String rest = m.group(3).trim();

            Integer ranking = null;
            int lane;
            if (rankStr != null) {
                try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}
                lane = laneOrRank;
            } else {
                // "순위" 컬럼만 있고 별도 레인 컬럼이 없는 포맷 (대학일반부 계주 등).
                // 단일 숫자를 순위로 사용 (lane에도 같은 값을 넣어 기존 ParsedResult 시그니처 유지).
                ranking = laneOrRank;
                lane = laneOrRank;
            }

            // 팀명이 너무 길어 다음 줄로 쪼개진 경우 (예: "POWERSLIDE TEAM TAIWAN - B"가 "- "에서 끊김)
            // 현재 rest에 시간 기록이 없고 다음 비-푸터 라인에 기록이 있으면 그 라인을 합친다.
            if (!TIME_RECORD.matcher(rest).find()) {
                String continuation = findTeamRowContinuation(lines, i);
                if (continuation != null) {
                    rest = rest + "   " + continuation;
                }
            }

            String[] parts = rest.split("\\s{2,}");
            String teamEntryName = parts.length >= 1 ? parts[0].trim() : null;
            String region = parts.length >= 2 ? parts[1].trim() : null;

            // 외국팀처럼 3줄로 wrap된 경우: parts[0]="POWERSLIDE TEAM TAIWAN -", parts[1]="TPE", parts[2]="A 8:38.161"
            // → team="POWERSLIDE TEAM TAIWAN - A", region="TPE"
            if (teamEntryName != null && teamEntryName.endsWith("-")
                    && parts.length >= 3 && region != null
                    && (region.matches("[A-Z]{3}") || RegionNormalizer.isValidKoreanRegion(region))) {
                String suffixAndRecord = parts[2].trim();
                int spaceIdx = suffixAndRecord.indexOf(' ');
                String suffix = spaceIdx > 0 ? suffixAndRecord.substring(0, spaceIdx) : suffixAndRecord;
                if (suffix.length() <= 3 && !suffix.matches("^\\d.*") && !TIME_RECORD.matcher(suffix).find()) {
                    teamEntryName = teamEntryName + " " + suffix;
                }
            }

            // parts[1]이 시도가 아니라 기록(시간/점수)이라면 region을 null로 되돌려 아래 분리 로직을 태운다.
            if (region != null && (TIME_RECORD.matcher(region).matches() || region.matches("^\\d+$"))) {
                region = null;
            }

            // 팀명 마지막 토큰이 IOC 코드(3글자 대문자) 또는 한국 시도명이면 region으로 분리.
            // wrap된 경우 team 뒤에 region이 붙어있고 그 다음 parts[1]이 team의 나머지 조각인 상황도 처리.
            // 예: parts[0]="POWERSLIDE TEAM TAIWAN - TPE", parts[1]="B" → team="POWERSLIDE TEAM TAIWAN - B", region="TPE"
            if (teamEntryName != null) {
                String[] tokens = teamEntryName.split(" ");
                String lastToken = tokens[tokens.length - 1];
                boolean lastIsRegion = lastToken.matches("[A-Z]{3}")
                        || RegionNormalizer.isValidKoreanRegion(lastToken);
                if (lastIsRegion && tokens.length > 1) {
                    String teamWithoutLast = String.join(" ",
                            java.util.Arrays.copyOfRange(tokens, 0, tokens.length - 1));
                    // parts[1]이 "B" 같은 짧은 팀명 접미사이고 team이 "-"로 끝나면 이어 붙여 완성.
                    boolean looksLikeTeamSuffix = parts.length > 1
                            && parts[1].length() <= 3
                            && !parts[1].matches("^\\d.*")
                            && !TIME_RECORD.matcher(parts[1]).find();
                    if (teamWithoutLast.endsWith("-") && looksLikeTeamSuffix) {
                        teamEntryName = teamWithoutLast + " " + parts[1];
                        region = lastToken;
                    } else if (region == null) {
                        teamEntryName = teamWithoutLast;
                        region = lastToken;
                    }
                }
            }

            // 팀명과 시도가 붙어있는 경우 분리. 두 단계 시도:
            //   (a) 공백 1개로 붙음: "...연맹 전북"
            //   (b) 아예 공백 없이 붙음: "...연맹전북" → 알려진 한국 시도 접미사로 잘라냄
            if (teamEntryName != null && region == null) {
                Matcher regionSuffix = Pattern.compile("^(.+?)\\s+([가-힣]{2,3})$").matcher(teamEntryName);
                if (regionSuffix.matches() && RegionNormalizer.isValidKoreanRegion(regionSuffix.group(2))) {
                    teamEntryName = regionSuffix.group(1).trim();
                    region = regionSuffix.group(2);
                } else {
                    for (String kor : RegionNormalizer.KOREAN_REGIONS) {
                        if (teamEntryName.endsWith(kor) && teamEntryName.length() > kor.length()) {
                            teamEntryName = teamEntryName.substring(0, teamEntryName.length() - kor.length()).trim();
                            region = kor;
                            break;
                        }
                    }
                }
            }

            String record = null, newRecord = null, qualification = null, note = null;

            Matcher rm = TIME_RECORD.matcher(rest);
            if (rm.find()) record = rm.group(1);
            if (rest.contains("세계신")) newRecord = "세계신";
            else if (rest.contains("한국신")) newRecord = "한국신";
            else if (rest.contains("부별신")) newRecord = "부별신";
            else if (rest.contains("대회신")) newRecord = "대회신";
            if (rest.contains("Q")) qualification = "Q";

            Matcher nm = Pattern.compile("(제외|실격|점수줌|낙차|경고|주의)").matcher(rest);
            if (nm.find()) note = nm.group(1);
            if (rankStr != null && ranking == null) {
                String sn = mapStatus(rankStr);
                note = note != null ? note + " " + sn : sn;
            }

            // 단체전: athleteName=팀명, teamName=팀명
            results.add(new ParsedResult(lane, teamEntryName, region, teamEntryName,
                    ranking, record, newRecord, qualification, note));
        }
        return results;
    }

    private static final Pattern LEADING_QUAL_MARK = Pattern.compile("^Q\\s+(.+)$");

    /**
     * 현재 라인 이후에서 wrap된 단체전 행의 계속 라인을 찾는다.
     * 기준: 다음 비-푸터 라인이 "새 결과 행"이 아니고 시간 기록을 포함하면 continuation으로 간주.
     */
    private static String findTeamRowContinuation(String[] lines, int currentIdx) {
        StringBuilder acc = new StringBuilder();
        for (int j = currentIdx + 1; j < lines.length && j <= currentIdx + 5; j++) {
            String t = lines[j].trim();
            if (t.isEmpty()) continue;
            if (isFooter(t)) break;
            if (LEADING_QUAL_MARK.matcher(t).matches()) break;
            if (RESULT_ROW.matcher(t).matches()) break;
            if (acc.length() > 0) acc.append("   ");
            acc.append(t);
            if (TIME_RECORD.matcher(t).find()) return acc.toString();
        }
        return null;
    }

    // ============================================================
    // helpers
    // ============================================================

    private static boolean detectRegionColumn(String[] lines) {
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("순위") && t.contains("이름")) {
                return t.contains("시도");
            }
        }
        return false;
    }

    private static boolean isFooter(String trimmed) {
        return trimmed.startsWith("기록확인") || trimmed.startsWith("심판")
                || trimmed.startsWith("경기부장") || trimmed.startsWith("대한롤러")
                || trimmed.matches("^\\d{4}\\..*") || trimmed.startsWith("(");
    }

    private static boolean containsTeamKeyword(String s) {
        if (s == null) return false;
        for (String t : s.split("\\s+")) {
            String u = t.toUpperCase();
            if (u.equals("TEAM") || u.equals("RACING")) return true;
        }
        return false;
    }

    /**
     * 이름과 소속이 공백 1개로 붙어있을 때 "TEAM"/"RACING" 키워드 앞 단어부터를 팀으로 분리.
     * 예: "HUNG YUN-CHEN POWERSLIDE TEAM TAIWAN - A" → ["HUNG YUN-CHEN", "POWERSLIDE TEAM TAIWAN - A"]
     */
    private static String[] trySplitNameTeamByKeyword(String rest) {
        String[] tokens = rest.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String u = tokens[i].toUpperCase();
            if ((u.equals("TEAM") || u.equals("RACING")) && i >= 2) {
                int teamStart = i - 1;
                String name = String.join(" ", Arrays.copyOfRange(tokens, 0, teamStart));
                String team = String.join(" ", Arrays.copyOfRange(tokens, teamStart, tokens.length));
                if (!name.isBlank() && !team.isBlank()) return new String[]{name, team};
            }
        }
        return null;
    }

    /** 끝에 붙은 기록(시간/점수) 제거: " A 16:03.085" → " A", "B57.823" → "B" */
    private static String stripRecordSuffix(String s) {
        if (s == null) return null;
        return TRAILING_RECORD.matcher(s).replaceAll("").trim();
    }

    private static String mapStatus(String code) {
        return switch (code) {
            case "EL" -> "제외";
            case "DF", "DQ", "DSQ" -> "실격";
            case "DNF" -> "미완주";
            case "DNS" -> "미출전";
            default -> code;
        };
    }
}