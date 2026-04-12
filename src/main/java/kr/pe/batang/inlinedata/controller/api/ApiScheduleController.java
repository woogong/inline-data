package kr.pe.batang.inlinedata.controller.api;

import kr.pe.batang.inlinedata.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/competitions/{id}/schedule")
    public ResponseEntity<?> schedule(@PathVariable Long id,
                                       @RequestParam(required = false) Integer day) {
        if (!scheduleService.hasSchedule(id)) {
            return ResponseEntity.notFound().build();
        }
        if (day != null) {
            var daySchedule = scheduleService.findByDay(id, day);
            if (daySchedule == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(daySchedule);
        }
        return ResponseEntity.ok(scheduleService.findByCompetition(id));
    }

    @GetMapping("/competitions/{id}/schedule/exists")
    public Map<String, Boolean> hasSchedule(@PathVariable Long id) {
        return Map.of("exists", scheduleService.hasSchedule(id));
    }

    @PostMapping("/admin/competitions/{id}/schedule/import")
    public Map<String, Object> importSchedule(@PathVariable Long id,
                                               @RequestBody List<Map<String, Object>> data) {
        int count = scheduleService.importSchedule(id, data);
        return Map.of("imported", count);
    }
}
