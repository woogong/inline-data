package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.TeamFormDto;
import kr.pe.batang.inlinedata.entity.Team;
import kr.pe.batang.inlinedata.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;

    public List<Team> findAll() {
        return teamRepository.findAllByOrderByRegionAscNameAsc();
    }

    public List<Team> findByRegion(String region) {
        return teamRepository.findByRegion(region);
    }

    public Team findById(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("소속을 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public Team create(TeamFormDto dto) {
        return teamRepository.save(dto.toEntity());
    }

    @Transactional
    public Team update(Long id, TeamFormDto dto) {
        Team team = findById(id);
        team.update(dto.getName(), dto.getRegion());
        return team;
    }

    @Transactional
    public void delete(Long id) {
        Team team = findById(id);
        teamRepository.delete(team);
    }
}
