package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.Team;
import kr.pe.batang.inlinedata.repository.AthleteRepository;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.TeamRepository;
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
    private final TeamRepository teamRepository;

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

    @Transactional
    public void unmap(Long entryId) {
        CompetitionEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("엔트리를 찾을 수 없습니다. id=" + entryId));
        entry.unmapAthlete();
    }

    // ===== Team 매핑 =====

    public record TeamCandidateDto(Long teamId, String name, String region) {}

    public List<TeamCandidateDto> findTeamCandidates(Long entryId) {
        CompetitionEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("엔트리를 찾을 수 없습니다. id=" + entryId));
        String teamName = entry.getTeamName();
        if (teamName == null || teamName.isBlank()) return List.of();

        return teamRepository.findAll().stream()
                .filter(t -> t.getName().contains(teamName) || teamName.contains(t.getName()))
                .map(t -> new TeamCandidateDto(t.getId(), t.getName(), t.getRegion()))
                .toList();
    }

    @Transactional
    public int autoMatchTeams(Long competitionId) {
        List<CompetitionEntry> unmapped = entryRepository.findByCompetitionIdAndTeamIsNull(competitionId);
        int matched = 0;

        Map<String, List<CompetitionEntry>> grouped = unmapped.stream()
                .filter(e -> e.getTeamName() != null && !e.getTeamName().isBlank()
                        && e.getRegion() != null && !e.getRegion().isBlank())
                .collect(Collectors.groupingBy(e -> e.getTeamName() + "|" + e.getRegion()));

        for (Map.Entry<String, List<CompetitionEntry>> group : grouped.entrySet()) {
            List<CompetitionEntry> entries = group.getValue();
            CompetitionEntry sample = entries.getFirst();

            Team team = teamRepository.findByNameAndRegion(sample.getTeamName(), sample.getRegion())
                    .orElseGet(() -> teamRepository.save(Team.builder()
                            .name(sample.getTeamName())
                            .region(sample.getRegion())
                            .build()));

            for (CompetitionEntry entry : entries) {
                entry.mapTeam(team);
                matched++;
            }
        }
        return matched;
    }

    @Transactional
    public void mapToTeam(Long entryId, Long teamId) {
        CompetitionEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("엔트리를 찾을 수 없습니다. id=" + entryId));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다. id=" + teamId));
        entry.mapTeam(team);
    }

    @Transactional
    public Long createAndMapTeam(Long entryId) {
        CompetitionEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("엔트리를 찾을 수 없습니다. id=" + entryId));
        Team team = teamRepository.save(Team.builder()
                .name(entry.getTeamName())
                .region(entry.getRegion())
                .build());
        entry.mapTeam(team);
        return team.getId();
    }

    @Transactional
    public void unmapTeam(Long entryId) {
        CompetitionEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("엔트리를 찾을 수 없습니다. id=" + entryId));
        entry.unmapTeam();
    }

    // ===== 일괄 등록 =====

    public record BulkRegisterResult(int teamsCreated, int teamsMapped, int athletesCreated, int athletesMapped) {}

    @Transactional
    public BulkRegisterResult bulkRegister(Long competitionId) {
        List<CompetitionEntry> entries = entryRepository.findIndividualEntriesWithAthlete(competitionId);

        int teamsCreated = 0, teamsMapped = 0, athletesCreated = 0, athletesMapped = 0;

        // 1. Team 등록+매핑
        Map<String, List<CompetitionEntry>> teamGroups = entries.stream()
                .filter(e -> e.getTeam() == null && e.getTeamName() != null && !e.getTeamName().isBlank()
                        && e.getRegion() != null && !e.getRegion().isBlank())
                .collect(Collectors.groupingBy(e -> e.getTeamName() + "|" + e.getRegion()));

        for (Map.Entry<String, List<CompetitionEntry>> group : teamGroups.entrySet()) {
            List<CompetitionEntry> ces = group.getValue();
            CompetitionEntry sample = ces.getFirst();
            boolean existed = teamRepository.findByNameAndRegion(sample.getTeamName(), sample.getRegion()).isPresent();
            Team team = teamRepository.findByNameAndRegion(sample.getTeamName(), sample.getRegion())
                    .orElseGet(() -> teamRepository.save(Team.builder()
                            .name(sample.getTeamName()).region(sample.getRegion()).build()));
            if (!existed) teamsCreated++;
            for (CompetitionEntry ce : ces) {
                ce.mapTeam(team);
                teamsMapped++;
            }
        }

        // 2. Athlete 등록+매핑 (이름+성별+팀명으로 그룹화, 기존 매핑 보정 포함)
        Map<String, List<CompetitionEntry>> athleteGroups = entries.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getAthleteName() + "|" + e.getGender() + "|" + (e.getTeamName() != null ? e.getTeamName() : "")));

        for (Map.Entry<String, List<CompetitionEntry>> group : athleteGroups.entrySet()) {
            List<CompetitionEntry> ces = group.getValue();
            CompetitionEntry sample = ces.getFirst();
            String sampleTeam = sample.getTeamName() != null ? sample.getTeamName() : "";

            // 같은 이름+성별+팀명 그룹에서 올바른 Athlete 결정
            Athlete correctAthlete = null;

            // 이미 이 그룹에 매핑된 athlete가 있고, 그 athlete의 모든 엔트리가 같은 팀이면 사용
            for (CompetitionEntry ce : ces) {
                if (ce.getAthlete() != null) {
                    List<CompetitionEntry> existingEntries = entryRepository.findByAthleteId(ce.getAthlete().getId());
                    // 해당 athlete의 모든 엔트리가 같은 팀인지 확인
                    boolean allSameTeam = existingEntries.stream()
                            .allMatch(he -> sampleTeam.equals(he.getTeamName() != null ? he.getTeamName() : ""));
                    if (allSameTeam) {
                        correctAthlete = ce.getAthlete();
                        break;
                    }
                    // 다른 팀 엔트리가 섞여 있으면 잘못된 매핑 → 기존 매핑 무시하고 새로 판단
                }
            }

            if (correctAthlete == null) {
                // 기존 Athlete 검색
                List<Athlete> candidates = athleteRepository.findByNameAndGender(sample.getAthleteName(), sample.getGender());

                if (candidates.isEmpty()) {
                    correctAthlete = athleteRepository.save(Athlete.builder()
                            .name(sample.getAthleteName())
                            .gender(sample.getGender())
                            .build());
                    athletesCreated++;
                } else {
                    // 같은 팀으로만 매핑된 후보 검색 (다른 팀이 섞여 있으면 제외)
                    List<Athlete> teamMatched = candidates.stream()
                            .filter(a -> {
                                List<CompetitionEntry> aEntries = entryRepository.findByAthleteId(a.getId());
                                return !aEntries.isEmpty() && aEntries.stream()
                                        .allMatch(he -> sampleTeam.equals(he.getTeamName() != null ? he.getTeamName() : ""));
                            })
                            .toList();
                    if (teamMatched.size() == 1) {
                        correctAthlete = teamMatched.getFirst();
                    } else {
                        // 새 Athlete 생성
                        String note = sample.getRegion() != null ? sample.getRegion() + " " + sampleTeam : sampleTeam;
                        correctAthlete = athleteRepository.save(Athlete.builder()
                                .name(sample.getAthleteName())
                                .gender(sample.getGender())
                                .notes(note.isBlank() ? null : note)
                                .build());
                        athletesCreated++;
                    }
                }
            }

            // 매핑 적용 (잘못된 매핑 보정 포함)
            for (CompetitionEntry ce : ces) {
                if (ce.getAthlete() == null || !ce.getAthlete().getId().equals(correctAthlete.getId())) {
                    ce.mapAthlete(correctAthlete);
                    athletesMapped++;
                }
            }
        }

        return new BulkRegisterResult(teamsCreated, teamsMapped, athletesCreated, athletesMapped);
    }
}