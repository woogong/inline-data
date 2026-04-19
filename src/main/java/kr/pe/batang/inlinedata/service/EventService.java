package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.EventFormDto;
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
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventResultHistoryRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.EventRoundRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import kr.pe.batang.inlinedata.repository.projection.EventMedalRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final EventRoundRepository eventRoundRepository;
    private final CompetitionEntryRepository competitionEntryRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;
    private final EventResultRepository eventResultRepository;
    private final EventResultHistoryRepository eventResultHistoryRepository;
    private final CompetitionService competitionService;

    // --- Event (종목) ---

    public record MedalInfo(String gold, Long goldId, String silver, Long silverId, String bronze, Long bronzeId) {}

    public record DivisionStat(String division, int male, int female, int total) {}

    public record RegionMedalStat(String region, int gold, int silver, int bronze, int total, int score,
                                    int athletes, int athletesBGroup) {}

    public record AthleteAward(String athleteName, String divisionName, String eventName, int ranking) {}

    public record TeamMedalStat(String teamName, String region, int gold, int silver, int bronze,
                                int total, String divisions, List<AthleteAward> awards) {}

    public record NewRecordInfo(String type, String athleteName, Long athleteId, String region, String teamName,
                                String divisionName, String eventName, String round,
                                String record, Integer ranking) {}

    public record ParticipantStats(int totalMale, int totalFemale, int totalAll,
                                   int totalEvents, int totalRegions,
                                   List<DivisionStat> divisionStats) {}

    public record AthleteProfileInfo(String name, String region, String teamName, Long athleteId,
                                     List<AthleteMedalRecord> medals) {}

    public record AthleteMedalRecord(String competitionName, String divisionName, String eventName,
                                     int ranking, String record) {}

    public record CompetitionMedalCount(int edition, String shortName, int gold, int silver, int bronze) {}
    public record TeamMedalCount(String teamName, int medals) {}
    public record AthleteMedalCount(String name, int gold, int silver, int bronze, int total) {}
    public record AllTimeRegionMedalStat(
        String region, int gold, int silver, int bronze, int total,
        List<CompetitionMedalCount> byCompetition,
        List<TeamMedalCount> topTeams,
        List<AthleteMedalCount> topAthletes
    ) {}
    public record AllTimeSummary(int totalCompetitions, int totalEvents, int totalMedals, int totalRegions) {}

    public List<Event> findByCompetitionId(Long competitionId) {
        return eventRepository.findByCompetitionIdOrderByFirstEventNumber(competitionId);
    }

    public Map<Long, MedalInfo> findMedalsByCompetitionId(Long competitionId) {
        // 프로젝션으로 필요한 컬럼만 단일 쿼리. 엔티티 hydration / lazy load 없음.
        List<EventMedalRow> rows = eventResultRepository.findMedalRowsByCompetitionId(competitionId);

        // eventId → {1: name, 2: name, 3: name}, {1: id, 2: id, 3: id}
        Map<Long, String[]> nameByEvent = new LinkedHashMap<>();
        Map<Long, Long[]> idByEvent = new LinkedHashMap<>();
        for (EventMedalRow row : rows) {
            int idx = row.ranking() - 1;            // 1,2,3 → 0,1,2
            if (idx < 0 || idx > 2) continue;
            nameByEvent.computeIfAbsent(row.eventId(), k -> new String[3])[idx] = row.athleteName();
            idByEvent.computeIfAbsent(row.eventId(), k -> new Long[3])[idx] = row.athleteId();
        }

        Map<Long, MedalInfo> medals = new LinkedHashMap<>();
        for (Map.Entry<Long, String[]> e : nameByEvent.entrySet()) {
            String[] n = e.getValue();
            Long[] a = idByEvent.get(e.getKey());
            medals.put(e.getKey(), new MedalInfo(n[0], a[0], n[1], a[1], n[2], a[2]));
        }
        return medals;
    }

    public ParticipantStats findParticipantStats(Long competitionId) {
        // 개인전 종목에 출전한 CompetitionEntry 조회 (중복 제거)
        List<CompetitionEntry> entries = competitionEntryRepository
                .findIndividualEntriesWithAthlete(competitionId);

        int totalMale = 0, totalFemale = 0;
        // divisionBase(부별 공통명) → {male, female}
        Map<String, int[]> divMap = new LinkedHashMap<>();

        for (CompetitionEntry ce : entries) {
            // 해당 엔트리가 참가한 종목의 division_name을 가져오기 위해 HeatEntry → Event 추적
            String gender = ce.getGender();
            boolean isMale = "M".equals(gender);
            if (isMale) totalMale++; else totalFemale++;
        }

        // 부별 통계: divisionName에서 성별 제거하여 그룹화
        // "여초부 5,6학년" → "초부 5,6학년", "남중부" → "중부"
        List<Event> events = findByCompetitionId(competitionId);
        Map<Long, Event> eventById = events.stream().collect(Collectors.toMap(Event::getId, e -> e));

        // entry → division 매핑 (HeatEntry를 통해)
        Map<Long, Set<String>> entryDivisions = new LinkedHashMap<>();
        for (Event event : events) {
            if (event.isTeamEvent()) continue;
            List<EventRound> rounds = eventRoundRepository.findByEventIdOrderByEventNumberAsc(event.getId());
            for (EventRound round : rounds) {
                List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(round.getId());
                if (heats.isEmpty()) continue;
                List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
                List<HeatEntry> heatEntries = heatEntryRepository.findByHeatIdsWithDetails(heatIds);
                String divBase = normalizeDivision(event.getDivisionName());
                for (HeatEntry he : heatEntries) {
                    entryDivisions.computeIfAbsent(he.getEntry().getId(), k -> new LinkedHashSet<>()).add(divBase);
                }
                break; // 첫 번째 라운드만 확인 (중복 방지)
            }
        }

        for (CompetitionEntry ce : entries) {
            Set<String> divs = entryDivisions.getOrDefault(ce.getId(), Set.of());
            boolean isMale = "M".equals(ce.getGender());
            for (String div : divs) {
                int[] counts = divMap.computeIfAbsent(div, k -> new int[2]);
                if (isMale) counts[0]++; else counts[1]++;
            }
        }

        List<DivisionStat> divisionStats = divMap.entrySet().stream()
                .map(e -> new DivisionStat(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[0] + e.getValue()[1]))
                .sorted(Comparator.comparingInt(d -> divisionOrder(d.division())))
                .toList();

        int totalEvents = findByCompetitionId(competitionId).size();
        long totalRegions = entries.stream()
                .map(CompetitionEntry::getRegion)
                .filter(r -> r != null && !r.isBlank())
                .distinct()
                .count();

        return new ParticipantStats(totalMale, totalFemale, totalMale + totalFemale,
                totalEvents, (int) totalRegions, divisionStats);
    }

    /**
     * DTT 종목의 라운드 순위를 조별이 아닌 전체 기록 순으로 재계산한다.
     */
    @Transactional
    public int recalculateDttRankings(Long eventRoundId) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(eventRoundId);
        if (heats.isEmpty()) return 0;

        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        List<EventResult> allResults = eventResultRepository.findByHeatIdsWithDetails(heatIds);
        if (allResults.isEmpty()) return 0;

        // 기록 순 정렬 (기록이 없으면 맨 뒤)
        allResults.sort(Comparator.comparing(
                (EventResult er) -> er.getRecord() != null ? er.getRecord() : "zzz"));

        // 순위 재할당 (내부 재계산: source 건드리지 않음)
        for (int i = 0; i < allResults.size(); i++) {
            EventResult er = allResults.get(i);
            Integer newRanking = er.getRecord() != null ? i + 1 : null;
            er.updateResult(newRanking, er.getRecord(), er.getNewRecord(),
                    er.getQualification(), er.getNote(), null);
        }
        return allResults.size();
    }

    /**
     * 대회의 모든 DTT 종목 라운드 순위를 재계산한다.
     */
    @Transactional
    public int recalculateAllDttRankings(Long competitionId) {
        List<Event> events = findByCompetitionId(competitionId);
        int total = 0;
        for (Event event : events) {
            if (!event.getEventName().contains("DTT")) continue;
            List<EventRound> rounds = eventRoundRepository.findByEventIdOrderByEventNumberAsc(event.getId());
            for (EventRound round : rounds) {
                total += recalculateDttRankings(round.getId());
            }
        }
        return total;
    }

    public List<NewRecordInfo> findNewRecords(Long competitionId) {
        return eventResultRepository.findNewRecordsByCompetitionId(competitionId).stream()
                .map(er -> {
                    var ce = er.getHeatEntry().getEntry();
                    var heat = er.getHeatEntry().getHeat();
                    var round = heat.getEventRound();
                    var event = round.getEvent();
                    Long athleteId = ce.getAthlete() != null ? ce.getAthlete().getId() : null;
                    return new NewRecordInfo(
                            er.getNewRecord(), ce.getAthleteName(), athleteId, ce.getRegion(), ce.getTeamName(),
                            event.getDivisionName(), event.getEventName(), round.getRound(),
                            er.getRecord(), er.getRanking());
                })
                .sorted(Comparator.comparingInt((NewRecordInfo r) -> recordTypeOrder(r.type()))
                        .thenComparingInt(r -> divisionOrder(normalizeDivision(r.divisionName())))
                        .thenComparingInt(r -> genderOrder(r.divisionName()))
                        .thenComparingInt(r -> extractDistance(r.eventName()))
                        .thenComparing(r -> r.record() != null ? r.record() : "zzz"))
                .toList();
    }

    public List<RegionMedalStat> findRegionMedalStats(Long competitionId) {
        List<EventResult> results = eventResultRepository.findScoringResultsByCompetitionId(competitionId);

        Map<String, int[]> regionData = new LinkedHashMap<>(); // [gold, silver, bronze, score]

        for (EventResult er : results) {
            var ce = er.getHeatEntry().getEntry();
            String region = ce.getRegion();
            if (region == null || region.isBlank()) continue;

            String divName = er.getHeatEntry().getHeat().getEventRound().getEvent().getDivisionName();
            boolean isBGroup = divName.contains("일반(B조)");

            int[] data = regionData.computeIfAbsent(region, k -> new int[4]);
            int ranking = er.getRanking();

            if (ranking == 1) data[0]++;
            else if (ranking == 2) data[1]++;
            else if (ranking == 3) data[2]++;

            if (!isBGroup) {
                int score = switch (ranking) {
                    case 1 -> 7; case 2 -> 5; case 3 -> 4;
                    case 4 -> 3; case 5 -> 2; case 6 -> 1;
                    default -> 0;
                };
                data[3] += score;
            }
        }

        // 지역별 참가 선수 수 집계 (개인전만) — 단일 쿼리로 N+1 제거
        Map<String, int[]> regionAthletes = new LinkedHashMap<>(); // [일반, B조]
        for (Object[] row : competitionEntryRepository.findRegionEntryBGroupInfo(competitionId)) {
            String region = (String) row[0];
            int isB = ((Number) row[2]).intValue();
            int[] counts = regionAthletes.computeIfAbsent(region, k -> new int[2]);
            if (isB == 1) counts[1]++;
            else counts[0]++;
        }

        // 모든 지역 통합 (메달 없지만 선수가 있는 지역 포함)
        Set<String> allRegions = new LinkedHashSet<>();
        allRegions.addAll(regionData.keySet());
        allRegions.addAll(regionAthletes.keySet());

        return allRegions.stream()
                .map(region -> {
                    int[] d = regionData.getOrDefault(region, new int[4]);
                    int[] a = regionAthletes.getOrDefault(region, new int[2]);
                    return new RegionMedalStat(region, d[0], d[1], d[2], d[0] + d[1] + d[2], d[3], a[0], a[1]);
                })
                .sorted(Comparator.comparingInt((RegionMedalStat r) -> -r.score()))
                .toList();
    }

    public List<TeamMedalStat> findTeamMedalStats(Long competitionId) {
        List<EventResult> medalResults = eventResultRepository.findMedalResultsByCompetitionId(competitionId);

        Map<String, int[]> medalCounts = new LinkedHashMap<>();
        Map<String, Set<String>> teamDivisions = new LinkedHashMap<>();
        Map<String, String> teamRegions = new LinkedHashMap<>();
        Map<String, List<AthleteAward>> teamAwards = new LinkedHashMap<>();

        for (EventResult er : medalResults) {
            var ce = er.getHeatEntry().getEntry();
            String teamName = ce.getTeamName();
            String region = ce.getRegion();
            if (teamName == null || teamName.isBlank()) continue;

            String key = teamName + "|" + (region != null ? region : "");
            teamRegions.putIfAbsent(key, region);
            int[] counts = medalCounts.computeIfAbsent(key, k -> new int[3]);
            if (er.getRanking() == 1) counts[0]++;
            else if (er.getRanking() == 2) counts[1]++;
            else if (er.getRanking() == 3) counts[2]++;

            var event = er.getHeatEntry().getHeat().getEventRound().getEvent();
            teamDivisions.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(event.getDivisionName());
            teamAwards.computeIfAbsent(key, k -> new java.util.ArrayList<>())
                    .add(new AthleteAward(ce.getAthleteName(), event.getDivisionName(),
                            event.getEventName(), er.getRanking()));
        }

        return medalCounts.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\|", 2);
                    int[] c = e.getValue();
                    String divs = String.join(", ", teamDivisions.getOrDefault(e.getKey(), Set.of()));
                    List<AthleteAward> awards = teamAwards.getOrDefault(e.getKey(), List.of());
                    awards.sort(Comparator.comparingInt(AthleteAward::ranking));
                    return new TeamMedalStat(parts[0], teamRegions.get(e.getKey()),
                            c[0], c[1], c[2], c[0] + c[1] + c[2], divs, awards);
                })
                .sorted(Comparator.comparingInt((TeamMedalStat t) -> -t.total())
                        .thenComparingInt(t -> -t.gold())
                        .thenComparingInt(t -> -t.silver()))
                .toList();
    }

    public List<AllTimeRegionMedalStat> findAllTimeRegionMedalStats() {
        List<EventResult> allMedals = eventResultRepository.findAllMedalResults();

        // region -> gold/silver/bronze counts
        Map<String, int[]> regionMedals = new LinkedHashMap<>();
        // region -> (edition -> gold/silver/bronze)
        Map<String, Map<Integer, int[]>> regionByComp = new LinkedHashMap<>();
        // region -> (teamName -> medal count)
        Map<String, Map<String, Integer>> regionTeams = new LinkedHashMap<>();
        // region -> (athleteName -> gold/silver/bronze)
        Map<String, Map<String, int[]>> regionAthletes = new LinkedHashMap<>();
        // edition -> shortName mapping
        Map<Integer, String> editionNames = new LinkedHashMap<>();

        for (EventResult er : allMedals) {
            var ce = er.getHeatEntry().getEntry();
            var comp = ce.getCompetition();
            String region = ce.getRegion();
            if (region == null || region.isBlank()) continue;

            int ranking = er.getRanking();
            int edition = comp.getEdition() != null ? comp.getEdition() : 0;
            editionNames.putIfAbsent(edition, comp.getShortName());

            // Total medals
            int[] counts = regionMedals.computeIfAbsent(region, k -> new int[3]);
            if (ranking == 1) counts[0]++;
            else if (ranking == 2) counts[1]++;
            else if (ranking == 3) counts[2]++;

            // By competition
            int[] compCounts = regionByComp
                .computeIfAbsent(region, k -> new LinkedHashMap<>())
                .computeIfAbsent(edition, k -> new int[3]);
            if (ranking == 1) compCounts[0]++;
            else if (ranking == 2) compCounts[1]++;
            else if (ranking == 3) compCounts[2]++;

            // By team
            String teamName = ce.getTeamName();
            if (teamName != null && !teamName.isBlank()) {
                regionTeams.computeIfAbsent(region, k -> new LinkedHashMap<>())
                    .merge(teamName, 1, Integer::sum);
            }

            // By athlete
            String athleteName = ce.getAthleteName();
            if (athleteName != null) {
                int[] aCounts = regionAthletes
                    .computeIfAbsent(region, k -> new LinkedHashMap<>())
                    .computeIfAbsent(athleteName, k -> new int[3]);
                if (ranking == 1) aCounts[0]++;
                else if (ranking == 2) aCounts[1]++;
                else if (ranking == 3) aCounts[2]++;
            }
        }

        return regionMedals.entrySet().stream()
            .map(e -> {
                String region = e.getKey();
                int[] c = e.getValue();
                int total = c[0] + c[1] + c[2];

                // byCompetition - sorted by edition
                List<CompetitionMedalCount> byComp = regionByComp.getOrDefault(region, Map.of())
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(ec -> new CompetitionMedalCount(ec.getKey(),
                        editionNames.getOrDefault(ec.getKey(), ""),
                        ec.getValue()[0], ec.getValue()[1], ec.getValue()[2]))
                    .toList();

                // topTeams - sorted by medal count desc, limit 5
                List<TeamMedalCount> topTeams = regionTeams.getOrDefault(region, Map.of())
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(et -> new TeamMedalCount(et.getKey(), et.getValue()))
                    .toList();

                // topAthletes - sorted by total desc, limit 5
                List<AthleteMedalCount> topAthletes = regionAthletes.getOrDefault(region, Map.of())
                    .entrySet().stream()
                    .map(ea -> new AthleteMedalCount(ea.getKey(), ea.getValue()[0], ea.getValue()[1], ea.getValue()[2],
                        ea.getValue()[0] + ea.getValue()[1] + ea.getValue()[2]))
                    .sorted(Comparator.comparingInt(AthleteMedalCount::total).reversed()
                        .thenComparingInt(AthleteMedalCount::gold).reversed())
                    .limit(5)
                    .toList();

                return new AllTimeRegionMedalStat(region, c[0], c[1], c[2], total, byComp, topTeams, topAthletes);
            })
            .sorted(Comparator.comparingInt((AllTimeRegionMedalStat r) -> r.total()).reversed()
                .thenComparingInt(r -> r.gold()).reversed()
                .thenComparingInt(r -> r.silver()).reversed())
            .toList();
    }

    public AllTimeSummary findAllTimeSummary() {
        List<EventResult> allMedals = eventResultRepository.findAllMedalResults();
        Set<Long> compIds = new HashSet<>();
        Set<Long> eventIds = new HashSet<>();
        Set<String> regions = new HashSet<>();
        for (EventResult er : allMedals) {
            compIds.add(er.getHeatEntry().getEntry().getCompetition().getId());
            eventIds.add(er.getHeatEntry().getHeat().getEventRound().getEvent().getId());
            String region = er.getHeatEntry().getEntry().getRegion();
            if (region != null && !region.isBlank()) regions.add(region);
        }
        return new AllTimeSummary(compIds.size(), eventIds.size(), allMedals.size(), regions.size());
    }

    private static int recordTypeOrder(String type) {
        return switch (type) {
            case "세계신" -> 0;
            case "한국신" -> 1;
            case "부별신" -> 2;
            case "대회신" -> 3;
            default -> 9;
        };
    }

    private static int genderOrder(String divisionName) {
        if (divisionName.startsWith("여")) return 0;
        if (divisionName.startsWith("남")) return 1;
        return 2; // 혼성
    }

    private static int extractDistance(String eventName) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d[\\d,]*)m").matcher(eventName);
        if (m.find()) return Integer.parseInt(m.group(1).replace(",", ""));
        return 99999;
    }

    private static String normalizeDivision(String divisionName) {
        // "여초부 5,6학년" → "초부 5,6학년", "남자18세이하부" → "18세이하부"
        String base = divisionName.replaceFirst("^(여자?|남자?)", "");
        return base.replaceFirst("^초부", "초등부")
                   .replaceFirst("^중부", "중등부")
                   .replaceFirst("^고부", "고등부")
                   .replaceFirst("^대부", "대학부")
                   .replaceFirst("^일부", "일반부");
    }

    private static int divisionOrder(String division) {
        if (division.contains("일반(B조)")) {
            if (division.contains("1,2학년")) return 14;
            if (division.contains("3,4학년")) return 15;
            if (division.contains("5,6학년")) return 16;
            return 17;
        }
        if (division.startsWith("초등부")) {
            if (division.contains("1,2학년")) return 10;
            if (division.contains("3,4학년")) return 11;
            if (division.contains("5,6학년")) return 12;
            return 13;
        }
        if (division.startsWith("중등부")) return 20;
        if (division.startsWith("고등부")) return 30;
        if (division.startsWith("대학부")) return 40;
        if (division.startsWith("일반부")) return 50;
        return 99;
    }

    public Event findById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("종목을 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public Event create(Long competitionId, EventFormDto dto) {
        Competition competition = competitionService.findById(competitionId);
        return eventRepository.save(dto.toEntity(competition));
    }

    @Transactional
    public Event update(Long id, EventFormDto dto) {
        Event event = findById(id);
        event.update(dto.getDivisionName(), dto.getGender(), dto.getEventName(), dto.isTeamEvent());
        return event;
    }

    @Transactional
    public void delete(Long id) {
        Event event = findById(id);
        List<EventRound> rounds = eventRoundRepository.findByEventIdOrderByEventNumberAsc(id);
        for (EventRound round : rounds) {
            List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(round.getId());
            for (EventHeat heat : heats) {
                List<HeatEntry> heatEntries = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(heat.getId());
                for (HeatEntry he : heatEntries) {
                    eventResultRepository.findByHeatEntryId(he.getId()).ifPresent(eventResultRepository::delete);
                }
                heatEntryRepository.deleteAll(heatEntries);
            }
            eventHeatRepository.deleteAll(heats);
        }
        eventRoundRepository.deleteAll(rounds);
        eventRepository.delete(event);
    }

    // --- EventRound (경기/라운드) ---

    public record RoundWithStatus(EventRound round, String status, int entryCount, int resultCount) {}

    public List<EventRound> findRoundsByEventId(Long eventId) {
        return eventRoundRepository.findByEventIdOrderByEventNumberAsc(eventId);
    }

    public List<RoundWithStatus> findRoundsWithStatus(Long eventId) {
        List<EventRound> rounds = eventRoundRepository.findByEventIdOrderByEventNumberAsc(eventId);
        // 모든 라운드의 heat을 한번에 조회
        List<EventHeat> allHeats = new java.util.ArrayList<>();
        for (EventRound r : rounds) {
            allHeats.addAll(eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(r.getId()));
        }
        Map<Long, List<EventHeat>> heatsByRound = allHeats.stream()
                .collect(Collectors.groupingBy(h -> h.getEventRound().getId()));

        return rounds.stream().map(r -> {
            List<EventHeat> heats = heatsByRound.getOrDefault(r.getId(), List.of());
            int entryCount = 0, resultCount = 0;
            if (!heats.isEmpty()) {
                List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
                entryCount = (int) heatEntryRepository.countByHeatIdIn(heatIds);
                resultCount = (int) eventResultRepository.countByHeatIds(heatIds);
            }
            String status;
            if (resultCount > 0 && resultCount >= entryCount) status = "완료";
            else if (resultCount > 0) status = "일부";
            else if (entryCount > 0) status = "엔트리만";
            else status = "없음";
            return new RoundWithStatus(r, status, entryCount, resultCount);
        }).toList();
    }

    public EventRound findRoundById(Long id) {
        return eventRoundRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("경기를 찾을 수 없습니다. id=" + id));
    }

    // --- EventHeat + HeatEntry (조/출전) ---

    public Map<EventHeat, List<HeatEntry>> findHeatsWithEntries(Long eventRoundId) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(eventRoundId);
        if (heats.isEmpty()) return Map.of();
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        List<HeatEntry> entries = heatEntryRepository.findByHeatIdsWithDetails(heatIds);

        // 결과가 있는 heatEntry ID를 수집
        List<EventResult> allResults = eventResultRepository.findByHeatIdsWithDetails(heatIds);
        Map<Long, EventResult> resultByEntryId = allResults.stream()
                .collect(Collectors.toMap(er -> er.getHeatEntry().getId(), er -> er, (a, b) -> a));
        Set<Long> heatsWithResults = allResults.stream()
                .map(er -> er.getHeatEntry().getHeat().getId())
                .collect(Collectors.toSet());

        // 결과가 있는 조는 순위 순, 없는 조는 배번 순
        Map<Long, List<HeatEntry>> entriesByHeatId = entries.stream()
                .collect(Collectors.groupingBy(he -> he.getHeat().getId()));
        for (Map.Entry<Long, List<HeatEntry>> e : entriesByHeatId.entrySet()) {
            Long heatId = e.getKey();
            if (heatsWithResults.contains(heatId)) {
                e.getValue().sort(Comparator.comparingInt(he -> {
                    EventResult r = resultByEntryId.get(he.getId());
                    return r != null && r.getRanking() != null ? r.getRanking() : Integer.MAX_VALUE;
                }));
            }
        }
        Map<EventHeat, List<HeatEntry>> result = new LinkedHashMap<>();
        for (EventHeat heat : heats) {
            result.put(heat, entriesByHeatId.getOrDefault(heat.getId(), List.of()));
        }
        return result;
    }

    public Map<Long, EventResult> findResultsByEntryId(Long eventRoundId) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(eventRoundId);
        if (heats.isEmpty()) return Map.of();
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        List<EventResult> results = eventResultRepository.findByHeatIdsWithDetails(heatIds);
        return results.stream()
                .collect(Collectors.toMap(er -> er.getHeatEntry().getId(), er -> er));
    }

    public Map<EventHeat, List<EventResult>> findHeatsWithResults(Long eventRoundId) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(eventRoundId);
        if (heats.isEmpty()) return Map.of();
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        List<EventResult> results = eventResultRepository.findByHeatIdsWithDetails(heatIds);
        Map<Long, List<EventResult>> resultsByHeatId = results.stream()
                .collect(Collectors.groupingBy(er -> er.getHeatEntry().getHeat().getId()));
        Map<EventHeat, List<EventResult>> result = new LinkedHashMap<>();
        for (EventHeat heat : heats) {
            result.put(heat, resultsByHeatId.getOrDefault(heat.getId(), List.of()));
        }
        return result;
    }

    public boolean hasResults(Long eventRoundId) {
        List<EventHeat> heats = eventHeatRepository.findByEventRoundIdOrderByHeatNumberAsc(eventRoundId);
        if (heats.isEmpty()) return false;
        List<Long> heatIds = heats.stream().map(EventHeat::getId).toList();
        return !eventResultRepository.findByHeatIdsWithDetails(heatIds).isEmpty();
    }

    @Transactional
    public void saveResult(Long heatEntryId, Integer ranking, String record,
                           String newRecord, String qualification, String note) {
        HeatEntry heatEntry = heatEntryRepository.findById(heatEntryId)
                .orElseThrow(() -> new IllegalArgumentException("출전 엔트리를 찾을 수 없습니다. id=" + heatEntryId));
        Optional<EventResult> existing = eventResultRepository.findByHeatEntryId(heatEntryId);
        EventResult er;
        if (existing.isPresent()) {
            // 관리자 직접 수정은 최상위 우선순위 — 어떤 소스의 기록이든 덮어쓴다.
            er = existing.get();
            er.updateResult(ranking, record, newRecord, qualification, note, ResultSource.MANUAL);
        } else {
            er = eventResultRepository.save(EventResult.builder()
                    .heatEntry(heatEntry)
                    .ranking(ranking)
                    .record(record)
                    .newRecord(newRecord)
                    .qualification(qualification)
                    .note(note)
                    .source(ResultSource.MANUAL)
                    .build());
        }
        eventResultHistoryRepository.save(EventResultHistory.builder()
                .eventResultId(er.getId())
                .heatEntryId(heatEntry.getId())
                .source(ResultSource.MANUAL)
                .ranking(ranking).record(record).newRecord(newRecord)
                .qualification(qualification).note(note)
                .build());
    }

    public AthleteProfileInfo findAthleteProfile(Long competitionId, String athleteName) {
        // 해당 대회의 CompetitionEntry에서 소속 정보 조회
        List<CompetitionEntry> entries = competitionEntryRepository.findByCompetitionId(competitionId)
                .stream().filter(ce -> athleteName.equals(ce.getAthleteName())).toList();

        String region = null, teamName = null;
        Long athleteId = null;
        if (!entries.isEmpty()) {
            CompetitionEntry entry = entries.getFirst();
            region = entry.getRegion();
            teamName = entry.getTeamName();
            athleteId = entry.getAthlete() != null ? entry.getAthlete().getId() : null;
        }

        // 최근 메달 이력 조회 (결승 1~3위, 최근 5개 대회)
        List<EventResult> allMedals = eventResultRepository.findMedalsByAthleteName(athleteName);

        Set<Long> seenCompIds = new LinkedHashSet<>();
        List<AthleteMedalRecord> medalRecords = new java.util.ArrayList<>();
        for (EventResult er : allMedals) {
            var comp = er.getHeatEntry().getEntry().getCompetition();
            seenCompIds.add(comp.getId());
            if (seenCompIds.size() > 5) break;

            var round = er.getHeatEntry().getHeat().getEventRound();
            var event = round.getEvent();
            String baseName = comp.getShortName() != null ? comp.getShortName() : comp.getName();
            String compName = (comp.getEdition() != null ? "제" + comp.getEdition() + "회 " : "") + baseName;
            medalRecords.add(new AthleteMedalRecord(compName, event.getDivisionName(),
                    event.getEventName(), er.getRanking(), er.getRecord()));
        }

        return new AthleteProfileInfo(athleteName, region, teamName, athleteId, medalRecords);
    }
}
