package kr.pe.batang.inlinedata.controller;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.CompetitionEntry;
import kr.pe.batang.inlinedata.service.CompetitionService;
import kr.pe.batang.inlinedata.service.MappingService;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/competitions/{compId}/mapping")
@RequiredArgsConstructor
public class AdminMappingController {

    private final CompetitionService competitionService;
    private final MappingService mappingService;
    private final CompetitionEntryRepository entryRepository;

    @GetMapping
    public String mappingPage(@PathVariable Long compId, Model model) {
        Competition competition = competitionService.findById(compId);
        List<CompetitionEntry> entries = entryRepository.findIndividualEntriesWithAthlete(compId);
        model.addAttribute("competition", competition);
        model.addAttribute("entries", entries);
        return "admin/competition/mapping";
    }

    @GetMapping("/candidates")
    @ResponseBody
    public List<MappingService.CandidateDto> candidates(@PathVariable Long compId,
                                                         @RequestParam Long entryId) {
        return mappingService.findCandidates(entryId);
    }

    @PostMapping("/auto")
    @ResponseBody
    public Map<String, Integer> autoMatch(@PathVariable Long compId) {
        int matched = mappingService.autoMatch(compId);
        return Map.of("matched", matched);
    }

    @PostMapping("/map")
    @ResponseBody
    public Map<String, String> map(@PathVariable Long compId,
                                   @RequestBody Map<String, Long> body) {
        mappingService.mapToAthlete(body.get("entryId"), body.get("athleteId"));
        return Map.of("status", "ok");
    }

    @PostMapping("/create")
    @ResponseBody
    public Map<String, Object> create(@PathVariable Long compId,
                                      @RequestBody Map<String, Object> body) {
        Long entryId = Long.parseLong(body.get("entryId").toString());
        Integer birthYear = body.get("birthYear") != null && !body.get("birthYear").toString().isEmpty()
                ? Integer.parseInt(body.get("birthYear").toString()) : null;
        Long athleteId = mappingService.createAndMap(entryId, birthYear);
        return Map.of("status", "ok", "athleteId", athleteId);
    }

    @PostMapping("/create-bulk")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public Map<String, Object> createBulk(@PathVariable Long compId,
                                          @RequestBody Map<String, Object> body) {
        List<Number> entryIds = (List<Number>) body.get("entryIds");
        int created = 0;
        for (Number id : entryIds) {
            mappingService.createAndMap(id.longValue(), null);
            created++;
        }
        return Map.of("status", "ok", "created", created);
    }

    @PostMapping("/unmap")
    @ResponseBody
    public Map<String, String> unmap(@PathVariable Long compId,
                                     @RequestBody Map<String, Long> body) {
        mappingService.unmap(body.get("entryId"));
        return Map.of("status", "ok");
    }
}