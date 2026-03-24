package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.EventRoundRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResultParsingService {

    private final EventRoundRepository eventRoundRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;
    private final EventResultRepository eventResultRepository;
    private final CompetitionEntryRepository competitionEntryRepository;

    public record ImportResult(int results, int newEntries, int filesProcessed) {}

    @Transactional
    public ImportResult parseResultPdf(Path pdfPath, Long competitionId) throws IOException {
        String text = extractText(pdfPath);
        if (text == null || text.isBlank()) return new ImportResult(0, 0, 0);

        String[] lines = text.split("\n");

        // 헤더 파싱
        int eventNumber = 0;
        int heatNumber = 0;
        boolean isTeamEvent = false;
        for (String line : lines) {
            String trimmed = line.trim();
            Matcher headerMatch = Pattern.compile("^(\\d+)(?:-(\\d+))?\\s+(여|남).+").matcher(trimmed);
            if (headerMatch.matches()) {
                eventNumber = Integer.parseInt(headerMatch.group(1));
                heatNumber = headerMatch.group(2) != null ? Integer.parseInt(headerMatch.group(2)) : 0;
                isTeamEvent = trimmed.contains("계주") || trimmed.contains("팀DTT") || trimmed.contains("팀dtt");
                break;
            }
        }
        if (eventNumber == 0) return new ImportResult(0, 0, 0);

        final int finalEventNumber = eventNumber;
        final int finalHeatNumber = heatNumber;

        // EventRound 찾기
        List<EventRound> rounds = eventRoundRepository.findByEvent_CompetitionIdOrderByEventNumberAsc(competitionId);
        EventRound targetRound = rounds.stream()
                .filter(r -> r.getEventNumber() != null && r.getEventNumber() == finalEventNumber)
                .findFirst().orElse(null);
        if (targetRound == null) return new ImportResult(0, 0, 0);

        // EventHeat 찾거나 생성
        EventHeat targetHeat = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(targetRound.getId())
                .stream().filter(h -> h.getHeatNumber() == finalHeatNumber).findFirst()
                .orElseGet(() -> eventHeatRepository.save(EventHeat.builder()
                        .eventRound(targetRound).heatNumber(finalHeatNumber).build()));

        // 기존 HeatEntry를 배번으로 매핑
        List<HeatEntry> existingEntries = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(targetHeat.getId());
        Map<Integer, HeatEntry> entryByBib = existingEntries.stream()
                .collect(Collectors.toMap(HeatEntry::getBibNumber, e -> e, (a, b) -> a));

        // 결과 파싱 (개인전/단체전 분기)
        List<ParsedResult> results = isTeamEvent ? parseTeamResultLines(lines) : parseResultLines(lines);

        int resultCount = 0;
        int newEntryCount = 0;
        Long compId = targetRound.getEvent().getCompetition().getId();
        String gender = targetRound.getEvent().getGender();

        for (ParsedResult pr : results) {
            HeatEntry heatEntry = entryByBib.get(pr.bibNumber);

            // HeatEntry가 없으면 → 엔트리 자동 생성
            if (heatEntry == null && pr.athleteName != null) {
                CompetitionEntry compEntry = findOrCreateCompetitionEntry(
                        compId, pr.athleteName, gender, pr.region, pr.teamName);
                heatEntry = heatEntryRepository.save(HeatEntry.builder()
                        .heat(targetHeat).entry(compEntry).bibNumber(pr.bibNumber).build());
                entryByBib.put(pr.bibNumber, heatEntry);
                newEntryCount++;
            }

            if (heatEntry == null) continue;

            // 결과 저장/갱신
            Optional<EventResult> existing = eventResultRepository.findByHeatEntryId(heatEntry.getId());
            if (existing.isPresent()) {
                existing.get().updateResult(pr.ranking, pr.record, pr.newRecord, pr.qualification, pr.note);
            } else {
                eventResultRepository.save(EventResult.builder()
                        .heatEntry(heatEntry).ranking(pr.ranking).record(pr.record)
                        .newRecord(pr.newRecord).qualification(pr.qualification).note(pr.note).build());
            }
            resultCount++;
        }

        return new ImportResult(resultCount, newEntryCount, 1);
    }

    @Transactional
    public ImportResult parseMultipleFiles(List<Path> pdfPaths, Long competitionId) {
        int totalResults = 0, totalNewEntries = 0, totalFiles = 0;
        for (Path path : pdfPaths) {
            try {
                ImportResult r = parseResultPdf(path, competitionId);
                totalResults += r.results();
                totalNewEntries += r.newEntries();
                totalFiles += r.filesProcessed();
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", path.getFileName(), e.getMessage());
            }
        }
        return new ImportResult(totalResults, totalNewEntries, totalFiles);
    }

    private CompetitionEntry findOrCreateCompetitionEntry(Long competitionId, String athleteName,
                                                          String gender, String region, String teamName) {
        return competitionEntryRepository
                .findByCompetitionIdAndAthleteNameAndGenderAndTeamName(competitionId, athleteName, gender, teamName != null ? teamName : "")
                .orElseGet(() -> competitionEntryRepository.save(CompetitionEntry.builder()
                        .competition(competitionEntryRepository.findByCompetitionId(competitionId).getFirst().getCompetition())
                        .athleteName(athleteName).gender(gender).region(region).teamName(teamName).build()));
    }

    private static final Pattern RECORD_ONLY_LINE = Pattern.compile(
            "^\\s+(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})(.*)$"
    );

    private List<ParsedResult> parseResultLines(String[] lines) {
        List<ParsedResult> results = new ArrayList<>();
        boolean inData = false;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("순위")) { inData = true; continue; }
            if (!inData) continue;
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("기록확인") || trimmed.startsWith("심판") || trimmed.startsWith("경기부장")
                    || trimmed.startsWith("대한롤러") || trimmed.matches("^\\d{4}\\..*") || trimmed.startsWith("(")) continue;

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

            Matcher m = Pattern.compile("^(\\d+|EL|DQ|DNF|DNS|DSQ|DF)\\s+(\\d+)\\s+(.+)$").matcher(trimmed);
            if (m.matches()) {
                String rankStr = m.group(1);
                int bib = Integer.parseInt(m.group(2));
                String rest = m.group(3).trim();

                Integer ranking = null;
                try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}

                String athleteName = null, region = null, teamName = null;
                String record = null, newRecord = null, qualification = null, note = null;

                String[] parts = rest.split("\\s{2,}");
                if (parts.length >= 1) athleteName = parts[0].trim();
                if (parts.length >= 2) region = parts[1].trim();
                if (parts.length >= 3) teamName = parts[2].trim();

                // 기록: 시간(00:00.000 또는 00.000) 또는 점수(정수)
                Matcher rm = Pattern.compile("(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})").matcher(rest);
                if (rm.find()) {
                    record = rm.group(1);
                } else if (parts.length >= 4) {
                    // teamName 뒤에 숫자만 있으면 점수로 인식
                    String candidate = parts[3].trim();
                    if (candidate.matches("^\\d+$")) {
                        record = candidate;
                    }
                }
                if (rest.contains("세계신")) newRecord = "세계신";
                else if (rest.contains("한국신")) newRecord = "한국신";
                else if (rest.contains("부별신")) newRecord = "부별신";
                else if (rest.contains("대회신")) newRecord = "대회신";
                if (rest.contains("Q")) qualification = "Q";
                Matcher nm = Pattern.compile("(제외|실격|점수줌|점수안줌|낙차|경고|주의|\\(점수줌\\)제외|점수안줌\\)실격)").matcher(rest);
                if (nm.find()) note = nm.group(1);
                if (ranking == null) {
                    String sn = switch (rankStr) {
                        case "EL" -> "제외";
                        case "DF", "DQ", "DSQ" -> "실격";
                        case "DNF" -> "미완주";
                        case "DNS" -> "미출전";
                        default -> rankStr;
                    };
                    note = note != null ? note + " " + sn : sn;
                }

                if (teamName != null) {
                    teamName = teamName.replaceAll("\\d+[:\\.]\\d+.*", "").trim();
                    // 팀명 끝에 붙은 점수(정수) 분리: "한국국제조리고등학교 11" → "한국국제조리고등학교", record=11
                    if (record == null) {
                        Matcher scoreSuffix = Pattern.compile("^(.+?)\\s+(\\d+)$").matcher(teamName);
                        if (scoreSuffix.matches()) {
                            teamName = scoreSuffix.group(1).trim();
                            record = scoreSuffix.group(2);
                        }
                    }
                    if (teamName.isEmpty()) teamName = null;
                }

                results.add(new ParsedResult(bib, athleteName, region, teamName, ranking, record, newRecord, qualification, note));
            }
        }
        return results;
    }

    /**
     * 단체전(계주/팀DTT) 결과 파싱.
     * 형식: "순위  레인 팀명  시도  신기록  진출여부  기록  사유"
     * 다음 줄에 멤버: "(이름,이름,이름)"
     */
    private List<ParsedResult> parseTeamResultLines(String[] lines) {
        List<ParsedResult> results = new ArrayList<>();
        boolean inData = false;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("순위")) { inData = true; continue; }
            if (!inData) continue;
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("기록확인") || trimmed.startsWith("심판") || trimmed.startsWith("경기부장")
                    || trimmed.startsWith("대한롤러") || trimmed.matches("^\\d{4}\\..*") || trimmed.startsWith("(")) continue;

            // 단체전 결과행: "1    3   대구성산중학교         대구          Q     4:55.912"
            // 또는 DQ 등: "DQ   1   팀명  시도  ...  사유"
            Matcher m = Pattern.compile("^(\\d+|EL|DQ|DNF|DNS|DSQ|DF)\\s+(\\d+)\\s+(.+)$").matcher(trimmed);
            if (m.matches()) {
                String rankStr = m.group(1);
                int lane = Integer.parseInt(m.group(2)); // 레인 = 배번
                String rest = m.group(3).trim();

                Integer ranking = null;
                try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}

                // rest: "대구성산중학교         대구          Q     4:55.912"
                String[] parts = rest.split("\\s{2,}");
                String teamEntryName = parts.length >= 1 ? parts[0].trim() : null; // 팀명 = athleteName
                String region = parts.length >= 2 ? parts[1].trim() : null;

                String record = null, newRecord = null, qualification = null, note = null;

                // 전체 rest에서 기록/신기록/진출 추출
                Matcher rm = Pattern.compile("(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})").matcher(rest);
                if (rm.find()) record = rm.group(1);
                if (rest.contains("세계신")) newRecord = "세계신";
                else if (rest.contains("한국신")) newRecord = "한국신";
                else if (rest.contains("부별신")) newRecord = "부별신";
                else if (rest.contains("대회신")) newRecord = "대회신";
                if (rest.contains("Q")) qualification = "Q";

                Matcher nm = Pattern.compile("(제외|실격|점수줌|낙차|경고|주의)").matcher(rest);
                if (nm.find()) note = nm.group(1);
                if (ranking == null) {
                    String sn = switch (rankStr) {
                        case "EL" -> "제외"; case "DF", "DQ", "DSQ" -> "실격";
                        case "DNF" -> "미완주"; case "DNS" -> "미출전"; default -> rankStr;
                    };
                    note = note != null ? note + " " + sn : sn;
                }

                // 단체전: athleteName=팀명, teamName=팀명
                results.add(new ParsedResult(lane, teamEntryName, region, teamEntryName, ranking, record, newRecord, qualification, note));
            }
        }
        return results;
    }

    private String extractText(Path pdfPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("pdftotext", "-layout", pdfPath.toString(), "-");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            String text = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("pdftotext timed out for {}", pdfPath.getFileName());
                return null;
            }
            return text;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private record ParsedResult(int bibNumber, String athleteName, String region, String teamName,
                                Integer ranking, String record, String newRecord,
                                String qualification, String note) {}
}