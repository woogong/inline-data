package kr.pe.batang.inlinedata.controller.api;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.service.CompetitionService;
import kr.pe.batang.inlinedata.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiCompetitionController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final CompetitionService competitionService;
    private final EventService eventService;

    @GetMapping("/competitions")
    public List<Map<String, Object>> list() {
        List<Competition> competitions = competitionService.findAll().stream()
                .sorted(Comparator.comparing(Competition::getStartDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return competitions.stream().map(this::toCompetitionMap).toList();
    }

    @GetMapping("/competitions/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return toCompetitionMap(competitionService.findById(id));
    }

    @GetMapping("/competitions/{id}/events")
    public List<Map<String, Object>> events(@PathVariable Long id) {
        List<Event> events = eventService.findByCompetitionId(id);
        Map<Long, EventService.MedalInfo> medals = eventService.findMedalsByCompetitionId(id);

        return events.stream().map(event -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", event.getId());
            map.put("divisionName", event.getDivisionName());
            map.put("gender", event.getGender());
            map.put("eventName", event.getEventName());
            map.put("teamEvent", event.isTeamEvent());

            EventService.MedalInfo medal = medals.get(event.getId());
            if (medal != null) {
                Map<String, Object> medalMap = new LinkedHashMap<>();
                medalMap.put("gold", medal.gold());
                medalMap.put("goldId", medal.goldId());
                medalMap.put("silver", medal.silver());
                medalMap.put("silverId", medal.silverId());
                medalMap.put("bronze", medal.bronze());
                medalMap.put("bronzeId", medal.bronzeId());
                map.put("medals", medalMap);
            } else {
                map.put("medals", null);
            }
            return map;
        }).toList();
    }

    @GetMapping("/competitions/{id}/stats")
    public EventService.ParticipantStats stats(@PathVariable Long id) {
        return eventService.findParticipantStats(id);
    }

    @GetMapping("/competitions/{id}/records")
    public List<EventService.NewRecordInfo> records(@PathVariable Long id) {
        return eventService.findNewRecords(id);
    }

    @GetMapping("/competitions/{id}/region-medals")
    public List<EventService.RegionMedalStat> regionMedals(@PathVariable Long id) {
        return eventService.findRegionMedalStats(id);
    }

    @GetMapping("/competitions/{id}/team-medals")
    public List<EventService.TeamMedalStat> teamMedals(@PathVariable Long id) {
        return eventService.findTeamMedalStats(id);
    }

    @GetMapping("/competitions/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        Competition competition = competitionService.findById(id);
        if (competition.getImagePath() == null) {
            return ResponseEntity.notFound().build();
        }
        Path filePath = Paths.get(uploadDir, competition.getImagePath());
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String fileName = filePath.getFileName().toString().toLowerCase();
        MediaType mediaType = fileName.endsWith(".png") ? MediaType.IMAGE_PNG
                : fileName.endsWith(".gif") ? MediaType.IMAGE_GIF
                : fileName.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Cache-Control", "public, max-age=86400")
                .body(resource);
    }

    private Map<String, Object> toCompetitionMap(Competition c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("name", c.getName());
        map.put("shortName", c.getShortName());
        map.put("edition", c.getEdition());
        map.put("startDate", c.getStartDate());
        map.put("endDate", c.getEndDate());
        map.put("durationDays", c.getDurationDays());
        map.put("venue", c.getVenue());
        map.put("venueDetail", c.getVenueDetail());
        map.put("host", c.getHost());
        map.put("organizer", c.getOrganizer());
        map.put("notes", c.getNotes());
        map.put("hasImage", c.getImagePath() != null);
        return map;
    }
}
