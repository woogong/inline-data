package kr.pe.batang.inlinedata.controller;

import kr.pe.batang.inlinedata.service.AutoResultImportService;
import kr.pe.batang.inlinedata.service.CompetitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/admin/result-import")
@RequiredArgsConstructor
public class AdminResultImportController {

    private final AutoResultImportService autoResultImportService;
    private final CompetitionService competitionService;

    @GetMapping
    public String page(Model model) {
        var status = autoResultImportService.getStatus();
        model.addAttribute("status", status);
        model.addAttribute("competitions", competitionService.findAll());
        model.addAttribute("recentResultImports", autoResultImportService.findRecentImports(status.selectedCompetitionId()));
        return "admin/result-import/settings";
    }

    @PostMapping
    public String update(@RequestParam(defaultValue = "false") boolean autoScanEnabled,
                         @RequestParam(required = false) Long competitionId,
                         RedirectAttributes redirectAttributes) {
        autoResultImportService.updateSetting(autoScanEnabled, competitionId);
        redirectAttributes.addFlashAttribute("message", "자동 결과 등록 설정을 저장했습니다.");
        return "redirect:/admin/result-import";
    }

    @PostMapping("/scan")
    @ResponseBody
    public Map<String, Object> scan() {
        var result = autoResultImportService.scanUsingCurrentSetting();
        return Map.of(
                "status", "ok",
                "scanned", result.scanned(),
                "imported", result.imported(),
                "skipped", result.skipped(),
                "failed", result.failed(),
                "results", result.results(),
                "newEntries", result.newEntries()
        );
    }
}
