package kr.pe.batang.inlinedata.controller;
import kr.pe.batang.inlinedata.controller.dto.AthleteFormDto;

import jakarta.validation.Valid;
import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.service.AthleteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/athletes")
@RequiredArgsConstructor
public class AdminAthleteController {

    private final AthleteService athleteService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("athletes", athleteService.findAll());
        return "admin/athlete/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("athlete", athleteService.findById(id));
        return "admin/athlete/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("dto", new AthleteFormDto());
        return "admin/athlete/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("dto") AthleteFormDto dto, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "admin/athlete/form";
        }
        athleteService.create(dto);
        return "redirect:/admin/athletes";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Athlete athlete = athleteService.findById(id);
        model.addAttribute("dto", AthleteFormDto.from(athlete));
        return "admin/athlete/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("dto") AthleteFormDto dto, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "admin/athlete/form";
        }
        athleteService.update(id, dto);
        return "redirect:/admin/athletes/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        athleteService.delete(id);
        return "redirect:/admin/athletes";
    }
}
