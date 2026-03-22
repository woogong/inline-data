package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByRegion(String region);

    List<Team> findAllByOrderByRegionAscNameAsc();

    Optional<Team> findByNameAndRegion(String name, String region);
}
