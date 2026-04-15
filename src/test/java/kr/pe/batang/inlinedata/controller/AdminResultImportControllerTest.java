package kr.pe.batang.inlinedata.controller;

import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.service.AutoResultImportService;
import kr.pe.batang.inlinedata.service.CompetitionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminResultImportController.class)
class AdminResultImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutoResultImportService autoResultImportService;

    @MockitoBean
    private CompetitionService competitionService;

    @Test
    @DisplayName("자동 결과 등록 설정 페이지")
    void page() throws Exception {
        given(autoResultImportService.getStatus())
                .willReturn(new AutoResultImportService.AutoImportStatus(false, "/tmp/posting", 1L, "테스트", 15L));
        given(autoResultImportService.findRecentImports(1L)).willReturn(List.of());
        given(competitionService.findAll()).willReturn(List.of(
                Competition.builder().name("테스트 대회").shortName("테스트").build()
        ));

        mockMvc.perform(get("/admin/result-import"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/result-import/settings"))
                .andExpect(model().attributeExists("status"))
                .andExpect(model().attributeExists("competitions"));
    }

    @Test
    @DisplayName("자동 결과 등록 설정 저장")
    void update() throws Exception {
        mockMvc.perform(post("/admin/result-import")
                        .param("autoScanEnabled", "true")
                        .param("competitionId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/result-import"));

        then(autoResultImportService).should().updateSetting(true, 1L);
    }

    @Test
    @DisplayName("자동 결과 등록 수동 스캔")
    void scan() throws Exception {
        given(autoResultImportService.scanUsingCurrentSetting())
                .willReturn(new AutoResultImportService.ScanSummary(1, 1, 0, 0, 3, 1));

        mockMvc.perform(post("/admin/result-import/scan"))
                .andExpect(status().isOk());
    }
}
