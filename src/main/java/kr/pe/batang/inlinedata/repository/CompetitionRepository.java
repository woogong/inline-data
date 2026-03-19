package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
}
