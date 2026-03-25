package kr.pe.batang.inlinedata.controller;

import kr.pe.batang.inlinedata.service.CompetitionService;
import kr.pe.batang.inlinedata.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/competitions")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionService competitionService;
    private final EventService eventService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("competitions", competitionService.findAll());
        return "competition/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(defaultValue = "overview") String tab,
                         Model model) {
        model.addAttribute("competition", competitionService.findById(id));
        model.addAttribute("tab", tab);
        if ("events".equals(tab)) {
            model.addAttribute("events", eventService.findByCompetitionId(id));
            model.addAttribute("medals", eventService.findMedalsByCompetitionId(id));
        } else if ("stats".equals(tab)) {
            model.addAttribute("stats", eventService.findParticipantStats(id));
        }
        return "competition/detail";
    }
}
