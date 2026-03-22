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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/athletes")
@RequiredArgsConstructor
public class AdminAthleteController {

    private final AthleteService athleteService;

    @GetMapping
    public String list(@RequestParam(required = false) String name,
                       @RequestParam(required = false) Integer birthYear,
                       @RequestParam(required = false) String notes,
                       Model model) {
        boolean hasFilter = (name != null && !name.isBlank())
                || birthYear != null
                || (notes != null && !notes.isBlank());
        if (hasFilter) {
            model.addAttribute("athletes", athleteService.search(name, birthYear, notes));
        } else {
            model.addAttribute("athletes", athleteService.findAll());
        }
        model.addAttribute("filterName", name);
        model.addAttribute("filterBirthYear", birthYear);
        model.addAttribute("filterNotes", notes);
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
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            athleteService.delete(id);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/athletes/" + id;
        }
        return "redirect:/admin/athletes";
    }

    @PostMapping("/{id}/delete-force")
    public String deleteForce(@PathVariable Long id) {
        athleteService.deleteForce(id);
        return "redirect:/admin/athletes";
    }
}
