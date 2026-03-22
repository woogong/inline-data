package kr.pe.batang.inlinedata.controller;

import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.service.CompetitionService;
import kr.pe.batang.inlinedata.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/competitions/{compId}/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final CompetitionService competitionService;

    @GetMapping
    public String list(@PathVariable Long compId, Model model) {
        List<Event> events = eventService.findByCompetitionId(compId);
        Map<Integer, List<Event>> eventsByDay = events.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getDayNumber() != null ? e.getDayNumber() : 0,
                        TreeMap::new,
                        Collectors.toList()
                ));
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("eventsByDay", eventsByDay);
        return "event/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long compId, @PathVariable Long id, Model model) {
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("event", eventService.findById(id));
        boolean hasResults = eventService.hasResults(id);
        model.addAttribute("hasResults", hasResults);
        if (hasResults) {
            model.addAttribute("heatsWithResults", eventService.findHeatsWithResults(id));
        } else {
            model.addAttribute("heatsWithEntries", eventService.findHeatsWithEntries(id));
        }
        return "event/detail";
    }
}
