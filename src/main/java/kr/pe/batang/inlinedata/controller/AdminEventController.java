package kr.pe.batang.inlinedata.controller;
import kr.pe.batang.inlinedata.controller.dto.EventFormDto;

import jakarta.validation.Valid;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.entity.EventRound;
import kr.pe.batang.inlinedata.service.CompetitionService;
import kr.pe.batang.inlinedata.service.EntryImportService;
import kr.pe.batang.inlinedata.service.EntryService;
import kr.pe.batang.inlinedata.service.EventService;
import kr.pe.batang.inlinedata.service.ResultParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/admin/competitions/{compId}/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final EventService eventService;
    private final CompetitionService competitionService;
    private final EntryService entryService;
    private final EntryImportService entryImportService;
    private final ResultParsingService resultParsingService;

    // --- 종목 목록 ---

    @GetMapping
    public String list(@PathVariable Long compId, Model model) {
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("events", eventService.findByCompetitionId(compId));
        return "admin/event/list";
    }

    @PostMapping("/import-result")
    @ResponseBody
    public Map<String, Object> importResultAtListLevel(@PathVariable Long compId,
                                                       @RequestParam("file") MultipartFile file) {
        String fileName = file.getOriginalFilename();
        try {
            Path temp = Files.createTempFile("result-", ".pdf");
            try (var out = Files.newOutputStream(temp)) {
                file.getInputStream().transferTo(out);
                out.flush();
            }
            var result = resultParsingService.parseResultPdf(temp, compId);
            Files.deleteIfExists(temp);
            log.info("결과 PDF import 성공: {} → 결과 {}건, 새 엔트리 {}건",
                    fileName, result.results(), result.newEntries());
            return Map.of("status", "ok", "fileName", fileName != null ? fileName : "",
                    "results", result.results(), "newEntries", result.newEntries());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("결과 PDF import 실패: {} - {}", fileName, msg, e);
            return Map.of("status", "error", "fileName", fileName != null ? fileName : "", "message", msg);
        }
    }

    // --- 종목 상세 (라운드 목록) ---

    @GetMapping("/{eventId}")
    public String detail(@PathVariable Long compId, @PathVariable Long eventId, Model model) {
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("event", eventService.findById(eventId));
        model.addAttribute("roundsWithStatus", eventService.findRoundsWithStatus(eventId));
        return "admin/event/detail";
    }

    @PostMapping("/{eventId}/import-result")
    @ResponseBody
    public Map<String, Object> importResultFile(@PathVariable Long compId, @PathVariable Long eventId,
                                                @RequestParam("file") MultipartFile file) {
        try {
            Path temp = Files.createTempFile("result-", ".pdf");
            try (var out = Files.newOutputStream(temp)) {
                file.getInputStream().transferTo(out);
                out.flush();
            }
            var result = resultParsingService.parseResultPdf(temp, compId);
            Files.deleteIfExists(temp);
            return Map.of("status", "ok", "results", result.results(), "newEntries", result.newEntries());
        } catch (Exception e) {
            log.error("결과 PDF import 실패: {}", file.getOriginalFilename(), e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    // --- 라운드 상세 (결과 편집) ---

    @GetMapping("/{eventId}/rounds/{roundId}")
    public String roundDetail(@PathVariable Long compId, @PathVariable Long eventId,
                              @PathVariable Long roundId, Model model) {
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("event", eventService.findById(eventId));
        model.addAttribute("round", eventService.findRoundById(roundId));
        model.addAttribute("heatsWithEntries", eventService.findHeatsWithEntries(roundId));
        model.addAttribute("resultByEntryId", eventService.findResultsByEntryId(roundId));
        return "admin/event/round-detail";
    }

    @PostMapping("/{eventId}/rounds/{roundId}/results/save")
    @ResponseBody
    public Map<String, Object> saveResultApi(@PathVariable Long compId, @PathVariable Long eventId,
                                             @PathVariable Long roundId,
                                             @RequestBody Map<String, String> body) {
        Long heatEntryId = Long.parseLong(body.get("heatEntryId"));
        String rankingStr = body.getOrDefault("ranking", "");
        Integer ranking = rankingStr.isBlank() ? null : Integer.parseInt(rankingStr);
        eventService.saveResult(heatEntryId, ranking,
                blankToNull(body.get("record")),
                blankToNull(body.get("newRecord")),
                blankToNull(body.get("qualification")),
                blankToNull(body.get("note")));
        return Map.of("status", "ok");
    }

    // --- 엔트리 등록 ---

    @GetMapping("/{eventId}/rounds/{roundId}/entries")
    public String entries(@PathVariable Long compId, @PathVariable Long eventId,
                          @PathVariable Long roundId, Model model) {
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("event", eventService.findById(eventId));
        model.addAttribute("round", eventService.findRoundById(roundId));
        model.addAttribute("heatsWithEntries", eventService.findHeatsWithEntries(roundId));
        return "admin/event/entries";
    }

    @PostMapping("/{eventId}/rounds/{roundId}/heats/add")
    @ResponseBody
    public Map<String, Object> addHeat(@PathVariable Long compId, @PathVariable Long eventId,
                                       @PathVariable Long roundId,
                                       @RequestBody Map<String, String> body) {
        EventRound round = eventService.findRoundById(roundId);
        int heatNumber = Integer.parseInt(body.get("heatNumber"));
        Long heatId = entryService.addHeat(round, heatNumber);
        return Map.of("status", "ok", "heatId", heatId);
    }

    @PostMapping("/{eventId}/rounds/{roundId}/heats/delete")
    @ResponseBody
    public Map<String, Object> deleteHeat(@PathVariable Long compId, @PathVariable Long eventId,
                                          @PathVariable Long roundId,
                                          @RequestBody Map<String, String> body) {
        Long heatId = Long.parseLong(body.get("heatId"));
        entryService.deleteHeat(heatId);
        return Map.of("status", "ok");
    }

    @PostMapping("/{eventId}/rounds/{roundId}/entries/save")
    @ResponseBody
    public Map<String, Object> saveEntry(@PathVariable Long compId, @PathVariable Long eventId,
                                         @PathVariable Long roundId,
                                         @RequestBody Map<String, String> body) {
        Event event = eventService.findById(eventId);
        Long heatId = Long.parseLong(body.get("heatId"));
        int bibNumber = Integer.parseInt(body.get("bibNumber"));
        entryService.saveEntry(event, heatId, bibNumber, body.get("name"),
                event.getGender(), body.getOrDefault("region", ""), body.getOrDefault("teamName", ""));
        return Map.of("status", "ok");
    }

    @PostMapping("/{eventId}/rounds/{roundId}/entries/delete")
    @ResponseBody
    public Map<String, Object> deleteEntry(@PathVariable Long compId, @PathVariable Long eventId,
                                           @PathVariable Long roundId,
                                           @RequestBody Map<String, String> body) {
        entryService.deleteEntry(Long.parseLong(body.get("heatEntryId")));
        return Map.of("status", "ok");
    }

    // --- 종목 CRUD ---

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

    @GetMapping("/{eventId}/edit")
    public String editForm(@PathVariable Long compId, @PathVariable Long eventId, Model model) {
        model.addAttribute("competition", competitionService.findById(compId));
        model.addAttribute("dto", EventFormDto.from(eventService.findById(eventId)));
        return "admin/event/form";
    }

    @PostMapping("/{eventId}")
    public String update(@PathVariable Long compId, @PathVariable Long eventId,
                         @Valid @ModelAttribute("dto") EventFormDto dto,
                         BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("competition", competitionService.findById(compId));
            return "admin/event/form";
        }
        eventService.update(eventId, dto);
        return "redirect:/admin/competitions/" + compId + "/events/" + eventId;
    }

    @PostMapping("/{eventId}/delete")
    public String delete(@PathVariable Long compId, @PathVariable Long eventId) {
        eventService.delete(eventId);
        return "redirect:/admin/competitions/" + compId + "/events";
    }

    // --- 조편성 PDF import ---

    @PostMapping("/import")
    public String importEntry(@PathVariable Long compId,
                              @RequestParam("file") MultipartFile file,
                              RedirectAttributes redirectAttributes) {
        try {
            Path tempFile = Files.createTempFile("entry-import-", ".pdf");
            file.transferTo(tempFile);
            var competition = competitionService.findById(compId);
            var result = entryImportService.importEntryPdf(tempFile, competition);
            Files.deleteIfExists(tempFile);
            redirectAttributes.addFlashAttribute("message",
                    String.format("종목 %d개, 경기 %d개, 조 %d개, 엔트리 %d건을 가져왔습니다.",
                            result.events(), result.rounds(), result.heats(), result.entries()));
        } catch (Exception e) {
            log.error("조편성 import 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "파일 처리 중 오류: " + e.getMessage());
        }
        return "redirect:/admin/competitions/" + compId + "/events";
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
