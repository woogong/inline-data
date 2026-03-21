package kr.pe.batang.inlinedata.controller;
import kr.pe.batang.inlinedata.controller.dto.TeamFormDto;

import jakarta.validation.Valid;
import kr.pe.batang.inlinedata.entity.Team;
import kr.pe.batang.inlinedata.service.TeamService;
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
@RequestMapping("/admin/teams")
@RequiredArgsConstructor
public class AdminTeamController {

    private final TeamService teamService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("teams", teamService.findAll());
        return "admin/team/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("team", teamService.findById(id));
        return "admin/team/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("dto", new TeamFormDto());
        return "admin/team/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("dto") TeamFormDto dto, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "admin/team/form";
        }
        teamService.create(dto);
        return "redirect:/admin/teams";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Team team = teamService.findById(id);
        model.addAttribute("dto", TeamFormDto.from(team));
        return "admin/team/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("dto") TeamFormDto dto, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "admin/team/form";
        }
        teamService.update(id, dto);
        return "redirect:/admin/teams/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        teamService.delete(id);
        return "redirect:/admin/teams";
    }
}
