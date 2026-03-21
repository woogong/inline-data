package kr.pe.batang.inlinedata.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.pe.batang.inlinedata.entity.Team;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TeamFormDto {

    private Long id;

    @NotBlank(message = "소속명은 필수입니다.")
    @Size(max = 100, message = "소속명은 100자 이내여야 합니다.")
    private String name;

    @NotBlank(message = "시도는 필수입니다.")
    @Size(max = 20, message = "시도는 20자 이내여야 합니다.")
    private String region;

    public Team toEntity() {
        return Team.builder()
                .name(name)
                .region(region)
                .build();
    }

    public static TeamFormDto from(Team team) {
        TeamFormDto dto = new TeamFormDto();
        dto.setId(team.getId());
        dto.setName(team.getName());
        dto.setRegion(team.getRegion());
        return dto;
    }
}
