package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventRoundRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryImportService {

    private final EventRepository eventRepository;
    private final EventRoundRepository eventRoundRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;
    private final CompetitionEntryRepository competitionEntryRepository;
    private final PdfTextExtractor pdfTextExtractor;

    // "67 여초부 일반(B조) 1,2학년 300m 조별결승" or "1 여초부 5,6학년 500m+D 예선"
    private static final Pattern EVENT_HEADER = Pattern.compile(
            "^\\s*(\\d+)\\s+(여|남)(.+?)\\s+(\\S+)\\s+(예선|준준결승|준결승|결승|조별결승)\\s*$"
    );
    private static final Pattern EVENT_HEADER_NO_ROUND = Pattern.compile(
            "^\\s*(\\d+)\\s+(여|남)(\\S+부.*)\\s+(\\S+m\\S*)\\s*$"
    );
    private static final Pattern HEAT_HEADER = Pattern.compile("^\\s*<\\s*(\\d+)조\\s*>\\s*$");
    private static final Pattern HEAT_FINAL = Pattern.compile("^\\s*<\\s*결승\\s*>\\s*$");
    private static final Pattern ATHLETE_LINE = Pattern.compile("^\\s*(\\d+)\\s+(\\S+)\\s+\\((.+?)\\)\\s*$");
    // 단체전: "   팀명 (시도 팀명)" 또는 "   0   팀명 (시도 팀명)"
    private static final Pattern TEAM_ENTRY_LINE = Pattern.compile("^\\s*(?:\\d+\\s+)?(.+?)\\s+\\((.+?)\\)\\s*$");
    private static final Pattern DAY_HEADER = Pattern.compile("^\\s*제(\\d)일차.*$");
    private static final Pattern TEAM_EVENT = Pattern.compile(".*(계주|팀DTT|팀dtt).*");

    @Transactional
    public ImportResult importEntryPdf(Path pdfPath, Competition competition) throws IOException {
        String text = pdfTextExtractor.extractText(pdfPath);
        if (text == null || text.isBlank()) return new ImportResult(0, 0, 0, 0);

        String[] lines = text.split("\n");
        int eventCount = 0, roundCount = 0, heatCount = 0, entryCount = 0;

        Event currentEvent = null;
        EventRound currentRound = null;
        EventHeat currentHeat = null;
        String currentGender = null;
        int currentDay = 0;
        boolean isTeamEvent = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // 일차
            Matcher dayMatch = DAY_HEADER.matcher(trimmed);
            if (dayMatch.matches()) {
                currentDay = Integer.parseInt(dayMatch.group(1));
                continue;
            }

            // 종목+라운드 헤더
            Matcher eventMatch = EVENT_HEADER.matcher(trimmed);
            if (eventMatch.matches()) {
                int eventNumber = Integer.parseInt(eventMatch.group(1));
                currentGender = eventMatch.group(2).equals("여") ? "F" : "M";
                String divisionName = eventMatch.group(2) + eventMatch.group(3);
                String eventName = eventMatch.group(4);
                String round = eventMatch.group(5);
                isTeamEvent = TEAM_EVENT.matcher(eventName).matches();

                boolean eventExisted = eventRepository.findByCompetitionIdAndDivisionNameAndGenderAndEventName(
                        competition.getId(), divisionName, currentGender, eventName).isPresent();
                currentEvent = findOrCreateEvent(competition, divisionName, currentGender, eventName, isTeamEvent);
                if (!eventExisted) eventCount++;
                currentRound = findOrCreateRound(currentEvent, round, eventNumber, currentDay);
                roundCount++;
                currentHeat = null;
                continue;
            }

            // 라운드 없는 종목 헤더
            Matcher noRoundMatch = EVENT_HEADER_NO_ROUND.matcher(trimmed);
            if (noRoundMatch.matches() && !trimmed.matches("^\\s*\\d+\\s+\\d+\\s+.*")) {
                int eventNumber = Integer.parseInt(noRoundMatch.group(1));
                currentGender = noRoundMatch.group(2).equals("여") ? "F" : "M";
                String divisionName = noRoundMatch.group(2) + noRoundMatch.group(3);
                String eventName = noRoundMatch.group(4);
                isTeamEvent = TEAM_EVENT.matcher(eventName).matches();

                boolean eventExisted2 = eventRepository.findByCompetitionIdAndDivisionNameAndGenderAndEventName(
                        competition.getId(), divisionName, currentGender, eventName).isPresent();
                currentEvent = findOrCreateEvent(competition, divisionName, currentGender, eventName, isTeamEvent);
                if (!eventExisted2) eventCount++;
                currentRound = findOrCreateRound(currentEvent, "결승", eventNumber, currentDay);
                roundCount++;
                currentHeat = null;
                continue;
            }

            // 조 헤더
            Matcher heatMatch = HEAT_HEADER.matcher(trimmed);
            if (heatMatch.matches() && currentRound != null) {
                currentHeat = findOrCreateHeat(currentRound, Integer.parseInt(heatMatch.group(1)));
                heatCount++;
                continue;
            }
            if (HEAT_FINAL.matcher(trimmed).matches() && currentRound != null) {
                currentHeat = findOrCreateHeat(currentRound, 0);
                heatCount++;
                continue;
            }

            // 개인전 선수 행
            if (!isTeamEvent) {
                Matcher athleteMatch = ATHLETE_LINE.matcher(trimmed);
                if (athleteMatch.matches() && currentRound != null && currentGender != null) {
                    if (currentHeat == null) {
                        currentHeat = findOrCreateHeat(currentRound, 0);
                        heatCount++;
                    }
                    int bibNumber = Integer.parseInt(athleteMatch.group(1));
                    String name = athleteMatch.group(2);
                    String info = athleteMatch.group(3).trim();

                    String[] parts = info.split(" ", 2);
                    String region = parts[0];
                    String rest = parts.length > 1 ? parts[1] : "";
                    if (rest.startsWith(region + " ")) rest = rest.substring(region.length() + 1);

                    Integer grade = null;
                    String teamNameStr = rest;
                    Matcher gradeMatch = Pattern.compile("(\\d+)$").matcher(rest);
                    if (gradeMatch.find()) {
                        grade = Integer.parseInt(gradeMatch.group(1));
                        teamNameStr = rest.substring(0, gradeMatch.start());
                    }

                    CompetitionEntry compEntry = findOrCreateCompetitionEntry(
                            competition, name.trim(), currentGender, region.trim(), teamNameStr.trim(), grade);
                    addHeatEntry(currentHeat, compEntry, bibNumber);
                    entryCount++;
                }
                continue;
            }

            // 단체전 엔트리 행: "팀명 (시도 팀명)" 또는 "0  팀명 (시도 팀명)"
            // 괄호로 시작하면 멤버 목록이므로 스킵
            if (trimmed.startsWith("(")) continue;

            Matcher teamEntryMatch = TEAM_ENTRY_LINE.matcher(trimmed);
            if (teamEntryMatch.matches() && currentRound != null && currentGender != null) {
                if (currentHeat == null) {
                    currentHeat = findOrCreateHeat(currentRound, 0);
                    heatCount++;
                }
                String teamEntryName = teamEntryMatch.group(1).trim();
                String info = teamEntryMatch.group(2).trim();

                String[] parts = info.split(" ", 2);
                String region = parts[0];
                String teamNameStr = parts.length > 1 ? parts[1] : teamEntryName;
                if (teamNameStr.startsWith(region + " ")) teamNameStr = teamNameStr.substring(region.length() + 1);

                // 단체전: athleteName에 팀명 저장, bibNumber는 0
                int bibNumber = 0;
                Matcher bibMatch = Pattern.compile("^\\s*(\\d+)\\s+").matcher(trimmed);
                if (bibMatch.find()) bibNumber = Integer.parseInt(bibMatch.group(1));

                CompetitionEntry compEntry = findOrCreateCompetitionEntry(
                        competition, teamEntryName, currentGender, region.trim(), teamNameStr.trim(), null);
                addHeatEntry(currentHeat, compEntry, bibNumber);
                entryCount++;
            }
        }

        log.info("Entry import: {} rounds, {} heats, {} entries", roundCount, heatCount, entryCount);
        return new ImportResult(eventCount, roundCount, heatCount, entryCount);
    }

    private void addHeatEntry(EventHeat heat, CompetitionEntry compEntry, int bibNumber) {
        boolean exists = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(heat.getId())
                .stream().anyMatch(he -> he.getEntry().getId().equals(compEntry.getId()));
        if (!exists) {
            heatEntryRepository.save(HeatEntry.builder()
                    .heat(heat).entry(compEntry).bibNumber(bibNumber).build());
        }
    }

    private Event findOrCreateEvent(Competition competition, String divisionName,
                                    String gender, String eventName, boolean teamEvent) {
        return eventRepository.findByCompetitionIdAndDivisionNameAndGenderAndEventName(
                        competition.getId(), divisionName, gender, eventName)
                .orElseGet(() -> eventRepository.save(Event.builder()
                        .competition(competition).divisionName(divisionName)
                        .gender(gender).eventName(eventName).teamEvent(teamEvent).build()));
    }

    private EventRound findOrCreateRound(Event event, String round,
                                         Integer eventNumber, Integer dayNumber) {
        return eventRoundRepository.findByEventIdAndRound(event.getId(), round)
                .orElseGet(() -> eventRoundRepository.save(EventRound.builder()
                        .event(event).round(round).eventNumber(eventNumber)
                        .dayNumber(dayNumber).build()));
    }

    private EventHeat findOrCreateHeat(EventRound round, int heatNumber) {
        return eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(round.getId())
                .stream().filter(h -> h.getHeatNumber() == heatNumber).findFirst()
                .orElseGet(() -> eventHeatRepository.save(EventHeat.builder()
                        .eventRound(round).heatNumber(heatNumber).build()));
    }

    private CompetitionEntry findOrCreateCompetitionEntry(Competition competition,
                                                          String athleteName, String gender,
                                                          String region, String teamName,
                                                          Integer grade) {
        return competitionEntryRepository
                .findByCompetitionIdAndAthleteNameAndGenderAndTeamName(
                        competition.getId(), athleteName, gender, teamName)
                .orElseGet(() -> competitionEntryRepository.save(CompetitionEntry.builder()
                        .competition(competition)
                        .athleteName(athleteName)
                        .gender(gender)
                        .region(region)
                        .teamName(teamName)
                        .grade(grade)
                        .build()));
    }

    public record ImportResult(int events, int rounds, int heats, int entries) {}
}
