package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.entity.EventResultHistory;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.entity.ResultSource;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.CompetitionRepository;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventResultHistoryRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.EventRoundRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import kr.pe.batang.inlinedata.service.parser.DivisionNormalizer;
import kr.pe.batang.inlinedata.service.parser.LayoutResultLineParser;
import kr.pe.batang.inlinedata.service.parser.LinePreprocessor;
import kr.pe.batang.inlinedata.service.parser.ParsedResult;
import kr.pe.batang.inlinedata.service.parser.ParsedResultValidator;
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
    private final EventResultHistoryRepository eventResultHistoryRepository;
    private final CompetitionEntryRepository competitionEntryRepository;
    private final PdfTextExtractor pdfTextExtractor;

    public record ImportResult(int results, int newEntries, int filesProcessed) {}

    @Transactional
    public ImportResult parseResultPdf(Path pdfPath, Long competitionId, ResultSource source) throws IOException {
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
        // 헤더 예시:
        //   "4-7 남자중학부(Men.Middle School) 500m+D"     (영문 괄호만)
        //   "44-4 남자초등부(5,6학년) DTT200m"              (학년 괄호만, 영문 없음)
        //   "44-4 남자초등부(5,6학년)(Men.Elementary School 5-6) DTT200m"  (학년+영문)
        //   "40 남자중학부 500m+D"                         (괄호 없음)
        //
        // 그룹:
        //   4 = division (학년 괄호 포함): "중학부", "초등부(5,6학년)"
        //   5 = event name
        // 영문 번역 괄호는 소비만 하고 버린다.
        Pattern fullHeader = Pattern.compile(
                "^(\\d{1,3})(?:-(\\d+))?\\s+" +
                "(남|여)자" +
                "(\\S+?(?:\\(\\d[^)]*\\))?)" +          // division + optional 학년 괄호 (숫자로 시작)
                "(?:\\s*\\([A-Za-z][^)]*\\))?" +        // optional 영문 번역 괄호 (영문으로 시작)
                "\\s+(.+)$");
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
        List<ParsedResult> parsedResults;
        if (isTeamEvent) {
            parsedResults = LayoutResultLineParser.parseTeam(lines);
        } else {
            String rawText = pdfTextExtractor.extractTextRaw(pdfPath);
            if (rawText != null && !rawText.isBlank()) {
                parsedResults = RawResultLineParser.parse(rawText.split("\n"));
            } else {
                parsedResults = LayoutResultLineParser.parseIndividual(lines);
            }
        }
        // 검증 통과한 결과만 DB 저장 대상으로. 유효하지 않은 행은 폐기되어 phantom 엔트리 유입 방지.
        List<ParsedResult> results = parsedResults.stream()
                .map(ParsedResultValidator::validate)
                .filter(java.util.Objects::nonNull)
                .toList();

        int resultCount = 0;
        int newEntryCount = 0;
        Long compId = targetRound.getEvent().getCompetition().getId();
        String gender = targetRound.getEvent().getGender();
        Set<Long> matchedHeatEntryIds = new HashSet<>();

        for (ParsedResult pr : results) {
            // === 1) 기존 HeatEntry 찾기 (이름 또는 CE id) ===
            HeatEntry heatEntry = entryByName.get(pr.athleteName());
            CompetitionEntry compEntry = null;
            if (heatEntry == null) {
                compEntry = findOrCreateCompetitionEntry(
                        compId, pr.athleteName(), gender, pr.region(), pr.teamName());
                // DB는 악센트/대소문자 무시 매칭이 가능하므로(MySQL utf8mb4_unicode_ci),
                // findOrCreate가 이미 이 heat에 등록된 CompetitionEntry를 돌려줄 수 있다.
                // 이 경우 HeatEntry (heat_id, entry_id) UK 충돌을 피하기 위해 기존 HeatEntry 재사용.
                if (compEntry.getId() != null) {
                    heatEntry = entryByCeId.get(compEntry.getId());
                }
            }

            // === 2) 상위 출처 보호: AUTO가 UPLOAD/MANUAL을 덮어쓰려 하면 어떤 mutation도 하지 않고 skip ===
            //     (bib update / evict / result update 등 모든 파생 동작을 일괄 차단)
            if (heatEntry != null) {
                Optional<EventResult> existingEr = eventResultRepository.findByHeatEntryId(heatEntry.getId());
                if (existingEr.isPresent() && !source.canOverwrite(existingEr.get().getSource())) {
                    log.info("상위 출처 결과 보호로 스킵: name={} bib={} existing-source={} new-source={}",
                            pr.athleteName(), pr.bibNumber(), existingEr.get().getSource(), source);
                    matchedHeatEntryIds.add(heatEntry.getId());  // cleanup에서 삭제되지 않도록 matched 처리
                    continue;
                }
            }

            // === 3) bib 충돌 해결. 충돌 소유자가 상위 출처 결과를 가지면 이 행 전체 스킵 ===
            boolean needsBibChange = (heatEntry == null)
                    || heatEntry.getBibNumber() == null
                    || heatEntry.getBibNumber().intValue() != pr.bibNumber();
            if (needsBibChange) {
                HeatEntry bibOwner = entryByBib.get(pr.bibNumber());
                if (bibOwner != null && bibOwner != heatEntry && !canEvictBibOwner(bibOwner, source)) {
                    log.info("bib {} 소유자가 상위 출처 결과라 스킵: name={} new-source={}",
                            pr.bibNumber(), pr.athleteName(), source);
                    if (heatEntry != null) matchedHeatEntryIds.add(heatEntry.getId());
                    continue;
                }
                evictBibConflict(pr.bibNumber(), heatEntry,
                        entryByBib, entryByName, entryByCeId, matchedHeatEntryIds);
            }

            // === 4) HeatEntry 업데이트 또는 생성 ===
            if (heatEntry != null) {
                if (needsBibChange) {
                    entryByBib.remove(heatEntry.getBibNumber());
                    heatEntry.updateBib(pr.bibNumber());
                    // Hibernate는 flush 시 INSERT → UPDATE → DELETE 순이라 bib swap 시나리오에서
                    // 뒤따르는 INSERT가 아직 flush되지 않은 UPDATE와 UK 충돌을 낼 수 있다. 즉시 flush하여 순서 고정.
                    heatEntryRepository.flush();
                    entryByBib.put(pr.bibNumber(), heatEntry);
                }
            } else {
                heatEntry = heatEntryRepository.save(HeatEntry.builder()
                        .heat(targetHeat).entry(compEntry).bibNumber(pr.bibNumber()).build());
                entryByBib.put(pr.bibNumber(), heatEntry);
                if (compEntry.getId() != null) entryByCeId.put(compEntry.getId(), heatEntry);
                newEntryCount++;
            }
            entryByName.put(pr.athleteName(), heatEntry);
            matchedHeatEntryIds.add(heatEntry.getId());

            // === 5) 재임포트 시 기존 CompetitionEntry의 소속/시도도 최신 파싱 결과로 업데이트 (미매핑인 경우만) ===
            CompetitionEntry ce = heatEntry.getEntry();
            if (ce != null && !ce.isMapped()) {
                ce.updateFromParsed(pr.region(), pr.teamName());
            }

            // === 6) EventResult 저장/갱신 (source 체크는 2단계에서 이미 수행됨) ===
            Optional<EventResult> existing = eventResultRepository.findByHeatEntryId(heatEntry.getId());
            if (existing.isPresent()) {
                EventResult er = existing.get();
                er.updateResult(pr.ranking(), pr.record(), pr.newRecord(), pr.qualification(), pr.note(), source);
                writeHistory(er, source);
            } else {
                EventResult saved = eventResultRepository.save(EventResult.builder()
                        .heatEntry(heatEntry).ranking(pr.ranking()).record(pr.record())
                        .newRecord(pr.newRecord()).qualification(pr.qualification()).note(pr.note())
                        .source(source).build());
                writeHistory(saved, source);
            }
            resultCount++;
        }

        // 결과에 없는 사전등록 엔트리 제거 (result PDF가 있어야 지움 → 파싱 실패 시 보호)
        // 단, AUTO 소스는 MANUAL/UPLOAD로 기록된 EventResult를 보유한 HeatEntry는 건드리지 않는다.
        if (!results.isEmpty() && !existingEntries.isEmpty()) {
            List<Long> candidates = existingEntries.stream()
                    .map(HeatEntry::getId)
                    .filter(id -> !matchedHeatEntryIds.contains(id))
                    .toList();
            List<Long> toDelete = source == ResultSource.AUTO
                    ? filterAutoDeletable(candidates) : candidates;
            if (!toDelete.isEmpty()) {
                eventResultRepository.deleteByHeatEntryIdIn(toDelete);
                heatEntryRepository.deleteAllById(toDelete);
                log.info("결과에 없는 사전등록 엔트리 {}건 제거 (heat_id={}, source={})",
                        toDelete.size(), targetHeat.getId(), source);
            }
            if (source == ResultSource.AUTO && toDelete.size() < candidates.size()) {
                log.info("AUTO 소스라 상위 출처 결과를 보유한 HeatEntry {}건은 삭제 스킵",
                        candidates.size() - toDelete.size());
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
            // ranking만 재계산; 소스는 건드리지 않는다 (DTT 재정렬은 내부 계산, 외부 덮어쓰기 아님)
            er.updateResult(newRanking, er.getRecord(), er.getNewRecord(),
                    er.getQualification(), er.getNote(), null);
        }
        log.info("DTT rankings recalculated for round {}: {} results", eventRoundId, allResults.size());
    }

    /** 모든 EventResult 저장/수정 시 동일한 스냅샷을 history에 append. */
    private void writeHistory(EventResult er, ResultSource source) {
        eventResultHistoryRepository.save(EventResultHistory.builder()
                .eventResultId(er.getId())
                .heatEntryId(er.getHeatEntry().getId())
                .source(source)
                .ranking(er.getRanking()).record(er.getRecord()).newRecord(er.getNewRecord())
                .qualification(er.getQualification()).note(er.getNote())
                .build());
    }

    /** AUTO 소스 cleanup 대상 중, 상위 출처(MANUAL/UPLOAD)가 기록한 EventResult가 붙은 HeatEntry는 제외. */
    private List<Long> filterAutoDeletable(List<Long> heatEntryIds) {
        if (heatEntryIds.isEmpty()) return heatEntryIds;
        Set<Long> protectedIds = eventResultRepository.findByHeatEntryIdIn(heatEntryIds).stream()
                .filter(er -> er.getSource() == ResultSource.MANUAL || er.getSource() == ResultSource.UPLOAD)
                .map(er -> er.getHeatEntry().getId())
                .collect(java.util.stream.Collectors.toSet());
        return heatEntryIds.stream().filter(id -> !protectedIds.contains(id)).toList();
    }

    /**
     * bib 소유자를 이번 source가 evict할 수 있는지 검사. 소유자의 EventResult가 상위 출처(MANUAL/UPLOAD)인
     * 상태에서 현재 source가 AUTO라면 evict 불가 → 호출부에서 전체 row를 스킵해야 한다.
     */
    private boolean canEvictBibOwner(HeatEntry bibOwner, ResultSource source) {
        Optional<EventResult> er = eventResultRepository.findByHeatEntryId(bibOwner.getId());
        return er.isEmpty() || source.canOverwrite(er.get().getSource());
    }

    /**
     * 같은 heat 안에서 bib가 다른 HeatEntry에 선점돼 있으면 그 HeatEntry와 결과를 삭제해 (heat_id, bib_number) UK 충돌을 막는다.
     * owner는 이번 결과를 담을 HeatEntry(없으면 null): 자기 자신은 건드리지 않는다.
     *
     * 삭제된 엔트리는 이번 파싱 결과에서 다른 선수로 교체될 대상이다. CompetitionEntry는 보존되고
     * 이후 이름 매칭 실패로 같은 CE를 재사용하는 로직(entryByCeId)이 HeatEntry를 재생성한다.
     *
     * 상위 출처 보호는 호출 전에 {@link #canEvictBibOwner}로 확인해야 한다.
     */
    private void evictBibConflict(Integer bib, HeatEntry owner,
                                  Map<Integer, HeatEntry> entryByBib,
                                  Map<String, HeatEntry> entryByName,
                                  Map<Long, HeatEntry> entryByCeId,
                                  Set<Long> matchedHeatEntryIds) {
        HeatEntry conflict = entryByBib.get(bib);
        if (conflict == null || conflict == owner) return;

        eventResultRepository.deleteByHeatEntryIdIn(List.of(conflict.getId()));
        heatEntryRepository.delete(conflict);
        heatEntryRepository.flush();

        entryByBib.remove(bib);
        entryByName.values().removeIf(he -> he == conflict);
        entryByCeId.values().removeIf(he -> he == conflict);
        matchedHeatEntryIds.remove(conflict.getId());
    }

    private CompetitionEntry findOrCreateCompetitionEntry(Long competitionId, String athleteName,
                                                          String gender, String region, String teamName) {
        String normalizedTeam = CompetitionEntry.normalizeTeamName(teamName);
        // 과거 버그로 중복 CE가 존재할 수 있어 List로 조회해 가장 오래된 것(최소 id) 사용
        List<CompetitionEntry> found = competitionEntryRepository
                .findAllByCompetitionIdAndAthleteNameAndGenderAndTeamName(
                        competitionId, athleteName, gender, normalizedTeam);
        if (!found.isEmpty()) {
            return found.stream().min(Comparator.comparing(CompetitionEntry::getId)).orElseThrow();
        }
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("대회를 찾을 수 없습니다. id=" + competitionId));
        return competitionEntryRepository.save(CompetitionEntry.builder()
                .competition(competition)
                .athleteName(athleteName).gender(gender).region(region).teamName(normalizedTeam).build());
    }

}