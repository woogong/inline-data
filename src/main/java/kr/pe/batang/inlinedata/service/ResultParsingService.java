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
import kr.pe.batang.inlinedata.service.parser.DivisionNormalizer;
import kr.pe.batang.inlinedata.service.parser.LayoutResultLineParser;
import kr.pe.batang.inlinedata.service.parser.LinePreprocessor;
import kr.pe.batang.inlinedata.service.parser.ParsedResult;
import kr.pe.batang.inlinedata.service.parser.RawResultLineParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        String[] lines = LinePreprocessor.mergeWrappedResultLines(text.split("\n"));

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
            String roundName = LinePreprocessor.parseRoundName(lines, headerLineIdx);
            String divisionName = DivisionNormalizer.normalize(genderChar, divisionRaw);
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
            results = LayoutResultLineParser.parseTeam(lines);
        } else {
            String rawText = pdfTextExtractor.extractTextRaw(pdfPath);
            if (rawText != null && !rawText.isBlank()) {
                results = RawResultLineParser.parse(rawText.split("\n"));
            } else {
                results = LayoutResultLineParser.parseIndividual(lines);
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
            if (pr.athleteName() != null) {
                heatEntry = entryByName.get(pr.athleteName().trim());
            }

            if (heatEntry != null) {
                // 재사용. bib이 바뀌었으면 업데이트
                if (heatEntry.getBibNumber() == null || heatEntry.getBibNumber().intValue() != pr.bibNumber()) {
                    entryByBib.remove(heatEntry.getBibNumber());
                    heatEntry.updateBib(pr.bibNumber());
                    entryByBib.put(pr.bibNumber(), heatEntry);
                }
            } else if (pr.athleteName() != null) {
                // 이름 매칭 실패 → CompetitionEntry 조회/생성
                CompetitionEntry compEntry = findOrCreateCompetitionEntry(
                        compId, pr.athleteName(), gender, pr.region(), pr.teamName());
                // DB는 악센트/대소문자 무시 매칭이 가능하므로(MySQL utf8mb4_unicode_ci),
                // findOrCreate가 이미 이 heat에 등록된 CompetitionEntry를 돌려줄 수 있다.
                // 이 경우 HeatEntry (heat_id, entry_id) UK 충돌을 피하기 위해 기존 HeatEntry 재사용.
                HeatEntry existing = compEntry.getId() != null ? entryByCeId.get(compEntry.getId()) : null;
                if (existing != null) {
                    heatEntry = existing;
                    if (heatEntry.getBibNumber() == null || heatEntry.getBibNumber().intValue() != pr.bibNumber()) {
                        entryByBib.remove(heatEntry.getBibNumber());
                        heatEntry.updateBib(pr.bibNumber());
                        entryByBib.put(pr.bibNumber(), heatEntry);
                    }
                } else {
                    heatEntry = heatEntryRepository.save(HeatEntry.builder()
                            .heat(targetHeat).entry(compEntry).bibNumber(pr.bibNumber()).build());
                    entryByBib.put(pr.bibNumber(), heatEntry);
                    if (compEntry.getId() != null) entryByCeId.put(compEntry.getId(), heatEntry);
                    newEntryCount++;
                }
                entryByName.put(pr.athleteName().trim(), heatEntry);
            }

            if (heatEntry == null) continue;
            matchedHeatEntryIds.add(heatEntry.getId());

            // 재임포트 시 기존 CompetitionEntry의 소속/시도도 최신 파싱 결과로 업데이트 (아직 매핑되지 않은 경우에만)
            CompetitionEntry compEntry = heatEntry.getEntry();
            if (compEntry != null && !compEntry.isMapped()) {
                compEntry.updateFromParsed(pr.region(), pr.teamName());
            }

            // 결과 저장/갱신
            Optional<EventResult> existing = eventResultRepository.findByHeatEntryId(heatEntry.getId());
            if (existing.isPresent()) {
                existing.get().updateResult(pr.ranking(), pr.record(), pr.newRecord(), pr.qualification(), pr.note());
            } else {
                eventResultRepository.save(EventResult.builder()
                        .heatEntry(heatEntry).ranking(pr.ranking()).record(pr.record())
                        .newRecord(pr.newRecord()).qualification(pr.qualification()).note(pr.note()).build());
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

}