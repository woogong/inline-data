package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.AthleteFormDto;
import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.AthleteRepository;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthleteService {

    private final AthleteRepository athleteRepository;
    private final CompetitionEntryRepository competitionEntryRepository;
    private final HeatEntryRepository heatEntryRepository;
    private final EventResultRepository eventResultRepository;

    public List<Athlete> findAll() {
        return athleteRepository.findAllByOrderByNameAsc();
    }

    public List<AthleteListItem> findAllWithLatestInfo() {
        return findWithLatestInfo(athleteRepository.findAllByOrderByNameAsc());
    }

    public List<AthleteListItem> searchWithLatestInfo(String name) {
        return findWithLatestInfo(athleteRepository.findByNameContaining(name));
    }

    private List<AthleteListItem> findWithLatestInfo(List<Athlete> athletes) {
        return athletes.stream().map(a -> {
            List<CompetitionEntry> entries = competitionEntryRepository.findByAthleteId(a.getId());
            if (entries.isEmpty()) {
                entries = competitionEntryRepository.findByAthleteNameAndGender(a.getName(), a.getGender());
            }
            if (entries.isEmpty()) return new AthleteListItem(a, null, null, null);
            CompetitionEntry latest = entries.stream()
                    .max(java.util.Comparator.comparing(
                            (CompetitionEntry ce) -> ce.getCompetition().getStartDate(),
                            java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                    .orElse(null);
            if (latest == null) return new AthleteListItem(a, null, null, null);
            String division = "";
            try {
                List<HeatEntry> heatEntries = heatEntryRepository.findByEntryId(latest.getId());
                division = heatEntries.stream()
                        .map(he -> he.getHeat().getEventRound().getEvent().getDivisionName())
                        .distinct()
                        .collect(Collectors.joining(", "));
            } catch (Exception ignored) {}
            return new AthleteListItem(a, latest.getRegion(), latest.getTeamName(),
                    division.isEmpty() ? null : division);
        }).toList();
    }

    public List<Athlete> search(String name, Integer birthYear, String notes) {
        String nameParam = (name != null && !name.isBlank()) ? name.trim() : null;
        String notesParam = (notes != null && !notes.isBlank()) ? notes.trim() : null;
        return athleteRepository.search(nameParam, birthYear, notesParam);
    }

    public record AthleteListItem(Athlete athlete, String region, String teamName, String division) {}

    public record AthleteProfileDto(String region, String teamName, String divisionsText) {}

    public record PerformanceDto(String competitionName, Long competitionId, Long eventId, Long roundId, Long heatId,
                                 String divisionName, String eventName,
                                 String round, Integer ranking, String record, String newRecord,
                                 String qualification, String note, Integer eventNumber,
                                 java.time.LocalDate competitionStartDate,
                                 String region, String teamName) {}

    public AthleteProfileDto findLatestProfile(Long athleteId) {
        Athlete athlete = findById(athleteId);
        List<CompetitionEntry> entries = competitionEntryRepository.findByAthleteId(athleteId);
        if (entries.isEmpty()) {
            entries = competitionEntryRepository.findByAthleteNameAndGender(athlete.getName(), athlete.getGender());
        }
        if (entries.isEmpty()) return new AthleteProfileDto(null, null, null);
        CompetitionEntry latest = entries.stream()
                .max(java.util.Comparator.comparing(
                        (CompetitionEntry ce) -> ce.getCompetition().getStartDate(),
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
        if (latest == null) return new AthleteProfileDto(null, null, null);
        String division = "";
        try {
            List<HeatEntry> heatEntries = heatEntryRepository.findByEntryId(latest.getId());
            division = heatEntries.stream()
                    .map(he -> he.getHeat().getEventRound().getEvent().getDivisionName())
                    .distinct().collect(Collectors.joining(", "));
        } catch (Exception ignored) {}
        return new AthleteProfileDto(latest.getRegion(), latest.getTeamName(), division.isEmpty() ? null : division);
    }

    public List<PerformanceDto> findPerformances(Long athleteId) {
        Athlete athlete = findById(athleteId);
        List<CompetitionEntry> entries = competitionEntryRepository.findByAthleteId(athleteId);
        if (entries.isEmpty()) {
            entries = competitionEntryRepository.findByAthleteNameAndGender(athlete.getName(), athlete.getGender());
        }
        List<PerformanceDto> performances = new java.util.ArrayList<>();
        for (CompetitionEntry ce : entries) {
            try {
                List<HeatEntry> heatEntries = heatEntryRepository.findByEntryId(ce.getId());
                var comp = ce.getCompetition();
                String baseName = comp.getShortName() != null ? comp.getShortName() : comp.getName();
                String compName = (comp.getEdition() != null ? "제" + comp.getEdition() + "회 " : "") + baseName;
                for (HeatEntry he : heatEntries) {
                    var round = he.getHeat().getEventRound();
                    var event = round.getEvent();
                    EventResult result = eventResultRepository.findByHeatEntryId(he.getId()).orElse(null);
                    performances.add(new PerformanceDto(
                            compName, comp.getId(), event.getId(), round.getId(), he.getHeat().getId(),
                            event.getDivisionName(), event.getEventName(),
                            round.getRound(),
                            result != null ? result.getRanking() : null,
                            result != null ? result.getRecord() : null,
                            result != null ? result.getNewRecord() : null,
                            result != null ? result.getQualification() : null,
                            result != null ? result.getNote() : null,
                            round.getEventNumber(),
                            comp.getStartDate(),
                            ce.getRegion(), ce.getTeamName()
                    ));
                }
            } catch (Exception ignored) {}
        }
        // 최근 대회 먼저, 같은 대회 내에서는 경기번호 역순
        performances.sort(java.util.Comparator
                .comparing((PerformanceDto p) -> p.competitionStartDate(),
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(p -> p.eventNumber() != null ? p.eventNumber() : 0,
                        java.util.Comparator.reverseOrder()));
        return performances;
    }

    public Athlete findById(Long id) {
        return athleteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("선수를 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public Athlete create(AthleteFormDto dto) {
        return athleteRepository.save(dto.toEntity());
    }

    @Transactional
    public Athlete update(Long id, AthleteFormDto dto) {
        Athlete athlete = findById(id);
        athlete.update(dto.getName(), dto.getGender(), dto.getBirthYear(), dto.getNotes());
        return athlete;
    }

    @Transactional
    public void delete(Long id) {
        Athlete athlete = findById(id);
        if (!competitionEntryRepository.findByAthleteId(id).isEmpty()) {
            throw new IllegalStateException("대회에 참가 이력이 있는 선수는 삭제할 수 없습니다.");
        }
        athleteRepository.delete(athlete);
    }

    @Transactional
    public void deleteForce(Long id) {
        Athlete athlete = findById(id);
        List<CompetitionEntry> entries = competitionEntryRepository.findByAthleteId(id);
        for (CompetitionEntry ce : entries) {
            List<HeatEntry> heatEntries = heatEntryRepository.findByEntryId(ce.getId());
            for (HeatEntry he : heatEntries) {
                eventResultRepository.findByHeatEntryId(he.getId())
                        .ifPresent(eventResultRepository::delete);
            }
            heatEntryRepository.deleteAll(heatEntries);
            competitionEntryRepository.delete(ce);
        }
        athleteRepository.delete(athlete);
    }
}
