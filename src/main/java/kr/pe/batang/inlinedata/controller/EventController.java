package kr.pe.batang.inlinedata.controller;

import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventRound;
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

    @GetMapping("/{eventId}")
    public String detail(@PathVariable Long compId, @PathVariable Long eventId, Model model) {
        Event event = eventService.findById(eventId);
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("event", event);
        model.addAttribute("rounds", eventService.findRoundsByEventId(eventId));
        return "event/detail";
    }

    @GetMapping("/{eventId}/rounds/{roundId}")
    public String roundDetail(@PathVariable Long compId, @PathVariable Long eventId,
                              @PathVariable Long roundId, Model model) {
        EventRound round = eventService.findRoundById(roundId);
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("event", eventService.findById(eventId));
        model.addAttribute("round", round);
        boolean hasResults = eventService.hasResults(roundId);
        model.addAttribute("hasResults", hasResults);
        if (hasResults) {
            model.addAttribute("heatsWithResults", eventService.findHeatsWithResults(roundId));
        } else {
            model.addAttribute("heatsWithEntries", eventService.findHeatsWithEntries(roundId));
        }
        return "event/round-detail";
    }
}
