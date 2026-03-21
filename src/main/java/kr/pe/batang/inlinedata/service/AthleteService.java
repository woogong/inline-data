package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.AthleteFormDto;
import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.repository.AthleteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthleteService {

    private final AthleteRepository athleteRepository;

    public List<Athlete> findAll() {
        return athleteRepository.findAllByOrderByNameAsc();
    }

    public List<Athlete> searchByName(String name) {
        return athleteRepository.findByNameContaining(name);
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
        athlete.update(dto.getName(), dto.getGender());
        return athlete;
    }

    @Transactional
    public void delete(Long id) {
        Athlete athlete = findById(id);
        athleteRepository.delete(athlete);
    }
}
