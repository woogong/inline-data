package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.CompetitionRepository;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.EventRoundRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResultParsingService {

    private final CompetitionRepository competitionRepository;
    private final EventRepository eventRepository;
    private final EventRoundRepository eventRoundRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;
    private final EventResultRepository eventResultRepository;
    private final CompetitionEntryRepository competitionEntryRepository;
    private final PdfTextExtractor pdfTextExtractor;

    public record ImportResult(int results, int newEntries, int filesProcessed) {}

    @Transactional
    public ImportResult parseResultPdf(Path pdfPath, Long competitionId) throws IOException {
        String text = pdfTextExtractor.extractText(pdfPath);
        if (text == null || text.isBlank()) return new ImportResult(0, 0, 0);

        String[] lines = mergeWrappedResultLines(text.split("\n"));

        // 헤더 파싱: 이벤트 번호, 조 번호, 성별, 종별, 종목명
        int eventNumber = 0;
        int heatNumber = 0;
        boolean isTeamEvent = false;
        String divisionRaw = null;
        String eventName = null;
        String genderChar = null;
        int headerLineIdx = -1;
        Pattern fullHeader = Pattern.compile("^(\\d{1,3})(?:-(\\d+))?\\s+(남|여)자(\\S+?)\\([A-Za-z][^)]*\\)\\s+(.+)$");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            Matcher headerMatch = Pattern.compile("^(\\d{1,3})(?:-(\\d+))?\\s+(여|남).+").matcher(trimmed);
            if (headerMatch.matches()) {
                if (trimmed.contains("결승종합")) {
                    log.info("결승종합 파일 스킵: {}", pdfPath.getFileName());
                    return new ImportResult(0, 0, 0);
                }
                eventNumber = Integer.parseInt(headerMatch.group(1));
                heatNumber = headerMatch.group(2) != null ? Integer.parseInt(headerMatch.group(2)) : 0;
                isTeamEvent = trimmed.contains("계주") || trimmed.contains("팀DTT") || trimmed.contains("팀dtt");
                Matcher full = fullHeader.matcher(trimmed);
                if (full.matches()) {
                    genderChar = full.group(3);
                    divisionRaw = full.group(4).trim();
                    eventName = full.group(5).trim();
                }
                headerLineIdx = i;
                break;
            }
        }
        if (eventNumber == 0) return new ImportResult(0, 0, 0);

        final int finalEventNumber = eventNumber;
        final int finalHeatNumber = heatNumber;

        // EventRound 찾기. 없으면 PDF 정보로 자동 생성
        List<EventRound> rounds = eventRoundRepository.findByEvent_CompetitionIdOrderByEventNumberAsc(competitionId);
        EventRound targetRound = rounds.stream()
                .filter(r -> r.getEventNumber() != null && r.getEventNumber() == finalEventNumber)
                .findFirst().orElse(null);
        if (targetRound == null && divisionRaw != null && eventName != null && genderChar != null) {
            String roundName = parseRoundName(lines, headerLineIdx);
            String divisionName = normalizeDivision(genderChar, divisionRaw);
            String genderCode = genderChar.equals("여") ? "F" : "M";
            Event event = eventRepository
                    .findByCompetitionIdAndDivisionNameAndGenderAndEventName(competitionId, divisionName, genderCode, eventName)
                    .orElse(null);
            if (event != null && roundName != null) {
                // 동일 이벤트에 같은 round 이름이 이미 있으면 재사용 (eventNumber만 업데이트)
                targetRound = eventRoundRepository.findByEventIdAndRound(event.getId(), roundName)
                        .orElse(null);
                if (targetRound == null) {
                    targetRound = eventRoundRepository.save(EventRound.builder()
                            .event(event).round(roundName).eventNumber(eventNumber).build());
                    log.info("결과 PDF에서 라운드 자동 생성: event={} round={} eventNumber={}",
                            event.getId(), roundName, eventNumber);
                }
            }
        }
        if (targetRound == null) {
            log.warn("매칭되는 EventRound를 찾지 못했고 자동 생성도 실패: {} (eventNumber={}, division={}, eventName={})",
                    pdfPath.getFileName(), eventNumber, divisionRaw, eventName);
            return new ImportResult(0, 0, 0);
        }

        // EventHeat 찾거나 생성
        final EventRound finalRound = targetRound;
        EventHeat targetHeat = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(finalRound.getId())
                .stream().filter(h -> h.getHeatNumber() == finalHeatNumber).findFirst()
                .orElseGet(() -> eventHeatRepository.save(EventHeat.builder()
                        .eventRound(finalRound).heatNumber(finalHeatNumber).build()));

        // 기존 HeatEntry를 배번/이름/CompetitionEntry id로 매핑
        List<HeatEntry> existingEntries = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(targetHeat.getId());
        Map<Integer, HeatEntry> entryByBib = new HashMap<>();
        Map<String, HeatEntry> entryByName = new HashMap<>();
        Map<Long, HeatEntry> entryByCeId = new HashMap<>();
        for (HeatEntry he : existingEntries) {
            entryByBib.putIfAbsent(he.getBibNumber(), he);
            CompetitionEntry ce = he.getEntry();
            if (ce != null) {
                if (ce.getId() != null) entryByCeId.putIfAbsent(ce.getId(), he);
                String nm = ce.getAthleteName();
                if (nm != null && !nm.isBlank()) entryByName.putIfAbsent(nm.trim(), he);
            }
        }

        // 결과 파싱. 개인전은 -raw 모드(공백 겹침 없음), 단체전은 -layout(기존 로직 유지)
        List<ParsedResult> results;
        if (isTeamEvent) {
            results = parseTeamResultLines(lines);
        } else {
            String rawText = pdfTextExtractor.extractTextRaw(pdfPath);
            if (rawText != null && !rawText.isBlank()) {
                results = parseRawResultLines(rawText.split("\n"));
            } else {
                results = parseResultLines(lines);
            }
        }
        int resultCount = 0;
        int newEntryCount = 0;
        Long compId = targetRound.getEvent().getCompetition().getId();
        String gender = targetRound.getEvent().getGender();
        Set<Long> matchedHeatEntryIds = new HashSet<>();

        for (ParsedResult pr : results) {
            // 이름 매칭만 사용: 같은 bib에 다른 선수가 배치되어도 "새 선수"로 취급
            HeatEntry heatEntry = null;
            if (pr.athleteName != null) {
                heatEntry = entryByName.get(pr.athleteName.trim());
            }

            if (heatEntry != null) {
                // 재사용. bib이 바뀌었으면 업데이트
                if (heatEntry.getBibNumber() == null || heatEntry.getBibNumber().intValue() != pr.bibNumber) {
                    entryByBib.remove(heatEntry.getBibNumber());
                    heatEntry.updateBib(pr.bibNumber);
                    entryByBib.put(pr.bibNumber, heatEntry);
                }
            } else if (pr.athleteName != null) {
                // 이름 매칭 실패 → CompetitionEntry 조회/생성
                CompetitionEntry compEntry = findOrCreateCompetitionEntry(
                        compId, pr.athleteName, gender, pr.region, pr.teamName);
                // DB는 악센트/대소문자 무시 매칭이 가능하므로(MySQL utf8mb4_unicode_ci),
                // findOrCreate가 이미 이 heat에 등록된 CompetitionEntry를 돌려줄 수 있다.
                // 이 경우 HeatEntry (heat_id, entry_id) UK 충돌을 피하기 위해 기존 HeatEntry 재사용.
                HeatEntry existing = compEntry.getId() != null ? entryByCeId.get(compEntry.getId()) : null;
                if (existing != null) {
                    heatEntry = existing;
                    if (heatEntry.getBibNumber() == null || heatEntry.getBibNumber().intValue() != pr.bibNumber) {
                        entryByBib.remove(heatEntry.getBibNumber());
                        heatEntry.updateBib(pr.bibNumber);
                        entryByBib.put(pr.bibNumber, heatEntry);
                    }
                } else {
                    heatEntry = heatEntryRepository.save(HeatEntry.builder()
                            .heat(targetHeat).entry(compEntry).bibNumber(pr.bibNumber).build());
                    entryByBib.put(pr.bibNumber, heatEntry);
                    if (compEntry.getId() != null) entryByCeId.put(compEntry.getId(), heatEntry);
                    newEntryCount++;
                }
                entryByName.put(pr.athleteName.trim(), heatEntry);
            }

            if (heatEntry == null) continue;
            matchedHeatEntryIds.add(heatEntry.getId());

            // 재임포트 시 기존 CompetitionEntry의 소속/시도도 최신 파싱 결과로 업데이트 (아직 매핑되지 않은 경우에만)
            CompetitionEntry compEntry = heatEntry.getEntry();
            if (compEntry != null && !compEntry.isMapped()) {
                compEntry.updateFromParsed(pr.region, pr.teamName);
            }

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

        // 결과에 없는 사전등록 엔트리 제거 (result PDF가 있어야 지움 → 파싱 실패 시 보호)
        if (!results.isEmpty() && !existingEntries.isEmpty()) {
            List<Long> toDelete = existingEntries.stream()
                    .map(HeatEntry::getId)
                    .filter(id -> !matchedHeatEntryIds.contains(id))
                    .toList();
            if (!toDelete.isEmpty()) {
                eventResultRepository.deleteByHeatEntryIdIn(toDelete);
                heatEntryRepository.deleteAllById(toDelete);
                log.info("결과에 없는 사전등록 엔트리 {}건 제거 (heat_id={})", toDelete.size(), targetHeat.getId());
            }
        }

        // DTT 종목이면 전체 기록 순으로 순위 재계산
        if (targetRound.getEvent().getEventName().contains("DTT")) {
            recalculateDttRankings(targetRound.getId());
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

    private void recalculateDttRankings(Long eventRoundId) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(eventRoundId);
        if (heats.isEmpty()) return;
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        List<EventResult> allResults = eventResultRepository.findByHeatIdsWithDetails(heatIds);
        if (allResults.isEmpty()) return;

        allResults.sort(Comparator.comparing(
                (EventResult er) -> er.getRecord() != null ? er.getRecord() : "zzz"));

        for (int i = 0; i < allResults.size(); i++) {
            EventResult er = allResults.get(i);
            Integer newRanking = er.getRecord() != null ? i + 1 : null;
            er.updateResult(newRanking, er.getRecord(), er.getNewRecord(),
                    er.getQualification(), er.getNote());
        }
        log.info("DTT rankings recalculated for round {}: {} results", eventRoundId, allResults.size());
    }

    private CompetitionEntry findOrCreateCompetitionEntry(Long competitionId, String athleteName,
                                                          String gender, String region, String teamName) {
        // 과거 버그로 중복 CE가 존재할 수 있어 List로 조회해 가장 오래된 것(최소 id) 사용
        List<CompetitionEntry> found = competitionEntryRepository
                .findAllByCompetitionIdAndAthleteNameAndGenderAndTeamName(
                        competitionId, athleteName, gender, teamName != null ? teamName : "");
        if (!found.isEmpty()) {
            return found.stream().min(Comparator.comparing(CompetitionEntry::getId)).orElseThrow();
        }
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("대회를 찾을 수 없습니다. id=" + competitionId));
        return competitionEntryRepository.save(CompetitionEntry.builder()
                .competition(competition)
                .athleteName(athleteName).gender(gender).region(region).teamName(teamName).build());
    }

    private static final Pattern RECORD_ONLY_LINE = Pattern.compile(
            "^\\s+(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})(.*)$"
    );

    private static final Pattern RANK_BIB_ONLY = Pattern.compile(
            "^(?:\\d+|EL|DQ|DNF|DNS|DSQ|DF)\\s+\\d+\\s*$"
    );
    private static final Pattern NEXT_RESULT_LINE = Pattern.compile(
            "^(?:\\d+|EL|DQ|DNF|DNS|DSQ|DF)\\s+\\d+(?:\\s.*)?$"
    );
    private static final Pattern RECORD_VALUE = Pattern.compile(
            "(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})"
    );

    // 이름이 길어 "순위 등번호"만 한 줄에 나오고 이름/소속/기록이 이어서 여러 줄로 나뉘는 PDF 줄바꿈을 한 줄로 합친다.
    private String[] mergeWrappedResultLines(String[] lines) {
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
                    if (nextTrim.startsWith("기록확인") || nextTrim.startsWith("심판") || nextTrim.startsWith("경기부장")
                            || nextTrim.startsWith("대한롤러") || nextTrim.matches("^\\d{4}\\..*") || nextTrim.startsWith("(")) break;
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

    // 이벤트 헤더 다음 줄에서 "결승"/"준결승"/"준준결승"/"예선"/"조별결승" 찾기
    private String parseRoundName(String[] lines, int startIdx) {
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

    // PDF의 "여자중학부" → DB의 "여중부"로 변환
    private String normalizeDivision(String gender, String raw) {
        String suffix = "";
        Matcher g = Pattern.compile("(\\(.+\\))$").matcher(raw);
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

    // pdftotext -raw 결과를 파싱.
    // "[EL|DF|...]? 팀 [rank]? bib 이름 [Q]? [기록 [신기록]?]? [시도]? [W]?"
    // rank와 기록 모두 optional (예선 통과/탈락만 표기하는 PDF 지원)
    private static final Pattern RAW_LINE = Pattern.compile(
            "^(?:(EL|El|DF|DQ|DNS|DNF|DSQ)\\s+)?" +
            "(.+?)\\s+" +
            "(?:(\\d+|EL|DF|DQ|DNS|DNF|DSQ)\\s+)?" +
            "(\\d+)\\s+" +
            "(\\D.*?)" +            // 이름은 숫자로 시작하지 않음 (팀명 끝 숫자 ex. "Powerslide China 1" 와 구분)
            "(?:\\s+(Q))?" +
            "(?:\\s+(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3}|\\d+))?" +   // 시간 기록 또는 포인트 점수(정수)
            "(?:\\s+(세계신|한국신|부별신|대회신))?" +
            "(?:\\s+([A-Z]{3}|[가-힣]{2,3}))?" +
            "(?:\\s+(W))?" +
            "\\s*$"
    );

    // DTT 형식 (시간 기록 필수): 비-EL 시간 경기 행. "팀 rank bib [Q]? 시간기록 [신기록]? 시도 이름"
    private static final Pattern RAW_LINE_DTT_TIME = Pattern.compile(
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

    // DTT 형식 (기록 없음): EL 행 in 시간 경기. "[EL]? 팀 rank bib 시도 이름" — team greedy로 "X 1" 꼴도 지원
    private static final Pattern RAW_LINE_DTT_NOREC = Pattern.compile(
            "^(?:(EL|El|DF|DQ|DNS|DNF|DSQ)\\s+)?" +
            "(.+)\\s+" +
            "(\\d+|EL|DF|DQ|DNS|DNF|DSQ)\\s+" +
            "(\\d+)\\s+" +
            "([A-Z]{3}|[가-힣]{2,3})\\s+" +
            "(\\D.+?)" +
            "\\s*$"
    );

    // DTT 형식 (정수 기록): 포인트 경기. "[EL]? 팀 rank bib [Q]? 정수점수 시도 이름"
    private static final Pattern RAW_LINE_DTT_INT = Pattern.compile(
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

    private List<ParsedResult> parseRawResultLines(String[] lines) {
        // 경기 타입 감지: 시간 포맷이 있으면 시간 경기, 없으면 포인트 경기 (또는 일반)
        boolean isTimeRace = false;
        Pattern timeRec = Pattern.compile("\\d+:\\d+\\.\\d+|\\d+\\.\\d{3}");
        for (String line : lines) {
            if (timeRec.matcher(line).find()) { isTimeRace = true; break; }
        }

        List<ParsedResult> results = new ArrayList<>();
        boolean inData = false;
        StringBuilder buffer = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("순위")) { inData = true; continue; }
            if (!inData) continue;
            if (trimmed.isEmpty()) continue;
            if (shouldSkipRawHeaderLine(trimmed)) continue;

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

    // DTT 패턴들을 경기 타입에 맞게 시도. 실패하면 RAW_LINE(일반 포맷)으로 fallback.
    private ParsedResult tryParseDttOrNormal(String candidate, boolean isTimeRace) {
        // 1) 시간 경기 비-EL DTT 행: 시간 기록 필수
        Matcher mt = RAW_LINE_DTT_TIME.matcher(candidate);
        if (mt.matches()) return toParsedResultDttTime(mt);

        // 2) 일반 포맷 (이름이 bib 다음, 기록이 뒤): 단 DTT 라인 오매칭 검증
        Matcher m = RAW_LINE.matcher(candidate);
        if (m.matches() && !looksLikeMisparsedDttByRawLine(m)) {
            return toParsedResult(m);
        }

        // 3) 시간 경기의 DTT EL/기록없는 행
        if (isTimeRace) {
            Matcher mn = RAW_LINE_DTT_NOREC.matcher(candidate);
            if (mn.matches()) return toParsedResultDttNoRec(mn);
        }

        // 4) 포인트 경기 DTT 행: 정수 기록
        if (!isTimeRace) {
            Matcher mi = RAW_LINE_DTT_INT.matcher(candidate);
            if (mi.matches()) return toParsedResultDttInt(mi);
        }

        // 5) RAW_LINE 매칭 결과가 의심스럽더라도 최후 수단으로 사용
        if (m.matches()) return toParsedResult(m);
        return null;
    }

    private static final java.util.Set<String> KOREAN_REGIONS = java.util.Set.of(
            "서울","부산","대구","인천","광주","대전","울산","세종","경기","강원",
            "충북","충남","전북","전남","경북","경남","제주","전국");

    // RAW_LINE 매칭 결과가 DTT 행을 잘못 먹었는지 확인:
    // 1) 이름이 시도 코드로 시작 + region이 비어있음
    // 2) region이 한글 2-3자이지만 실제 시도 목록에 없음 (이름 등이 region으로 잘못 잡힘)
    private boolean looksLikeMisparsedDttByRawLine(Matcher m) {
        String name = m.group(5);
        String region = m.group(9);
        if (region == null && name != null
                && (name.matches("^[A-Z]{3}\\s+\\S.+") || name.matches("^[가-힣]{2,3}\\s+\\S.+"))) {
            return true;
        }
        if (region != null && region.matches("[가-힣]{2,3}") && !KOREAN_REGIONS.contains(region)) {
            return true;
        }
        return false;
    }

    private static boolean shouldSkipRawHeaderLine(String trimmed) {
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

    // DTT 시간 경기 비-EL: 팀 rank bib [Q] 시간기록 [신기록] 시도 이름
    private ParsedResult toParsedResultDttTime(Matcher m) {
        return buildDttResult(null, m.group(1), m.group(2), m.group(3),
                m.group(4), m.group(5), m.group(6), m.group(7), m.group(8));
    }

    // DTT 기록 없음 (시간 경기 EL): [EL]? 팀 rank bib 시도 이름
    private ParsedResult toParsedResultDttNoRec(Matcher m) {
        return buildDttResult(m.group(1), m.group(2), m.group(3), m.group(4),
                null, null, null, m.group(5), m.group(6));
    }

    // DTT 정수 기록 (포인트 경기): [EL]? 팀 rank bib [Q] 정수점수 시도 이름
    private ParsedResult toParsedResultDttInt(Matcher m) {
        return buildDttResult(m.group(1), m.group(2), m.group(3), m.group(4),
                m.group(5), m.group(6), null, m.group(7), m.group(8));
    }

    private static final Map<String, String> REGION_CODE_TO_KOREAN = Map.ofEntries(
            Map.entry("TPE", "대만"), Map.entry("HKG", "홍콩"), Map.entry("JPN", "일본"),
            Map.entry("AUS", "호주"), Map.entry("CHN", "중국"), Map.entry("CHINA", "중국"),
            Map.entry("IDN", "인도네시아"), Map.entry("CHI", "칠레"), Map.entry("KOR", "한국"),
            Map.entry("NZL", "뉴질랜드"), Map.entry("USA", "미국"), Map.entry("CAN", "캐나다"),
            Map.entry("MAC", "마카오"), Map.entry("SGP", "싱가포르"), Map.entry("MAS", "말레이시아"),
            Map.entry("THA", "태국"), Map.entry("VIE", "베트남"), Map.entry("INA", "인도네시아"),
            Map.entry("PHI", "필리핀"), Map.entry("IND", "인도"), Map.entry("GER", "독일"),
            Map.entry("FRA", "프랑스"), Map.entry("ITA", "이탈리아"), Map.entry("ESP", "스페인"),
            Map.entry("GBR", "영국"), Map.entry("NED", "네덜란드"), Map.entry("BEL", "벨기에"));

    private static String normalizeRegion(String region) {
        if (region == null) return null;
        String mapped = REGION_CODE_TO_KOREAN.get(region);
        return mapped != null ? mapped : region;
    }

    private ParsedResult buildDttResult(String notePrefix, String teamNameRaw, String rankStr, String bibStr,
                                        String qualification, String record, String newRecord,
                                        String region, String athleteNameRaw) {
        int bib;
        try { bib = Integer.parseInt(bibStr); } catch (NumberFormatException e) { return null; }
        region = normalizeRegion(region);
        Integer ranking = null;
        try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}

        String note = null;
        if (notePrefix != null) {
            note = switch (notePrefix) {
                case "EL", "El" -> "제외";
                case "DF", "DQ", "DSQ" -> "실격";
                case "DNF" -> "미완주";
                case "DNS" -> "미출전";
                default -> notePrefix;
            };
        }
        if (rankStr != null && ranking == null) {
            String sn = switch (rankStr) {
                case "EL" -> "제외";
                case "DF", "DQ", "DSQ" -> "실격";
                case "DNF" -> "미완주";
                case "DNS" -> "미출전";
                default -> rankStr;
            };
            note = note != null ? note + " " + sn : sn;
        }
        return new ParsedResult(bib, athleteNameRaw.trim(), region, teamNameRaw.trim(),
                ranking, record, newRecord, qualification, note);
    }

    private ParsedResult toParsedResult(Matcher m) {
        String notePrefix = m.group(1);
        String teamName = m.group(2).trim();
        String rankStr = m.group(3);
        int bib;
        try { bib = Integer.parseInt(m.group(4)); } catch (NumberFormatException e) { return null; }
        String athleteName = m.group(5).trim();
        String qualification = m.group(6);
        String record = m.group(7);
        String newRecord = m.group(8);
        String region = normalizeRegion(m.group(9));
        String wMarker = m.group(10);

        Integer ranking = null;
        try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}

        String note = null;
        if (notePrefix != null) {
            note = switch (notePrefix) {
                case "EL", "El" -> "제외";
                case "DF", "DQ", "DSQ" -> "실격";
                case "DNF" -> "미완주";
                case "DNS" -> "미출전";
                default -> notePrefix;
            };
        }
        if (rankStr != null && ranking == null) {
            String sn = switch (rankStr) {
                case "EL" -> "제외";
                case "DF", "DQ", "DSQ" -> "실격";
                case "DNF" -> "미완주";
                case "DNS" -> "미출전";
                default -> rankStr;
            };
            note = note != null ? note + " " + sn : sn;
        }
        if (wMarker != null) {
            note = note != null ? note + " W" : "W";
        }

        return new ParsedResult(bib, athleteName, region, teamName, ranking, record, newRecord, qualification, note);
    }

    private static boolean containsTeamKeyword(String s) {
        if (s == null) return false;
        for (String t : s.split("\\s+")) {
            String u = t.toUpperCase();
            if (u.equals("TEAM") || u.equals("RACING")) return true;
        }
        return false;
    }

    // 이름과 소속이 공백 1개로 붙어있을 때 "TEAM"/"RACING" 키워드 앞 단어부터를 팀으로 분리
    // 예: "HUNG YUN-CHEN POWERSLIDE TEAM TAIWAN - A" → ["HUNG YUN-CHEN", "POWERSLIDE TEAM TAIWAN - A"]
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

    // 끝에 붙은 기록(시간/점수) 제거.
    // " A 16:03.085" → " A", "B57.823" → "B"
    private static String stripRecordSuffix(String s) {
        if (s == null) return null;
        return s.replaceAll("\\s*(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3}).*$", "").trim();
    }

    private List<ParsedResult> parseResultLines(String[] lines) {
        List<ParsedResult> results = new ArrayList<>();
        boolean inData = false;
        // 헤더에 "시도" 컬럼이 있는지 감지. 없으면 parts[1]이 소속(teamName)이 됨
        boolean hasRegionColumn = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("순위") && t.contains("이름")) {
                hasRegionColumn = t.contains("시도");
                break;
            }
        }
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

            Matcher m = Pattern.compile("^(?:(\\d+|EL|DQ|DNF|DNS|DSQ|DF)\\s+)?(\\d+)\\s+(.+)$").matcher(trimmed);
            if (m.matches()) {
                String rankStr = m.group(1); // null if no rank
                int bib = Integer.parseInt(m.group(2));
                String rest = m.group(3).trim();

                Integer ranking = null;
                if (rankStr != null) {
                    try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}
                }

                String athleteName = null, region = null, teamName = null;
                String record = null, newRecord = null, qualification = null, note = null;

                String[] parts = rest.split("\\s{2,}");
                // 외국 팀은 이름과 소속이 공백 1개로 붙어있어 2+ 공백 split이 실패한다. "TEAM"/"Racing" 키워드로 분리
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
                    // 시도 컬럼이 없는 PDF: parts[1]이 소속, parts[2]+ 는 이미 regex로 추출되는 신기록/진출여부/비고
                    if (parts.length >= 2) teamName = parts[1].trim();
                }

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
                // "El" (Eliminated) → 제외
                if (Pattern.compile("\\bEl\\b").matcher(rest).find()) {
                    note = note != null ? note : "제외";
                }
                if (rankStr != null && ranking == null) {
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

                // 이름/소속에 기록이 이어붙은 경우(공백 1개) 제거
                athleteName = stripRecordSuffix(athleteName);
                region = stripRecordSuffix(region);
                if (region != null && region.isEmpty()) region = null;

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
            Matcher m = Pattern.compile("^(?:(\\d+|EL|DQ|DNF|DNS|DSQ|DF)\\s+)?(\\d+)\\s+(.+)$").matcher(trimmed);
            if (m.matches()) {
                String rankStr = m.group(1); // null if no rank
                int lane = Integer.parseInt(m.group(2)); // 레인 = 배번
                String rest = m.group(3).trim();

                Integer ranking = null;
                if (rankStr != null) {
                    try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}
                }

                // rest: "대구성산중학교         대구          Q     4:55.912"
                // 또는: "전북특별자치도롤러스포츠연맹 전북                 4:35.053" (공백 1개)
                String[] parts = rest.split("\\s{2,}");
                String teamEntryName = parts.length >= 1 ? parts[0].trim() : null;
                String region = parts.length >= 2 ? parts[1].trim() : null;

                // 팀명과 시도가 공백 1개로 붙어있는 경우 분리
                // 시도는 2~3글자 한글 (서울, 부산, 경기, 전북, 충남 등)
                if (teamEntryName != null && region == null) {
                    Matcher regionSuffix = Pattern.compile("^(.+?)\\s+([가-힣]{2,3})$").matcher(teamEntryName);
                    if (regionSuffix.matches()) {
                        teamEntryName = regionSuffix.group(1).trim();
                        region = regionSuffix.group(2);
                    }
                }

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
                if (rankStr != null && ranking == null) {
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

    private record ParsedResult(int bibNumber, String athleteName, String region, String teamName,
                                Integer ranking, String record, String newRecord,
                                String qualification, String note) {}
}