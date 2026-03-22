package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.Athlete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AthleteRepository extends JpaRepository<Athlete, Long> {

    List<Athlete> findByNameContaining(String name);

    List<Athlete> findAllByOrderByNameAsc();

    List<Athlete> findByNameAndGender(String name, String gender);

    boolean existsByNameAndGenderAndNotes(String name, String gender, String notes);

    @Query("SELECT a FROM Athlete a WHERE " +
           "(:name IS NULL OR a.name LIKE %:name%) AND " +
           "(:birthYear IS NULL OR a.birthYear = :birthYear) AND " +
           "(:notes IS NULL OR a.notes LIKE %:notes%) " +
           "ORDER BY a.name")
    List<Athlete> search(@Param("name") String name,
                         @Param("birthYear") Integer birthYear,
                         @Param("notes") String notes);
}
