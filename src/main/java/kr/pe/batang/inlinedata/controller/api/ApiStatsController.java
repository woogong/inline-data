package kr.pe.batang.inlinedata.controller.api;

import kr.pe.batang.inlinedata.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class ApiStatsController {

    private final EventService eventService;

    @GetMapping("/all-time-region-medals")
    public Map<String, Object> allTimeRegionMedals() {
        return Map.of(
            "summary", eventService.findAllTimeSummary(),
            "rankings", eventService.findAllTimeRegionMedalStats()
        );
    }
}
