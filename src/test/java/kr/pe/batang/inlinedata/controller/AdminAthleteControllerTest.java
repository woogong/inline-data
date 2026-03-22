package kr.pe.batang.inlinedata.controller;
import kr.pe.batang.inlinedata.controller.dto.AthleteFormDto;

import kr.pe.batang.inlinedata.entity.Athlete;
import kr.pe.batang.inlinedata.service.AthleteService;
import kr.pe.batang.inlinedata.service.EntryParsingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAthleteController.class)
class AdminAthleteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AthleteService athleteService;

    @MockitoBean
    private EntryParsingService entryParsingService;

    private Athlete createAthlete() {
        return Athlete.builder().name("구예림").gender("F").build();
    }

    @Test
    @DisplayName("선수 목록")
    void list() throws Exception {
        given(athleteService.findAll()).willReturn(List.of(createAthlete()));

        mockMvc.perform(get("/admin/athletes"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/athlete/list"));
    }

    @Test
    @DisplayName("선수 등록")
    void create() throws Exception {
        given(athleteService.create(any(AthleteFormDto.class))).willReturn(createAthlete());

        mockMvc.perform(post("/admin/athletes")
                        .param("name", "구예림")
                        .param("gender", "F"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/athletes"));
    }

    @Test
    @DisplayName("선수 삭제")
    void delete() throws Exception {
        mockMvc.perform(post("/admin/athletes/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/athletes"));

        then(athleteService).should().delete(1L);
    }
}
