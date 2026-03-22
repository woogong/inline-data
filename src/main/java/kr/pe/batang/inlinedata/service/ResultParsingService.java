package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
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

    private final EventRepository eventRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;
    private final EventResultRepository eventResultRepository;

    @Transactional
    public int parseResultDirectory(Path baseDir, Long competitionId) {
        int totalParsed = 0;
        try (DirectoryStream<Path> days = Files.newDirectoryStream(baseDir)) {
            for (Path dayDir : days) {
                if (!Files.isDirectory(dayDir)) continue;
                try (DirectoryStream<Path> pdfs = Files.newDirectoryStream(dayDir, "*.pdf")) {
                    for (Path pdf : pdfs) {
                        try {
                            totalParsed += parseResultPdf(pdf, competitionId);
                        } catch (Exception e) {
                            log.warn("Failed to parse {}: {}", pdf.getFileName(), e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to read directory: {}", baseDir, e);
        }
        return totalParsed;
    }

    @Transactional
    public int parseResultPdf(Path pdfPath, Long competitionId) throws IOException {
        String text = extractText(pdfPath);
        if (text == null || text.isBlank()) return 0;

        String[] lines = text.split("\n");

        // 헤더 파싱: "1-1 ..." 또는 "10 ..."
        int eventNumber = 0;
        int heatNumber = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            Matcher headerMatch = Pattern.compile("^(\\d+)(?:-(\\d+))?\\s+.+").matcher(trimmed);
            if (headerMatch.matches() && !trimmed.startsWith("순위") && !trimmed.matches("^\\d+\\s+\\d+\\s+.*")) {
                eventNumber = Integer.parseInt(headerMatch.group(1));
                heatNumber = headerMatch.group(2) != null ? Integer.parseInt(headerMatch.group(2)) : 0;
                break;
            }
        }

        if (eventNumber == 0) return 0;

        final int finalHeatNumber = heatNumber;

        // Event 찾기
        Optional<Event> eventOpt = eventRepository.findByCompetitionIdAndEventNumber(competitionId, eventNumber);
        if (eventOpt.isEmpty()) return 0;

        // EventHeat 찾기
        List<EventHeat> heats = eventHeatRepository.findByEventIdOrderByHeatNumberAsc(eventOpt.get().getId());
        EventHeat targetHeat = heats.stream()
                .filter(h -> h.getHeatNumber() == finalHeatNumber)
                .findFirst()
                .orElse(null);
        if (targetHeat == null) return 0;

        // HeatEntry 목록 (배번으로 매칭, 중복 시 first wins — 계주는 팀 단위이므로 배번 중복 없음)
        List<HeatEntry> entries = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(targetHeat.getId());
        Map<Integer, HeatEntry> entryByBib = entries.stream()
                .collect(Collectors.toMap(
                        HeatEntry::getBibNumber,
                        e -> e,
                        (existing, replacement) -> existing
                ));

        // 결과 행 파싱
        List<ParsedResult> results = parseResultLines(lines);

        int saved = 0;
        for (ParsedResult pr : results) {
            HeatEntry entry = entryByBib.get(pr.bibNumber);
            if (entry == null) {
                log.debug("Bib {} not found in heat (event={}, heat={})", pr.bibNumber, eventNumber, heatNumber);
                continue;
            }

            Optional<EventResult> existing = eventResultRepository.findByHeatEntryId(entry.getId());
            if (existing.isPresent()) {
                existing.get().updateResult(pr.ranking, pr.record, pr.newRecord, pr.qualification, pr.note);
            } else {
                eventResultRepository.save(EventResult.builder()
                        .heatEntry(entry)
                        .ranking(pr.ranking)
                        .record(pr.record)
                        .newRecord(pr.newRecord)
                        .qualification(pr.qualification)
                        .note(pr.note)
                        .build());
            }
            saved++;
        }
        return saved;
    }

    private List<ParsedResult> parseResultLines(String[] lines) {
        List<ParsedResult> results = new ArrayList<>();

        boolean inData = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("순위")) {
                inData = true;
                continue;
            }
            if (!inData) continue;
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("기록확인") || trimmed.startsWith("심판") || trimmed.startsWith("경기부장")
                    || trimmed.startsWith("대한롤러") || trimmed.matches("^\\d{4}\\..*")
                    || trimmed.startsWith("(")) {
                // 괄호로 시작하는 줄은 계주 멤버 목록이므로 스킵
                continue;
            }

            Matcher m = Pattern.compile(
                    "^(\\d+|EL|DNF|DNS|DSQ|DF)\\s+(\\d+)\\s+(.+)$"
            ).matcher(trimmed);

            if (m.matches()) {
                String rankStr = m.group(1);
                int bib = Integer.parseInt(m.group(2));
                String rest = m.group(3).trim();

                Integer ranking = null;
                try {
                    ranking = Integer.parseInt(rankStr);
                } catch (NumberFormatException ignored) {
                }

                String record = null;
                String newRecord = null;
                String qualification = null;
                String note = null;

                Matcher recordMatch = Pattern.compile("(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})").matcher(rest);
                if (recordMatch.find()) {
                    record = recordMatch.group(1);
                }

                if (rest.contains("세계신")) newRecord = "세계신";
                else if (rest.contains("한국신")) newRecord = "한국신";
                else if (rest.contains("부별신")) newRecord = "부별신";
                else if (rest.contains("대회신")) newRecord = "대회신";

                if (rest.contains("Q")) qualification = "Q";

                Matcher noteMatch = Pattern.compile("(제외|실격|점수줌|낙차|경고|주의|\\(점수줌\\)제외)").matcher(rest);
                if (noteMatch.find()) {
                    note = noteMatch.group(1);
                }

                if (ranking == null) {
                    String statusNote = switch (rankStr) {
                        case "EL" -> "제외";
                        case "DF" -> "실격";
                        default -> rankStr;
                    };
                    note = note != null ? note + " " + statusNote : statusNote;
                }

                results.add(new ParsedResult(bib, ranking, record, newRecord, qualification, note));
            }
        }
        return results;
    }

    private String extractText(Path pdfPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("pdftotext", "-layout", pdfPath.toString(), "-");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            process.waitFor();
            return sb.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private record ParsedResult(int bibNumber, Integer ranking, String record,
                                String newRecord, String qualification, String note) {
    }
}
