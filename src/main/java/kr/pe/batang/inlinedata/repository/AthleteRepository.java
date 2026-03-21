package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.Athlete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AthleteRepository extends JpaRepository<Athlete, Long> {

    List<Athlete> findByNameContaining(String name);

    List<Athlete> findAllByOrderByNameAsc();
}
