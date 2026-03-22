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

import java.util.List;

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

    public List<Athlete> search(String name, Integer birthYear, String notes) {
        String nameParam = (name != null && !name.isBlank()) ? name.trim() : null;
        String notesParam = (notes != null && !notes.isBlank()) ? notes.trim() : null;
        return athleteRepository.search(nameParam, birthYear, notesParam);
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
