package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.EventResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventResultRepository extends JpaRepository<EventResult, Long> {

    Optional<EventResult> findByHeatEntryId(Long heatEntryId);

    @Query("SELECT er FROM EventResult er " +
           "JOIN FETCH er.heatEntry he " +
           "JOIN FETCH he.entry ce " +
           "LEFT JOIN FETCH ce.athlete " +
           "LEFT JOIN FETCH ce.team " +
           "WHERE he.heat.id IN :heatIds " +
           "ORDER BY he.heat.id, er.ranking")
    List<EventResult> findByHeatIdsWithDetails(@Param("heatIds") List<Long> heatIds);
}
