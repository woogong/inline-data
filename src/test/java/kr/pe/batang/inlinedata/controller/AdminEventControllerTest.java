package kr.pe.batang.inlinedata.controller;
import kr.pe.batang.inlinedata.controller.dto.EventFormDto;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.Event;
import kr.pe.batang.inlinedata.service.CompetitionService;
import kr.pe.batang.inlinedata.service.EntryImportService;
import kr.pe.batang.inlinedata.service.EntryService;
import kr.pe.batang.inlinedata.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminEventController.class)
class AdminEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private EventService eventService;
    @MockitoBean private CompetitionService competitionService;
    @MockitoBean private EntryService entryService;
    @MockitoBean private EntryImportService entryImportService;

    private Competition createCompetition() {
        return Competition.builder().name("테스트 대회").shortName("테스트").build();
    }

    private Event createEvent() {
        return Event.builder()
                .competition(createCompetition())
                .divisionName("여초부 5,6학년")
                .gender("F")
                .eventName("500m+D")
                .build();
    }

    @Test
    @DisplayName("종목 목록")
    void list() throws Exception {
        given(competitionService.findById(1L)).willReturn(createCompetition());
        given(eventService.findByCompetitionId(1L)).willReturn(List.of(createEvent()));

        mockMvc.perform(get("/admin/competitions/1/events"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/event/list"));
    }

    @Test
    @DisplayName("종목 등록")
    void create() throws Exception {
        given(competitionService.findById(1L)).willReturn(createCompetition());
        given(eventService.create(eq(1L), any(EventFormDto.class))).willReturn(createEvent());

        mockMvc.perform(post("/admin/competitions/1/events")
                        .param("divisionName", "여초부 5,6학년")
                        .param("gender", "F")
                        .param("eventName", "500m+D"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/competitions/1/events"));
    }

    @Test
    @DisplayName("종목 삭제")
    void delete() throws Exception {
        mockMvc.perform(post("/admin/competitions/1/events/2/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/competitions/1/events"));

        then(eventService).should().delete(2L);
    }
}
