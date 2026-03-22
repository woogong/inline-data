package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.repository.AthleteRepository;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MappingService {

    private final CompetitionEntryRepository entryRepository;
    private final AthleteRepository athleteRepository;

    public record HistoryDto(String competitionName, String teamName, String region, Integer grade) {}

    public record CandidateDto(
            Long athleteId, String name, String gender, Integer birthYear, String notes,
            List<HistoryDto> history
    ) {}

    /**
     * Find match candidates for a CompetitionEntry.
     * Queries Athlete by name+gender, then loads their CompetitionEntry history.
     */
    public List<CandidateDto> findCandidates(Long entryId) {
        CompetitionEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("엔트리를 찾을 수 없습니다. id=" + entryId));

        List<Athlete> athletes = athleteRepository.findByNameAndGender(entry.getAthleteName(), entry.getGender());

        return athletes.stream()
                .map(athlete -> {
                    List<CompetitionEntry> historyEntries = entryRepository.findByAthleteId(athlete.getId());
                    List<HistoryDto> history = historyEntries.stream()
                            .map(he -> new HistoryDto(
                                    he.getCompetition().getName(),
                                    he.getTeamName(),
                                    he.getRegion(),
                                    he.getGrade()
                            ))
                            .toList();
                    return new CandidateDto(
                            athlete.getId(),
                            athlete.getName(),
                            athlete.getGender(),
                            athlete.getBirthYear(),
                            athlete.getNotes(),
                            history
                    );
                })
                .toList();
    }

    /**
     * Auto-match: for each unmapped entry, find Athletes by name+gender.
     * If exactly one match exists, map it automatically.
     */
    @Transactional
    public int autoMatch(Long competitionId) {
        List<CompetitionEntry> unmapped = entryRepository.findIndividualUnmappedEntries(competitionId);
        int matched = 0;

        // Group by name+gender+teamName: 소속이 다르면 다른 선수로 판단
        Map<String, List<CompetitionEntry>> grouped = unmapped.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getAthleteName() + "|" + e.getGender() + "|" + (e.getTeamName() != null ? e.getTeamName() : "")));

        for (Map.Entry<String, List<CompetitionEntry>> group : grouped.entrySet()) {
            List<CompetitionEntry> entries = group.getValue();
            CompetitionEntry sample = entries.getFirst();
            String sampleTeam = sample.getTeamName() != null ? sample.getTeamName() : "";

            List<Athlete> candidates = athleteRepository.findByNameAndGender(
                    sample.getAthleteName(), sample.getGender());

            Athlete athlete;
            if (candidates.isEmpty()) {
                // 기존 Athlete 없음 → 새로 생성
                athlete = athleteRepository.save(Athlete.builder()
                        .name(sample.getAthleteName())
                        .gender(sample.getGender())
                        .build());
            } else {
                // 같은 팀으로 매핑된 이력이 있는 후보 필터
                List<Athlete> teamMatched = candidates.stream()
                        .filter(a -> entryRepository.findByAthleteId(a.getId()).stream()
                                .anyMatch(he -> sampleTeam.equals(he.getTeamName() != null ? he.getTeamName() : "")))
                        .toList();

                if (teamMatched.size() == 1) {
                    athlete = teamMatched.getFirst();
                } else {
                    continue; // 팀 매칭 불가 또는 동명이인 → 수동 매핑
                }
            }

            for (CompetitionEntry entry : entries) {
                entry.mapAthlete(athlete);
                matched++;
            }
        }

        return matched;
    }

    /**
     * Map a CompetitionEntry to an existing Athlete.
     */
    @Transactional
    public void mapToAthlete(Long entryId, Long athleteId) {
        CompetitionEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("엔트리를 찾을 수 없습니다. id=" + entryId));
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new IllegalArgumentException("선수를 찾을 수 없습니다. id=" + athleteId));
        entry.mapAthlete(athlete);
    }

    /**
     * Create a new Athlete from a CompetitionEntry's data and map it.
     */
    @Transactional
    public Long createAndMap(Long entryId, Integer birthYear) {
        CompetitionEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("엔트리를 찾을 수 없습니다. id=" + entryId));

        Athlete athlete = Athlete.builder()
                .name(entry.getAthleteName())
                .gender(entry.getGender())
                .birthYear(birthYear)
                .build();
        athleteRepository.save(athlete);

        entry.mapAthlete(athlete);
        return athlete.getId();
    }

    /**
     * Unmap a CompetitionEntry from its Athlete.
     */
    @Transactional
    public void unmap(Long entryId) {
        CompetitionEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("엔트리를 찾을 수 없습니다. id=" + entryId));
        entry.unmapAthlete();
    }
}