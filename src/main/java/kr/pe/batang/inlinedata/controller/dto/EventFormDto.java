package kr.pe.batang.inlinedata.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.entity.Event;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EventFormDto {

    private Long id;

    @NotBlank(message = "부별명은 필수입니다.")
    @Size(max = 50, message = "부별명은 50자 이내여야 합니다.")
    private String divisionName;

    @NotBlank(message = "성별은 필수입니다.")
    private String gender;

    @NotBlank(message = "종목명은 필수입니다.")
    @Size(max = 30, message = "종목명은 30자 이내여야 합니다.")
    private String eventName;

    private boolean teamEvent;

    public Event toEntity(Competition competition) {
        return Event.builder()
                .competition(competition)
                .divisionName(divisionName)
                .gender(gender)
                .eventName(eventName)
                .teamEvent(teamEvent)
                .build();
    }

    public static EventFormDto from(Event event) {
        EventFormDto dto = new EventFormDto();
        dto.setId(event.getId());
        dto.setDivisionName(event.getDivisionName());
        dto.setGender(event.getGender());
        dto.setEventName(event.getEventName());
        dto.setTeamEvent(event.isTeamEvent());
        return dto;
    }
}
