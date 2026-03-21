package kr.pe.batang.inlinedata.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.pe.batang.inlinedata.entity.Competition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CompetitionFormDto {

    private Long id;

    @NotBlank(message = "대회명은 필수입니다.")
    @Size(max = 200, message = "대회명은 200자 이내여야 합니다.")
    private String name;

    @NotBlank(message = "간략명은 필수입니다.")
    @Size(max = 50, message = "간략명은 50자 이내여야 합니다.")
    private String shortName;

    private Integer edition;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private Integer durationDays;

    @Size(max = 200, message = "대회 장소는 200자 이내여야 합니다.")
    private String venue;

    @Size(max = 200, message = "경기장 상세는 200자 이내여야 합니다.")
    private String venueDetail;

    @Size(max = 100, message = "주최는 100자 이내여야 합니다.")
    private String host;

    @Size(max = 100, message = "주관은 100자 이내여야 합니다.")
    private String organizer;

    private String notes;

    public Competition toEntity() {
        return Competition.builder()
                .name(name)
                .shortName(shortName)
                .edition(edition)
                .startDate(startDate)
                .endDate(endDate)
                .durationDays(durationDays)
                .venue(venue)
                .venueDetail(venueDetail)
                .host(host)
                .organizer(organizer)
                .notes(notes)
                .build();
    }

    public static CompetitionFormDto from(Competition competition) {
        CompetitionFormDto dto = new CompetitionFormDto();
        dto.setId(competition.getId());
        dto.setName(competition.getName());
        dto.setShortName(competition.getShortName());
        dto.setEdition(competition.getEdition());
        dto.setStartDate(competition.getStartDate());
        dto.setEndDate(competition.getEndDate());
        dto.setDurationDays(competition.getDurationDays());
        dto.setVenue(competition.getVenue());
        dto.setVenueDetail(competition.getVenueDetail());
        dto.setHost(competition.getHost());
        dto.setOrganizer(competition.getOrganizer());
        dto.setNotes(competition.getNotes());
        return dto;
    }
}
