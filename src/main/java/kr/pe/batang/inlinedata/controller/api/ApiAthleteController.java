package kr.pe.batang.inlinedata.controller.api;

import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.service.AthleteService;
import kr.pe.batang.inlinedata.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.repository.EventResultRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiAthleteController {

    private final AthleteService athleteService;
    private final EventService eventService;
    private final EventResultRepository eventResultRepository;

    @GetMapping("/athletes")
    public List<Map<String, Object>> list(@RequestParam(required = false) String name) {
        boolean isSearch = name != null && !name.isBlank();
        List<AthleteService.AthleteListItem> items = isSearch
                ? athleteService.searchWithLatestInfo(name.trim())
                : athleteService.findAllWithLatestInfo();
        List<AthleteService.AthleteListItem> finalItems = items;
        return finalItems.stream().map(item -> {
            Map<String, Object> map = new LinkedHashMap<>();
            Athlete a = item.athlete();
            map.put("id", a.getId());
            map.put("name", a.getName());
            map.put("gender", a.getGender());
            map.put("birthYear", a.getBirthYear());
            map.put("notes", a.getNotes());
            map.put("region", item.region());
            map.put("teamName", item.teamName());
            map.put("division", item.division());
            if (isSearch) {
                map.put("recentMedals", getMedalSummary(a.getName()));
            }
            return map;
        }).toList();
    }

    @GetMapping("/athletes/{id}")
    public Map<String, Object> detail(@PathVariable Long id,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Athlete athlete = athleteService.findById(id);
        AthleteService.AthleteProfileDto profile;
        List<AthleteService.PerformanceDto> allPerformances;
        try {
            profile = athleteService.findLatestProfile(id);
            allPerformances = athleteService.findPerformances(id);
        } catch (Exception e) {
            profile = new AthleteService.AthleteProfileDto(null, null, null);
            allPerformances = List.of();
        }

        int start = Math.min(page * size, allPerformances.size());
        int end = Math.min(start + size, allPerformances.size());
        List<AthleteService.PerformanceDto> pagedPerformances = allPerformances.subList(start, end);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", athlete.getId());
        result.put("name", athlete.getName());
        result.put("gender", athlete.getGender());
        result.put("birthYear", athlete.getBirthYear());
        result.put("notes", athlete.getNotes());

        Map<String, Object> profileMap = new LinkedHashMap<>();
        profileMap.put("region", profile.region());
        profileMap.put("teamName", profile.teamName());
        profileMap.put("divisionsText", profile.divisionsText());
        result.put("profile", profileMap);

        Map<String, Object> perfPage = new LinkedHashMap<>();
        perfPage.put("content", pagedPerformances);
        perfPage.put("totalElements", allPerformances.size());
        perfPage.put("totalPages", (int) Math.ceil((double) allPerformances.size() / size));
        perfPage.put("number", page);
        perfPage.put("last", end >= allPerformances.size());
        result.put("performances", perfPage);
        return result;
    }

    @GetMapping("/competitions/{compId}/athlete-profile")
    public EventService.AthleteProfileInfo athleteProfile(@PathVariable Long compId,
                                                          @RequestParam String name) {
        return eventService.findAthleteProfile(compId, name);
    }

    private Map<String, Integer> getMedalSummary(String athleteName) {
        List<EventResult> medals = eventResultRepository.findMedalsByAthleteName(athleteName);
        int gold = 0, silver = 0, bronze = 0;
        for (EventResult er : medals) {
            switch (er.getRanking()) {
                case 1 -> gold++;
                case 2 -> silver++;
                case 3 -> bronze++;
            }
        }
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("gold", gold);
        summary.put("silver", silver);
        summary.put("bronze", bronze);
        return summary;
    }
}
