package kr.pe.batang.inlinedata.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.pe.batang.inlinedata.entity.Athlete;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AthleteFormDto {

    private Long id;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이내여야 합니다.")
    private String name;

    @NotBlank(message = "성별은 필수입니다.")
    private String gender;

    private Integer birthYear;

    @Size(max = 200, message = "비고는 200자 이내여야 합니다.")
    private String notes;

    public Athlete toEntity() {
        return Athlete.builder()
                .name(name)
                .gender(gender)
                .birthYear(birthYear)
                .notes(notes)
                .build();
    }

    public static AthleteFormDto from(Athlete athlete) {
        AthleteFormDto dto = new AthleteFormDto();
        dto.setId(athlete.getId());
        dto.setName(athlete.getName());
        dto.setGender(athlete.getGender());
        dto.setBirthYear(athlete.getBirthYear());
        dto.setNotes(athlete.getNotes());
        return dto;
    }
}
