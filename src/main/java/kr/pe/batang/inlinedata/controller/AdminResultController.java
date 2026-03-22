package kr.pe.batang.inlinedata.controller;

import kr.pe.batang.inlinedata.service.ResultParsingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;

@Controller
@RequestMapping("/admin/competitions/{compId}/results")
@RequiredArgsConstructor
public class AdminResultController {

    private final ResultParsingService resultParsingService;

    @PostMapping("/import")
    public String importResults(@PathVariable Long compId,
                                @RequestParam String directory,
                                RedirectAttributes redirectAttributes) {
        Path dir = Path.of(directory);
        int count = resultParsingService.parseResultDirectory(dir, compId);
        redirectAttributes.addFlashAttribute("message", count + "건의 결과를 가져왔습니다.");
        return "redirect:/admin/competitions/" + compId;
    }
}
