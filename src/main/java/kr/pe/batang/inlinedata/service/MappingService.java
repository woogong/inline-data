package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.entity.Team;
import kr.pe.batang.inlinedata.repository.AthleteRepository;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import kr.pe.batang.inlinedata.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MappingService {

    private final CompetitionEntryRepository entryRepository;
    private final AthleteRepository athleteRepository;
    private final TeamRepository teamRepository;
    private final HeatEntryRepository heatEntryRepository;

    /**
     * 부별명을 계열로 정규화한다.
     * 초등/중등/고등/대일 4가지 계열로 분류.
     */
    static String normalizeDivision(String divisionName) {
        if (divisionName == null) return "";
        if (divisionName.contains("초")) return "초등";
        if (divisionName.contains("중")) return "중등";
        if (divisionName.contains("고")) return "고등";
        // 대학, 일반, 대일 → 대일
        return "대일";
    }

    /**
     * 엔트리가 출전한 종목의 부별 계열 집합을 반환한다.
     */
    private Set<String> getDivisionCategories(Long entryId) {
        return heatEntryRepository.findByEntryId(entryId).stream()
                .map(he -> normalizeDivision(he.getHeat().getEventRound().getEvent().getDivisionName()))
                .collect(Collectors.toSet());
    }

    /**
     * 엔트리 ID → 부별 계열을 일괄 조회하여 캐시 맵을 만든다.
     * 단일 aggregated 쿼리로 N+1 제거.
     */
    private Map<Long, Set<String>> buildDivisionCache(List<CompetitionEntry> entries) {
        if (entries.isEmpty()) return new HashMap<>();
        List<Long> ids = entries.stream().map(CompetitionEntry::getId).toList();
        Map<Long, Set<String>> cache = new HashMap<>();
        for (Long id : ids) cache.put(id, new HashSet<>());
        for (Object[] row : heatEntryRepository.findDivisionNamesByEntryIds(ids)) {
            Long entryId = (Long) row[0];
            String divisionName = (String) row[1];
            cache.computeIfAbsent(entryId, k -> new HashSet<>()).add(normalizeDivision(divisionName));
        }
        return cache;
    }

    public record HistoryDto(String competitionName, Integer edition, Integer year,
                             String teamName, String region, Integer grade,
                             String divisions, String events) {}

    private HistoryDto toHistoryDto(CompetitionEntry ce) {
        return toHistoryDto(ce, heatEntryRepository.findByEntryId(ce.getId()));
    }

    /** heatEntries를 외부에서 미리 조회해 넘기는 bulk 친화 버전. */
    private HistoryDto toHistoryDto(CompetitionEntry ce, List<HeatEntry> heatEntries) {
        String compName = ce.getCompetition().getShortName() != null
                ? ce.getCompetition().getShortName() : ce.getCompetition().getName();
        String divisions = heatEntries.stream()
                .map(he -> he.getHeat().getEventRound().getEvent().getDivisionName())
                .distinct().sorted().collect(Collectors.joining(", "));
        String events = heatEntries.stream()
                .map(he -> he.getHeat().getEventRound().getEvent().getEventName())
                .distinct().sorted().collect(Collectors.joining(", "));
        Integer edition = ce.getCompetition().getEdition();
        Integer year = ce.getCompetition().getStartDate() != null ? ce.getCompetition().getStartDate().getYear() : null;
        return new HistoryDto(compName, edition, year, ce.getTeamName(), ce.getRegion(), ce.getGrade(),
                divisions.isEmpty() ? null : divisions, events.isEmpty() ? null : events);
    }

    /** CE 목록 전체에 대해 HeatEntry를 한번에 fetch join 후 HistoryDto로 변환. */
    private List<HistoryDto> toHistoryDtosBulk(List<CompetitionEntry> entries) {
        if (entries.isEmpty()) return List.of();
        List<Long> ids = entries.stream().map(CompetitionEntry::getId).toList();
        Map<Long, List<HeatEntry>> byEntry = heatEntryRepository.findByEntryIdsWithEvent(ids)
                .stream().collect(Collectors.groupingBy(he -> he.getEntry().getId()));
        return entries.stream()
                .map(ce -> toHistoryDto(ce, byEntry.getOrDefault(ce.getId(), List.of())))
                .toList();
    }

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
                    List<HistoryDto> history = toHistoryDtosBulk(historyEntries);
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

        Map<Long, Set<String>> divCache = buildDivisionCache(unmapped);

        // Group by name+gender+teamName+부별계열
        Map<String, List<CompetitionEntry>> grouped = unmapped.stream()
                .collect(Collectors.groupingBy(e -> {
                    String team = e.getTeamName() != null ? e.getTeamName() : "";
                    String divKey = divCache.getOrDefault(e.getId(), Set.of()).stream()
                            .sorted().collect(Collectors.joining(","));
                    return e.getAthleteName() + "|" + e.getGender() + "|" + team + "|" + divKey;
                }));

        for (Map.Entry<String, List<CompetitionEntry>> group : grouped.entrySet()) {
            List<CompetitionEntry> groupEntries = group.getValue();
            CompetitionEntry sample = groupEntries.getFirst();
            String sampleTeam = sample.getTeamName() != null ? sample.getTeamName() : "";
            Set<String> sampleDivs = divCache.getOrDefault(sample.getId(), Set.of());

            List<Athlete> candidates = athleteRepository.findByNameAndGender(
                    sample.getAthleteName(), sample.getGender());

            if (candidates.isEmpty()) {
                continue; // autoMatch에서는 신규 생성하지 않음 → bulkRegister에서 처리
            }

            // 같은 팀 + 호환 부별 후보 필터
            List<Athlete> teamMatched = candidates.stream()
                    .filter(a -> {
                        List<CompetitionEntry> aEntries = entryRepository.findByAthleteId(a.getId());
                        return !aEntries.isEmpty() && aEntries.stream().anyMatch(he -> {
                            boolean teamMatch = sampleTeam.equals(he.getTeamName() != null ? he.getTeamName() : "");
                            Set<String> heDivs = getDivisionCategories(he.getId());
                            boolean divMatch = sampleDivs.isEmpty() || heDivs.isEmpty()
                                    || sampleDivs.stream().anyMatch(heDivs::contains);
                            return teamMatch && divMatch;
                        });
                    })
                    .toList();

            if (teamMatched.size() == 1) {
                Athlete athlete = teamMatched.getFirst();
                for (CompetitionEntry entry : groupEntries) {
                    entry.mapAthlete(athlete);
                    matched++;
                }
            }
            // 매칭 불가 또는 동명이인 → 수동 매핑
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

    // ===== 동명이인 후보 =====

    public record DuplicateCandidateDto(Long athleteId, String name, String gender, String notes,
                                        List<HistoryDto> history) {}

    public record DuplicateGroupDto(String name, String gender, List<DuplicateCandidateDto> athletes) {}

    public List<DuplicateGroupDto> findDuplicateCandidates() {
        // 이름+성별이 같은 athlete가 2명 이상인 그룹 조회
        List<Athlete> allAthletes = athleteRepository.findAll();
        Map<String, List<Athlete>> grouped = allAthletes.stream()
                .collect(Collectors.groupingBy(a -> a.getName() + "|" + a.getGender()));

        return grouped.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(e -> {
                    List<Athlete> athletes = e.getValue();
                    String name = athletes.getFirst().getName();
                    String gender = athletes.getFirst().getGender();
                    List<DuplicateCandidateDto> candidates = athletes.stream()
                            .map(a -> {
                                List<CompetitionEntry> entries = entryRepository.findByAthleteId(a.getId());
                                List<HistoryDto> history = toHistoryDtosBulk(entries);
                                return new DuplicateCandidateDto(a.getId(), a.getName(), a.getGender(), a.getNotes(), history);
                            })
                            .toList();
                    return new DuplicateGroupDto(name, gender, candidates);
                })
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .toList();
    }

    @Transactional
    public void mergeAthletes(Long keepId, Long removeId) {
        Athlete keep = athleteRepository.findById(keepId)
                .orElseThrow(() -> new IllegalArgumentException("선수를 찾을 수 없습니다. id=" + keepId));
        Athlete remove = athleteRepository.findById(removeId)
                .orElseThrow(() -> new IllegalArgumentException("선수를 찾을 수 없습니다. id=" + removeId));

        // 삭제 대상의 모든 엔트리를 유지 대상으로 재매핑
        List<CompetitionEntry> entries = entryRepository.findByAthleteId(removeId);
        for (CompetitionEntry entry : entries) {
            entry.mapAthlete(keep);
        }
        athleteRepository.delete(remove);
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

        // 2. Athlete 등록+매핑 (이름+성별+팀명+부별계열로 그룹화)
        // 엔트리별 부별 계열 캐시
        Map<Long, Set<String>> divCache = buildDivisionCache(entries);

        // 그룹화 키: 이름|성별|팀명|부별계열(정렬된 문자열)
        Map<String, List<CompetitionEntry>> athleteGroups = entries.stream()
                .collect(Collectors.groupingBy(e -> {
                    String team = e.getTeamName() != null ? e.getTeamName() : "";
                    Set<String> divs = divCache.getOrDefault(e.getId(), Set.of());
                    String divKey = divs.stream().sorted().collect(Collectors.joining(","));
                    return e.getAthleteName() + "|" + e.getGender() + "|" + team + "|" + divKey;
                }));

        for (Map.Entry<String, List<CompetitionEntry>> group : athleteGroups.entrySet()) {
            List<CompetitionEntry> ces = group.getValue();
            CompetitionEntry sample = ces.getFirst();
            String sampleTeam = sample.getTeamName() != null ? sample.getTeamName() : "";
            Set<String> sampleDivs = divCache.getOrDefault(sample.getId(), Set.of());

            // 같은 이름+성별+팀명+부별 그룹에서 올바른 Athlete 결정
            Athlete correctAthlete = null;

            // 이미 매핑된 athlete가 있고, 팀+부별이 호환되면 사용
            for (CompetitionEntry ce : ces) {
                if (ce.getAthlete() != null) {
                    List<CompetitionEntry> existingEntries = entryRepository.findByAthleteId(ce.getAthlete().getId());
                    boolean allCompatible = existingEntries.stream().allMatch(he -> {
                        boolean teamMatch = sampleTeam.equals(he.getTeamName() != null ? he.getTeamName() : "");
                        Set<String> heDivs = divCache.containsKey(he.getId())
                                ? divCache.get(he.getId()) : getDivisionCategories(he.getId());
                        boolean divMatch = sampleDivs.isEmpty() || heDivs.isEmpty()
                                || sampleDivs.stream().anyMatch(heDivs::contains);
                        return teamMatch && divMatch;
                    });
                    if (allCompatible) {
                        correctAthlete = ce.getAthlete();
                        break;
                    }
                }
            }

            if (correctAthlete == null) {
                List<Athlete> candidates = athleteRepository.findByNameAndGender(sample.getAthleteName(), sample.getGender());

                if (candidates.isEmpty()) {
                    correctAthlete = athleteRepository.save(Athlete.builder()
                            .name(sample.getAthleteName())
                            .gender(sample.getGender())
                            .build());
                    athletesCreated++;
                } else {
                    // 같은 팀 + 호환 부별로 매핑된 후보 검색
                    List<Athlete> matched = candidates.stream()
                            .filter(a -> {
                                List<CompetitionEntry> aEntries = entryRepository.findByAthleteId(a.getId());
                                if (aEntries.isEmpty()) return false;
                                return aEntries.stream().allMatch(he -> {
                                    boolean teamMatch = sampleTeam.equals(he.getTeamName() != null ? he.getTeamName() : "");
                                    Set<String> heDivs = getDivisionCategories(he.getId());
                                    boolean divMatch = sampleDivs.isEmpty() || heDivs.isEmpty()
                                            || sampleDivs.stream().anyMatch(heDivs::contains);
                                    return teamMatch && divMatch;
                                });
                            })
                            .toList();
                    if (matched.size() == 1) {
                        correctAthlete = matched.getFirst();
                    } else {
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