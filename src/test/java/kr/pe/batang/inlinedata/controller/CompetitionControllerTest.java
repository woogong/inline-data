package kr.pe.batang.inlinedata.controller;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.service.CompetitionService;
import kr.pe.batang.inlinedata.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompetitionController.class)
class CompetitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompetitionService competitionService;

    @MockitoBean
    private EventService eventService;

    private Competition createCompetition() {
        return Competition.builder()
                .name("테스트 대회")
                .shortName("테스트")
                .startDate(LocalDate.of(2025, 6, 20))
                .endDate(LocalDate.of(2025, 6, 22))
                .durationDays(3)
                .venue("나주시")
                .host("대한롤러스포츠연맹")
                .organizer("전라남도롤러스포츠연맹")
                .build();
    }

    @Test
    @DisplayName("사용자 목록 페이지")
    void list() throws Exception {
        given(competitionService.findAll()).willReturn(List.of(createCompetition()));

        mockMvc.perform(get("/competitions"))
                .andExpect(status().isOk())
                .andExpect(view().name("competition/list"))
                .andExpect(model().attributeExists("competitions"));
    }

    @Test
    @DisplayName("사용자 상세 페이지")
    void detail() throws Exception {
        given(competitionService.findById(1L)).willReturn(createCompetition());

        mockMvc.perform(get("/competitions/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("competition/detail"))
                .andExpect(model().attributeExists("competition"));
    }
}
