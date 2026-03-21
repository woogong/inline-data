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

@Controller
@RequestMapping("/competitions/{compId}/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final CompetitionService competitionService;

    @GetMapping
    public String list(@PathVariable Long compId, Model model) {
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("events", eventService.findByCompetitionId(compId));
        return "event/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long compId, @PathVariable Long id, Model model) {
        Event event = eventService.findById(id);
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("event", event);
        model.addAttribute("heats", eventService.findHeatsByEventId(id));
        return "event/detail";
    }
}
