package kr.pe.batang.inlinedata.controller;

import java.util.List;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.entity.HeatEntry;
import kr.pe.batang.inlinedata.service.CompetitionService;
import kr.pe.batang.inlinedata.service.EntryService;
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
    private final EntryService entryService;

    @GetMapping
    public String list(@PathVariable Long compId, Model model) {
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("events", eventService.findByCompetitionId(compId));
        model.addAttribute("medals", eventService.findMedalsByCompetitionId(compId));
        return "event/list";
    }

    @GetMapping("/{eventId}")
    public String detail(@PathVariable Long compId, @PathVariable Long eventId, Model model) {
        Event event = eventService.findById(eventId);
        List<EventRound> rounds = eventService.findRoundsByEventId(eventId);

        // 결승만 있으면 바로 라운드 상세로 이동
        if (rounds.size() == 1) {
            return "redirect:/competitions/" + compId + "/events/" + eventId + "/rounds/" + rounds.getFirst().getId();
        }

        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("event", event);
        model.addAttribute("rounds", rounds);
        return "event/detail";
    }

    @GetMapping("/{eventId}/rounds/{roundId}")
    public String roundDetail(@PathVariable Long compId, @PathVariable Long eventId,
                              @PathVariable Long roundId, Model model) {
        EventRound round = eventService.findRoundById(roundId);
        model.addAttribute("competition", competitionService.findById(compId));
        Event event = eventService.findById(eventId);
        model.addAttribute("event", event);
        model.addAttribute("round", round);
        boolean isFinal = "결승".equals(round.getRound()) || "조별결승".equals(round.getRound());
        model.addAttribute("isFinal", isFinal);
        boolean hasResults = eventService.hasResults(roundId);
        model.addAttribute("hasResults", hasResults);
        List<Long> heatEntryIds;
        if (hasResults) {
            var heatsWithResults = eventService.findHeatsWithResults(roundId);
            model.addAttribute("heatsWithResults", heatsWithResults);
            heatEntryIds = heatsWithResults.values().stream()
                    .flatMap(List::stream)
                    .map(er -> er.getHeatEntry().getId()).toList();
        } else {
            var heatsWithEntries = eventService.findHeatsWithEntries(roundId);
            model.addAttribute("heatsWithEntries", heatsWithEntries);
            heatEntryIds = heatsWithEntries.values().stream()
                    .flatMap(List::stream).map(HeatEntry::getId).toList();
        }
        if (event.isRelayEvent()) {
            model.addAttribute("relayMembersByHeatEntryId",
                    entryService.findRelayMembersByHeatEntryIds(heatEntryIds));
        }
        return "event/round-detail";
    }
}
