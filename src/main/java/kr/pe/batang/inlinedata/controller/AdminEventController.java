package kr.pe.batang.inlinedata.controller;
import kr.pe.batang.inlinedata.controller.dto.EventFormDto;

import jakarta.validation.Valid;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.service.CompetitionService;
import kr.pe.batang.inlinedata.service.EntryService;
import kr.pe.batang.inlinedata.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/competitions/{compId}/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final EventService eventService;
    private final CompetitionService competitionService;
    private final EntryService entryService;

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
        return "admin/event/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long compId, @PathVariable Long id, Model model) {
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("event", eventService.findById(id));
        model.addAttribute("heatsWithEntries", eventService.findHeatsWithEntries(id));
        model.addAttribute("resultByEntryId", eventService.findResultsByEntryId(id));
        return "admin/event/detail";
    }

    @PostMapping("/{id}/results/save")
    @ResponseBody
    public Map<String, Object> saveResultApi(@PathVariable Long compId, @PathVariable Long id,
                                             @RequestBody Map<String, String> body) {
        Long heatEntryId = Long.parseLong(body.get("heatEntryId"));
        String rankingStr = body.getOrDefault("ranking", "");
        Integer ranking = rankingStr.isBlank() ? null : Integer.parseInt(rankingStr);
        String record = blankToNull(body.get("record"));
        String newRecord = blankToNull(body.get("newRecord"));
        String qualification = blankToNull(body.get("qualification"));
        String note = blankToNull(body.get("note"));
        eventService.saveResult(heatEntryId, ranking, record, newRecord, qualification, note);
        return Map.of("status", "ok");
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @GetMapping("/{id}/entries")
    public String entries(@PathVariable Long compId, @PathVariable Long id, Model model) {
        Event event = eventService.findById(id);
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("event", event);
        model.addAttribute("heatsWithEntries", eventService.findHeatsWithEntries(id));
        return "admin/event/entries";
    }

    @PostMapping("/{id}/heats/add")
    @ResponseBody
    public Map<String, Object> addHeat(@PathVariable Long compId, @PathVariable Long id,
                                       @RequestBody Map<String, String> body) {
        Event event = eventService.findById(id);
        int heatNumber = Integer.parseInt(body.get("heatNumber"));
        Long heatId = entryService.addHeat(event, heatNumber);
        return Map.of("status", "ok", "heatId", heatId);
    }

    @PostMapping("/{id}/heats/delete")
    @ResponseBody
    public Map<String, Object> deleteHeat(@PathVariable Long compId, @PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        Long heatId = Long.parseLong(body.get("heatId"));
        entryService.deleteHeat(heatId);
        return Map.of("status", "ok");
    }

    @PostMapping("/{id}/entries/save")
    @ResponseBody
    public Map<String, Object> saveEntry(@PathVariable Long compId, @PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        Event event = eventService.findById(id);
        Long heatId = Long.parseLong(body.get("heatId"));
        int bibNumber = Integer.parseInt(body.get("bibNumber"));
        String name = body.get("name");
        String region = body.getOrDefault("region", "");
        String teamName = body.getOrDefault("teamName", "");
        entryService.saveEntry(event, heatId, bibNumber, name, event.getGender(), region, teamName);
        return Map.of("status", "ok");
    }

    @PostMapping("/{id}/entries/delete")
    @ResponseBody
    public Map<String, Object> deleteEntry(@PathVariable Long compId, @PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        Long heatEntryId = Long.parseLong(body.get("heatEntryId"));
        entryService.deleteEntry(heatEntryId);
        return Map.of("status", "ok");
    }

    @GetMapping("/new")
    public String createForm(@PathVariable Long compId, Model model) {
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("dto", new EventFormDto());
        return "admin/event/form";
    }

    @PostMapping
    public String create(@PathVariable Long compId, @Valid @ModelAttribute("dto") EventFormDto dto,
                         BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("competition", competitionService.findById(compId));
            return "admin/event/form";
        }
        eventService.create(compId, dto);
        return "redirect:/admin/competitions/" + compId + "/events";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long compId, @PathVariable Long id, Model model) {
        Event event = eventService.findById(id);
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("dto", EventFormDto.from(event));
        return "admin/event/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long compId, @PathVariable Long id,
                         @Valid @ModelAttribute("dto") EventFormDto dto,
                         BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("competition", competitionService.findById(compId));
            return "admin/event/form";
        }
        eventService.update(id, dto);
        return "redirect:/admin/competitions/" + compId + "/events/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long compId, @PathVariable Long id) {
        eventService.delete(id);
        return "redirect:/admin/competitions/" + compId + "/events";
    }
}
