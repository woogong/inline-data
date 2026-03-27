package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.AthleteFormDto;
import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.repository.AthleteRepository;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        List<Athlete> athletes = athleteRepository.findAllByOrderByNameAsc();
        return athletes.stream().map(a -> {
            List<CompetitionEntry> entries = competitionEntryRepository.findByAthleteId(a.getId());
            if (entries.isEmpty()) return new AthleteListItem(a, null, null, null);
            // 최신 엔트리 (ID가 가장 큰 것 = 가장 나중에 등록된 것)
            CompetitionEntry latest = entries.stream()
                    .max(java.util.Comparator.comparingLong(CompetitionEntry::getId))
                    .orElse(null);
            if (latest == null) return new AthleteListItem(a, null, null, null);
            // 부별 정보
            List<HeatEntry> heatEntries = heatEntryRepository.findByEntryId(latest.getId());
            String division = heatEntries.stream()
                    .map(he -> he.getHeat().getEventRound().getEvent().getDivisionName())
                    .distinct()
                    .collect(Collectors.joining(", "));
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

    public record CompetitionHistoryDto(String competitionName, String teamName, String region,
                                           Integer grade, Set<String> divisions) {}

    public List<CompetitionHistoryDto> findCompetitionHistory(Long athleteId) {
        List<CompetitionEntry> entries = competitionEntryRepository.findByAthleteId(athleteId);
        return entries.stream().map(ce -> {
            // HeatEntry를 통해 출전 종목의 부별 수집
            List<HeatEntry> heatEntries = heatEntryRepository.findByEntryId(ce.getId());
            Set<String> divisions = heatEntries.stream()
                    .map(he -> he.getHeat().getEventRound().getEvent().getDivisionName())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new CompetitionHistoryDto(
                    ce.getCompetition().getShortName() != null ? ce.getCompetition().getShortName() : ce.getCompetition().getName(),
                    ce.getTeamName(), ce.getRegion(), ce.getGrade(), divisions);
        }).toList();
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
