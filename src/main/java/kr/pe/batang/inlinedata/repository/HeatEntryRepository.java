package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.HeatEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HeatEntryRepository extends JpaRepository<HeatEntry, Long> {

    List<HeatEntry> findByHeatIdOrderByBibNumberAsc(Long heatId);

    long countByHeatIdIn(List<Long> heatIds);

    List<HeatEntry> findByEntryId(Long entryId);

    @Query("SELECT he FROM HeatEntry he " +
           "JOIN FETCH he.entry ce " +
           "LEFT JOIN FETCH ce.athlete " +
           "LEFT JOIN FETCH ce.team " +
           "WHERE he.heat.id IN :heatIds " +
           "ORDER BY he.heat.id, he.bibNumber")
    List<HeatEntry> findByHeatIdsWithDetails(@Param("heatIds") List<Long> heatIds);
}
