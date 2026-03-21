package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByRegion(String region);

    List<Team> findAllByOrderByRegionAscNameAsc();
}
