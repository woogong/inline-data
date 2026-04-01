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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiAthleteController {

    private final AthleteService athleteService;
    private final EventService eventService;

    @GetMapping("/athletes")
    public List<Map<String, Object>> list() {
        return athleteService.findAllWithLatestInfo().stream().map(item -> {
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
            return map;
        }).toList();
    }

    @GetMapping("/athletes/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Athlete athlete = athleteService.findById(id);
        AthleteService.AthleteProfileDto profile;
        List<AthleteService.PerformanceDto> performances;
        try {
            profile = athleteService.findLatestProfile(id);
            performances = athleteService.findPerformances(id);
        } catch (Exception e) {
            profile = new AthleteService.AthleteProfileDto(null, null, null);
            performances = List.of();
        }

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

        result.put("performances", performances);
        return result;
    }

    @GetMapping("/competitions/{compId}/athlete-profile")
    public EventService.AthleteProfileInfo athleteProfile(@PathVariable Long compId,
                                                          @RequestParam String name) {
        return eventService.findAthleteProfile(compId, name);
    }
}
