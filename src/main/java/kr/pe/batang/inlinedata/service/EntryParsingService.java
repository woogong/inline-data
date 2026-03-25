package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.repository.AthleteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryParsingService {

    private final AthleteRepository athleteRepository;
    private final PdfTextExtractor pdfTextExtractor;

    // 종목 헤더: "1 여초부 5,6학년 500m+D 예선" 등
    private static final Pattern EVENT_HEADER_PATTERN = Pattern.compile(
            "^\\s*(\\d+)\\s+(여|남)\\S+부\\s+.*$"
    );

    // 선수 행: "4   구예림 (경기 팀에스6)" 또는 "4   구예림 (경기 경기 팀에스6)"
    private static final Pattern ATHLETE_PATTERN = Pattern.compile(
            "^\\s*(\\d+)\\s+(\\S+)\\s+\\((.+?)\\)\\s*$"
    );

    // 소속 정보에서 끝의 학년 숫자 제거: "경기 팀에스6" -> "경기 팀에스"
    private static final Pattern AFFILIATION_PATTERN = Pattern.compile(
            "^(.+?)\\d*$"
    );

    @Transactional
    public ImportResult parseEntryPdf(Path pdfPath) throws IOException {
        String text = pdfTextExtractor.extractText(pdfPath);
        if (text == null || text.isBlank()) {
            return new ImportResult(0, 0);
        }

        String[] lines = text.split("\n");
        List<ParsedAthlete> athletes = parseAthleteLines(lines);

        int created = 0;
        int skipped = 0;

        for (ParsedAthlete pa : athletes) {
            if (athleteRepository.existsByNameAndGenderAndNotes(pa.name, pa.gender, pa.notes)) {
                skipped++;
            } else {
                athleteRepository.save(Athlete.builder()
                        .name(pa.name)
                        .gender(pa.gender)
                        .notes(pa.notes)
                        .build());
                created++;
            }
        }

        log.info("Entry parsing complete: {} created, {} skipped (duplicates)", created, skipped);
        return new ImportResult(created, skipped);
    }

    // 계주, 팀DTT 등 팀 종목 감지
    private static final Pattern TEAM_EVENT_PATTERN = Pattern.compile(
            ".*(계주|팀DTT|팀dtt).*"
    );

    List<ParsedAthlete> parseAthleteLines(String[] lines) {
        List<ParsedAthlete> athletes = new ArrayList<>();
        String currentGender = null;
        boolean isTeamEvent = false;

        for (String line : lines) {
            // 종목 헤더에서 성별 추출 + 팀 종목 판별
            Matcher headerMatcher = EVENT_HEADER_PATTERN.matcher(line);
            if (headerMatcher.matches()) {
                currentGender = headerMatcher.group(2).equals("여") ? "F" : "M";
                isTeamEvent = TEAM_EVENT_PATTERN.matcher(line).matches();
                continue;
            }

            // 팀 종목이면 선수 파싱 스킵
            if (isTeamEvent) continue;

            // 선수 행 파싱
            Matcher athleteMatcher = ATHLETE_PATTERN.matcher(line);
            if (athleteMatcher.matches() && currentGender != null) {
                String name = athleteMatcher.group(2);
                String rawAffiliation = athleteMatcher.group(3).trim();

                // 학년 숫자 제거
                Matcher affMatcher = AFFILIATION_PATTERN.matcher(rawAffiliation);
                String notes = affMatcher.matches() ? affMatcher.group(1).trim() : rawAffiliation;

                athletes.add(new ParsedAthlete(name, currentGender, notes));
            }
        }

        // 동일 PDF 내 중복 제거 (같은 선수가 여러 종목에 출전)
        return athletes.stream()
                .distinct()
                .toList();
    }

    record ParsedAthlete(String name, String gender, String notes) {}

    public record ImportResult(int created, int skipped) {}
}
