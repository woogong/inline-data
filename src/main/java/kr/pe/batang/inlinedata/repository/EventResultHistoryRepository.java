package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.EventResultHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventResultHistoryRepository extends JpaRepository<EventResultHistory, Long> {

    List<EventResultHistory> findByEventResultIdOrderByRecordedAtDesc(Long eventResultId);
}