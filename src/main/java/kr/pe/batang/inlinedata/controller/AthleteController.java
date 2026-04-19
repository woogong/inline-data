package kr.pe.batang.inlinedata.controller;

import kr.pe.batang.inlinedata.service.AthleteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/athletes")
@RequiredArgsConstructor
public class AthleteController {

    private final AthleteService athleteService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("athleteItems", athleteService.findAllWithLatestInfo());
        return "athlete/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("athlete", athleteService.findById(id));
        model.addAttribute("profile", athleteService.findLatestProfile(id));
        model.addAttribute("performances", athleteService.findPerformances(id));
        return "athlete/detail";
    }
}
