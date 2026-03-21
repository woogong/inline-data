package kr.pe.batang.inlinedata.controller;
import kr.pe.batang.inlinedata.controller.dto.TeamFormDto;

import kr.pe.batang.inlinedata.entity.Team;
import kr.pe.batang.inlinedata.service.TeamService;
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

@WebMvcTest(AdminTeamController.class)
class AdminTeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamService teamService;

    private Team createTeam() {
        return Team.builder().name("팀에스").region("경기").build();
    }

    @Test
    @DisplayName("소속 목록")
    void list() throws Exception {
        given(teamService.findAll()).willReturn(List.of(createTeam()));

        mockMvc.perform(get("/admin/teams"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/team/list"));
    }

    @Test
    @DisplayName("소속 등록")
    void create() throws Exception {
        given(teamService.create(any(TeamFormDto.class))).willReturn(createTeam());

        mockMvc.perform(post("/admin/teams")
                        .param("name", "팀에스")
                        .param("region", "경기"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/teams"));
    }

    @Test
    @DisplayName("소속 삭제")
    void delete() throws Exception {
        mockMvc.perform(post("/admin/teams/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/teams"));

        then(teamService).should().delete(1L);
    }
}
