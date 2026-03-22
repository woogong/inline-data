package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.entity.Team;
import kr.pe.batang.inlinedata.repository.AthleteRepository;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import kr.pe.batang.inlinedata.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntryService {

    private final AthleteRepository athleteRepository;
    private final TeamRepository teamRepository;
    private final CompetitionEntryRepository competitionEntryRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;

    @Transactional
    public Long addHeat(Event event, int heatNumber) {
        // 이미 존재하면 기존 것 반환
        List<EventHeat> heats = eventHeatRepository.findByEventIdOrderByHeatNumberAsc(event.getId());
        for (EventHeat h : heats) {
            if (h.getHeatNumber() == heatNumber) {
                return h.getId();
            }
        }
        EventHeat heat = eventHeatRepository.save(EventHeat.builder()
                .event(event)
                .heatNumber(heatNumber)
                .build());
        return heat.getId();
    }

    @Transactional
    public void deleteHeat(Long heatId) {
        // 조에 속한 엔트리도 함께 삭제
        List<HeatEntry> entries = heatEntryRepository.findByHeatIdOrderByBibNumberAsc(heatId);
        heatEntryRepository.deleteAll(entries);
        eventHeatRepository.deleteById(heatId);
    }

    @Transactional
    public HeatEntry saveEntry(Event event, Long heatId, int bibNumber, String athleteName,
                               String gender, String region, String teamName) {
        EventHeat heat = eventHeatRepository.findById(heatId)
                .orElseThrow(() -> new IllegalArgumentException("조를 찾을 수 없습니다. id=" + heatId));

        Team team = null;
        if (teamName != null && !teamName.isBlank() && region != null && !region.isBlank()) {
            team = findOrCreateTeam(teamName.trim(), region.trim());
        }

        Athlete athlete = findOrCreateAthlete(athleteName.trim(), gender);

        Competition competition = event.getCompetition();
        CompetitionEntry compEntry = findOrCreateCompetitionEntry(competition, athlete, team);

        // 같은 조에 같은 선수가 이미 있는지 확인
        Optional<HeatEntry> existing = heatEntryRepository
                .findByHeatIdOrderByBibNumberAsc(heat.getId()).stream()
                .filter(he -> he.getEntry().getId().equals(compEntry.getId()))
                .findFirst();

        if (existing.isPresent()) {
            return existing.get();
        }

        return heatEntryRepository.save(HeatEntry.builder()
                .heat(heat)
                .entry(compEntry)
                .bibNumber(bibNumber)
                .build());
    }

    @Transactional
    public void deleteEntry(Long heatEntryId) {
        heatEntryRepository.deleteById(heatEntryId);
    }

    private Team findOrCreateTeam(String name, String region) {
        return teamRepository.findByNameAndRegion(name, region)
                .orElseGet(() -> teamRepository.save(Team.builder()
                        .name(name)
                        .region(region)
                        .build()));
    }

    private Athlete findOrCreateAthlete(String name, String gender) {
        List<Athlete> candidates = athleteRepository.findByNameAndGender(name, gender);
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        return athleteRepository.save(Athlete.builder()
                .name(name)
                .gender(gender)
                .build());
    }

    private CompetitionEntry findOrCreateCompetitionEntry(Competition competition,
                                                          Athlete athlete, Team team) {
        Optional<CompetitionEntry> existing = competitionEntryRepository
                .findByCompetitionIdAndAthleteId(competition.getId(), athlete.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        return competitionEntryRepository.save(CompetitionEntry.builder()
                .competition(competition)
                .athlete(athlete)
                .team(team)
                .build());
    }
}
