package kr.pe.batang.inlinedata.controller.api;

import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventHeat;
import kr.pe.batang.inlinedata.entity.EventResult;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiEventController {

    private final EventService eventService;

    @GetMapping("/competitions/{compId}/events/{eventId}")
    public Map<String, Object> eventDetail(@PathVariable Long compId, @PathVariable Long eventId) {
        Event event = eventService.findById(eventId);
        List<EventService.RoundWithStatus> rounds = eventService.findRoundsWithStatus(eventId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", event.getId());
        result.put("divisionName", event.getDivisionName());
        result.put("gender", event.getGender());
        result.put("eventName", event.getEventName());
        result.put("teamEvent", event.isTeamEvent());
        result.put("rounds", rounds.stream().map(rws -> {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", rws.round().getId());
            rm.put("round", rws.round().getRound());
            rm.put("eventNumber", rws.round().getEventNumber());
            rm.put("dayNumber", rws.round().getDayNumber());
            rm.put("status", rws.status());
            rm.put("entryCount", rws.entryCount());
            rm.put("resultCount", rws.resultCount());
            return rm;
        }).toList());
        return result;
    }

    @GetMapping("/competitions/{compId}/events/{eventId}/rounds/{roundId}")
    public Map<String, Object> roundDetail(@PathVariable Long compId,
                                           @PathVariable Long eventId,
                                           @PathVariable Long roundId) {
        EventRound round = eventService.findRoundById(roundId);
        Event event = eventService.findById(eventId);
        boolean isFinal = "결승".equals(round.getRound()) || "조별결승".equals(round.getRound());
        boolean hasResults = eventService.hasResults(roundId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roundId", round.getId());
        result.put("round", round.getRound());
        result.put("eventNumber", round.getEventNumber());
        result.put("dayNumber", round.getDayNumber());
        result.put("isFinal", isFinal);
        result.put("hasResults", hasResults);

        // Event info
        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("id", event.getId());
        eventMap.put("divisionName", event.getDivisionName());
        eventMap.put("gender", event.getGender());
        eventMap.put("eventName", event.getEventName());
        eventMap.put("teamEvent", event.isTeamEvent());
        result.put("event", eventMap);

        if (hasResults) {
            Map<EventHeat, List<EventResult>> heatsWithResults = eventService.findHeatsWithResults(roundId);
            result.put("heats", heatsWithResults.entrySet().stream().map(entry -> {
                EventHeat heat = entry.getKey();
                List<EventResult> results = entry.getValue();
                Map<String, Object> heatMap = new LinkedHashMap<>();
                heatMap.put("id", heat.getId());
                heatMap.put("heatNumber", heat.getHeatNumber());
                heatMap.put("entries", results.stream().map(er -> {
                    Map<String, Object> entryMap = new LinkedHashMap<>();
                    HeatEntry he = er.getHeatEntry();
                    entryMap.put("heatEntryId", he.getId());
                    entryMap.put("bibNumber", he.getBibNumber());
                    entryMap.put("athleteName", he.getEntry().getAthleteName());
                    entryMap.put("region", he.getEntry().getRegion());
                    entryMap.put("teamName", he.getEntry().getTeamName());
                    entryMap.put("ranking", er.getRanking());
                    entryMap.put("record", er.getRecord());
                    entryMap.put("newRecord", er.getNewRecord());
                    entryMap.put("qualification", er.getQualification());
                    entryMap.put("note", er.getNote());
                    return entryMap;
                }).toList());
                return heatMap;
            }).toList());
        } else {
            Map<EventHeat, List<HeatEntry>> heatsWithEntries = eventService.findHeatsWithEntries(roundId);
            result.put("heats", heatsWithEntries.entrySet().stream().map(entry -> {
                EventHeat heat = entry.getKey();
                List<HeatEntry> entries = entry.getValue();
                Map<String, Object> heatMap = new LinkedHashMap<>();
                heatMap.put("id", heat.getId());
                heatMap.put("heatNumber", heat.getHeatNumber());
                heatMap.put("entries", entries.stream().map(he -> {
                    Map<String, Object> entryMap = new LinkedHashMap<>();
                    entryMap.put("heatEntryId", he.getId());
                    entryMap.put("bibNumber", he.getBibNumber());
                    entryMap.put("athleteName", he.getEntry().getAthleteName());
                    entryMap.put("region", he.getEntry().getRegion());
                    entryMap.put("teamName", he.getEntry().getTeamName());
                    entryMap.put("ranking", null);
                    entryMap.put("record", null);
                    entryMap.put("newRecord", null);
                    entryMap.put("qualification", null);
                    entryMap.put("note", null);
                    return entryMap;
                }).toList());
                return heatMap;
            }).toList());
        }

        return result;
    }
}
