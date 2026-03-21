package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.HeatEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HeatEntryRepository extends JpaRepository<HeatEntry, Long> {

    List<HeatEntry> findByHeatIdOrderByBibNumberAsc(Long heatId);
}
