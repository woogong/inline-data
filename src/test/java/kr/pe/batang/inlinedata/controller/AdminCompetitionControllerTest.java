package kr.pe.batang.inlinedata.controller;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.service.CompetitionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminCompetitionController.class)
class AdminCompetitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompetitionService competitionService;

    private Competition createCompetition() {
        return Competition.builder()
                .name("테스트 대회")
                .startDate(LocalDate.of(2025, 6, 20))
                .endDate(LocalDate.of(2025, 6, 22))
                .durationDays(3)
                .venue("나주시")
                .host("대한롤러스포츠연맹")
                .organizer("전라남도롤러스포츠연맹")
                .build();
    }

    @Test
    @DisplayName("관리자 목록 페이지")
    void list() throws Exception {
        given(competitionService.findAll()).willReturn(List.of(createCompetition()));

        mockMvc.perform(get("/admin/competitions"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/competition/list"))
                .andExpect(model().attributeExists("competitions"));
    }

    @Test
    @DisplayName("관리자 상세 페이지")
    void detail() throws Exception {
        given(competitionService.findById(1L)).willReturn(createCompetition());

        mockMvc.perform(get("/admin/competitions/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/competition/detail"))
                .andExpect(model().attributeExists("competition"));
    }

    @Test
    @DisplayName("등록 폼 페이지")
    void createForm() throws Exception {
        mockMvc.perform(get("/admin/competitions/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/competition/form"))
                .andExpect(model().attributeExists("dto"));
    }

    @Test
    @DisplayName("대회 등록 처리")
    void create() throws Exception {
        given(competitionService.create(any(CompetitionFormDto.class))).willReturn(createCompetition());

        mockMvc.perform(post("/admin/competitions")
                        .param("name", "신규 대회"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/competitions"));
    }

    @Test
    @DisplayName("대회 등록 - 유효성 검증 실패")
    void createValidationFail() throws Exception {
        mockMvc.perform(post("/admin/competitions")
                        .param("name", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/competition/form"));
    }

    @Test
    @DisplayName("수정 폼 페이지")
    void editForm() throws Exception {
        given(competitionService.findById(1L)).willReturn(createCompetition());

        mockMvc.perform(get("/admin/competitions/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/competition/form"))
                .andExpect(model().attributeExists("dto"));
    }

    @Test
    @DisplayName("대회 수정 처리")
    void update() throws Exception {
        given(competitionService.update(eq(1L), any(CompetitionFormDto.class))).willReturn(createCompetition());

        mockMvc.perform(post("/admin/competitions/1")
                        .param("name", "수정된 대회"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/competitions/1"));
    }

    @Test
    @DisplayName("대회 삭제 처리")
    void delete() throws Exception {
        mockMvc.perform(post("/admin/competitions/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/competitions"));

        then(competitionService).should().delete(1L);
    }
}
