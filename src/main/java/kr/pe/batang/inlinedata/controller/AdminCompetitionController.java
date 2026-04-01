package kr.pe.batang.inlinedata.controller;
import kr.pe.batang.inlinedata.controller.dto.CompetitionFormDto;

import jakarta.validation.Valid;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.service.CompetitionService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/competitions")
@RequiredArgsConstructor
public class AdminCompetitionController {

    private final CompetitionService competitionService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("competitions", competitionService.findAll());
        return "admin/competition/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("competition", competitionService.findById(id));
        return "admin/competition/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("dto", new CompetitionFormDto());
        return "admin/competition/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("dto") CompetitionFormDto dto, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "admin/competition/form";
        }
        competitionService.create(dto);
        return "redirect:/admin/competitions";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Competition competition = competitionService.findById(id);
        model.addAttribute("dto", CompetitionFormDto.from(competition));
        model.addAttribute("competition", competition);
        return "admin/competition/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("dto") CompetitionFormDto dto, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "admin/competition/form";
        }
        competitionService.update(id, dto);
        return "redirect:/admin/competitions/" + id;
    }

    @PostMapping("/{id}/image")
    public String uploadImage(@PathVariable Long id, @RequestParam("imageFile") MultipartFile file,
                              RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("imageError", "파일을 선택해주세요.");
            return "redirect:/admin/competitions/" + id + "/edit";
        }
        competitionService.saveImage(id, file);
        redirectAttributes.addFlashAttribute("imageSuccess", "이미지가 업로드되었습니다.");
        return "redirect:/admin/competitions/" + id + "/edit";
    }

    @PostMapping("/{id}/image/delete")
    public String deleteImage(@PathVariable Long id) {
        competitionService.deleteImage(id);
        return "redirect:/admin/competitions/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        competitionService.delete(id);
        return "redirect:/admin/competitions";
    }
}
