package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
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

    private final EventRoundRepository eventRoundRepository;
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

        final int finalEventNumber = eventNumber;
        final int finalHeatNumber = heatNumber;

        // EventRound 찾기 (eventNumber로)
        List<EventRound> rounds = eventRoundRepository.findByEvent_CompetitionIdOrderByEventNumberAsc(competitionId);
        EventRound targetRound = rounds.stream()
                .filter(r -> r.getEventNumber() != null && r.getEventNumber() == finalEventNumber)
                .findFirst().orElse(null);
        if (targetRound == null) return 0;

        // EventHeat 찾기
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(targetRound.getId());
        EventHeat targetHeat = heats.stream()
                .filter(h -> h.getHeatNumber() == finalHeatNumber)
                .findFirst().orElse(null);
        if (targetHeat == null) return 0;

        List<HeatEntry> entries = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(targetHeat.getId());
        Map<Integer, HeatEntry> entryByBib = entries.stream()
                .collect(Collectors.toMap(HeatEntry::getBibNumber, e -> e, (a, b) -> a));

        List<ParsedResult> results = parseResultLines(lines);

        int saved = 0;
        for (ParsedResult pr : results) {
            HeatEntry entry = entryByBib.get(pr.bibNumber);
            if (entry == null) continue;

            Optional<EventResult> existing = eventResultRepository.findByHeatEntryId(entry.getId());
            if (existing.isPresent()) {
                existing.get().updateResult(pr.ranking, pr.record, pr.newRecord, pr.qualification, pr.note);
            } else {
                eventResultRepository.save(EventResult.builder()
                        .heatEntry(entry).ranking(pr.ranking).record(pr.record)
                        .newRecord(pr.newRecord).qualification(pr.qualification).note(pr.note).build());
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
            if (trimmed.startsWith("순위")) { inData = true; continue; }
            if (!inData) continue;
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("기록확인") || trimmed.startsWith("심판") || trimmed.startsWith("경기부장")
                    || trimmed.startsWith("대한롤러") || trimmed.matches("^\\d{4}\\..*") || trimmed.startsWith("(")) continue;

            Matcher m = Pattern.compile("^(\\d+|EL|DNF|DNS|DSQ|DF)\\s+(\\d+)\\s+(.+)$").matcher(trimmed);
            if (m.matches()) {
                String rankStr = m.group(1);
                int bib = Integer.parseInt(m.group(2));
                String rest = m.group(3).trim();

                Integer ranking = null;
                try { ranking = Integer.parseInt(rankStr); } catch (NumberFormatException ignored) {}

                String record = null, newRecord = null, qualification = null, note = null;
                Matcher rm = Pattern.compile("(\\d+:\\d+\\.\\d+|\\d+\\.\\d{3})").matcher(rest);
                if (rm.find()) record = rm.group(1);
                if (rest.contains("세계신")) newRecord = "세계신";
                else if (rest.contains("한국신")) newRecord = "한국신";
                else if (rest.contains("부별신")) newRecord = "부별신";
                else if (rest.contains("대회신")) newRecord = "대회신";
                if (rest.contains("Q")) qualification = "Q";
                Matcher nm = Pattern.compile("(제외|실격|점수줌|낙차|경고|주의|\\(점수줌\\)제외)").matcher(rest);
                if (nm.find()) note = nm.group(1);
                if (ranking == null) {
                    String sn = switch (rankStr) { case "EL" -> "제외"; case "DF" -> "실격"; default -> rankStr; };
                    note = note != null ? note + " " + sn : sn;
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
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            process.waitFor();
            return sb.toString();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
    }

    private record ParsedResult(int bibNumber, Integer ranking, String record,
                                String newRecord, String qualification, String note) {}
}
