package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.ScheduleEntry;
import kr.pe.batang.inlinedata.repository.CompetitionRepository;
import kr.pe.batang.inlinedata.repository.ScheduleEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleEntryRepository scheduleEntryRepository;
    private final CompetitionRepository competitionRepository;

    public record ScheduleEntryDto(Integer orderNumber, String startTime, String divisionName,
                                    String eventName, String roundType, String heatInfo,
                                    Integer quarterFinalRef, Integer semiFinalRef, Integer finalRef,
                                    String entryType, String notes) {}

    public record DaySchedule(int dayNumber, LocalDate date, String dayLabel, List<ScheduleEntryDto> entries) {}

    public record CompetitionSchedule(Long competitionId, List<DaySchedule> days) {}

    public boolean hasSchedule(Long competitionId) {
        return scheduleEntryRepository.existsByCompetitionId(competitionId);
    }

    public CompetitionSchedule findByCompetition(Long competitionId) {
        List<ScheduleEntry> entries = scheduleEntryRepository.findByCompetitionIdOrderByDayNumberAscIdAsc(competitionId);
        return buildSchedule(competitionId, entries);
    }

    public DaySchedule findByDay(Long competitionId, int dayNumber) {
        List<ScheduleEntry> entries = scheduleEntryRepository.findByCompetitionIdAndDayNumberOrderByIdAsc(competitionId, dayNumber);
        if (entries.isEmpty()) return null;

        ScheduleEntry first = entries.getFirst();
        String dayLabel = buildDayLabel(first.getDayNumber(), first.getDayDate());
        List<ScheduleEntryDto> dtos = entries.stream().map(this::toDto).toList();
        return new DaySchedule(dayNumber, first.getDayDate(), dayLabel, dtos);
    }

    @Transactional
    public int importSchedule(Long competitionId, List<Map<String, Object>> data) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("대회를 찾을 수 없습니다. id=" + competitionId));

        // 기존 일정 삭제
        scheduleEntryRepository.deleteByCompetitionId(competitionId);

        int count = 0;
        for (Map<String, Object> row : data) {
            ScheduleEntry entry = ScheduleEntry.builder()
                    .competition(competition)
                    .dayNumber(getInt(row, "dayNumber"))
                    .dayDate(row.get("dayDate") != null ? LocalDate.parse(row.get("dayDate").toString()) : null)
                    .orderNumber(getInt(row, "orderNumber"))
                    .startTime((String) row.get("startTime"))
                    .divisionName((String) row.get("divisionName"))
                    .eventName((String) row.get("eventName"))
                    .roundType((String) row.get("roundType"))
                    .heatInfo((String) row.get("heatInfo"))
                    .quarterFinalRef(getInt(row, "quarterFinalRef"))
                    .semiFinalRef(getInt(row, "semiFinalRef"))
                    .finalRef(getInt(row, "finalRef"))
                    .entryType((String) row.getOrDefault("entryType", "race"))
                    .notes((String) row.get("notes"))
                    .build();
            scheduleEntryRepository.save(entry);
            count++;
        }
        return count;
    }

    private CompetitionSchedule buildSchedule(Long competitionId, List<ScheduleEntry> entries) {
        Map<Integer, List<ScheduleEntry>> byDay = entries.stream()
                .collect(Collectors.groupingBy(ScheduleEntry::getDayNumber, LinkedHashMap::new, Collectors.toList()));

        List<DaySchedule> days = byDay.entrySet().stream()
                .map(e -> {
                    int dayNumber = e.getKey();
                    List<ScheduleEntry> dayEntries = e.getValue();
                    LocalDate date = dayEntries.getFirst().getDayDate();
                    String dayLabel = buildDayLabel(dayNumber, date);
                    List<ScheduleEntryDto> dtos = dayEntries.stream().map(this::toDto).toList();
                    return new DaySchedule(dayNumber, date, dayLabel, dtos);
                })
                .toList();

        return new CompetitionSchedule(competitionId, days);
    }

    private ScheduleEntryDto toDto(ScheduleEntry e) {
        return new ScheduleEntryDto(e.getOrderNumber(), e.getStartTime(), e.getDivisionName(),
                e.getEventName(), e.getRoundType(), e.getHeatInfo(),
                e.getQuarterFinalRef(), e.getSemiFinalRef(), e.getFinalRef(),
                e.getEntryType(), e.getNotes());
    }

    private String buildDayLabel(int dayNumber, LocalDate date) {
        if (date == null) return "제" + dayNumber + "일차";
        String[] dayNames = {"", "월", "화", "수", "목", "금", "토", "일"};
        String dow = dayNames[date.getDayOfWeek().getValue()];
        return String.format("제%d일차 (%d/%d %s)", dayNumber, date.getMonthValue(), date.getDayOfMonth(), dow);
    }

    private Integer getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
}
