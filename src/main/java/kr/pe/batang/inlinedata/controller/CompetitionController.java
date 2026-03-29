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
import org.springframework.web.bind.annotation.ResponseBody;

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
        } else if ("records".equals(tab)) {
            model.addAttribute("newRecords", eventService.findNewRecords(id));
        } else if ("regionMedals".equals(tab)) {
            model.addAttribute("regionMedalStats", eventService.findRegionMedalStats(id));
        } else if ("teamMedals".equals(tab)) {
            model.addAttribute("teamMedalStats", eventService.findTeamMedalStats(id));
        }
        return "competition/detail";
    }

    @GetMapping("/{id}/athlete-profile")
    @ResponseBody
    public EventService.AthleteProfileInfo athleteProfile(@PathVariable Long id,
                                                          @RequestParam String name) {
        return eventService.findAthleteProfile(id, name);
    }
}
