package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.CompetitionFormDto;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.repository.CompetitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetitionService {

    private final CompetitionRepository competitionRepository;

    public List<Competition> findAll() {
        return competitionRepository.findAll();
    }

    public Competition findById(Long id) {
        return competitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("대회를 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public Competition create(CompetitionFormDto dto) {
        return competitionRepository.save(dto.toEntity());
    }

    @Transactional
    public Competition update(Long id, CompetitionFormDto dto) {
        Competition competition = findById(id);
        competition.update(
                dto.getName(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getDurationDays(),
                dto.getVenue(),
                dto.getVenueDetail(),
                dto.getHost(),
                dto.getOrganizer(),
                dto.getNotes()
        );
        return competition;
    }

    @Transactional
    public void delete(Long id) {
        Competition competition = findById(id);
        competitionRepository.delete(competition);
    }
}
