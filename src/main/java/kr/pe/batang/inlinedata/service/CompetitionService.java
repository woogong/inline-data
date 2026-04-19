package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.controller.dto.CompetitionFormDto;
import kr.pe.batang.inlinedata.entity.Competition;
import kr.pe.batang.inlinedata.repository.CompetitionEntryRepository;
import kr.pe.batang.inlinedata.repository.CompetitionRepository;
import kr.pe.batang.inlinedata.repository.EventHeatRepository;
import kr.pe.batang.inlinedata.repository.EventRepository;
import kr.pe.batang.inlinedata.repository.EventResultRepository;
import kr.pe.batang.inlinedata.repository.EventRoundRepository;
import kr.pe.batang.inlinedata.repository.HeatEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetitionService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final CompetitionRepository competitionRepository;
    private final EventRepository eventRepository;
    private final EventRoundRepository eventRoundRepository;
    private final EventHeatRepository eventHeatRepository;
    private final HeatEntryRepository heatEntryRepository;
    private final EventResultRepository eventResultRepository;
    private final CompetitionEntryRepository competitionEntryRepository;

    public List<Competition> findAll() {
        return competitionRepository.findAll();
    }

    public Competition findById(Long id) {
        return competitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("대회를 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public Competition create(CompetitionFormDto dto) {
        return competitionRepository.save(dto.toEntity());
    }

    @Transactional
    public Competition update(Long id, CompetitionFormDto dto) {
        Competition competition = findById(id);
        competition.update(
                dto.getName(),
                dto.getShortName(),
                dto.getEdition(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getDurationDays(),
                dto.getVenue(),
                dto.getVenueDetail(),
                dto.getHost(),
                dto.getOrganizer(),
                dto.getNotes()
        );
        return competition;
    }

    @Transactional
    public void saveImage(Long id, MultipartFile file) {
        Competition competition = findById(id);

        // 기존 이미지 삭제
        if (competition.getImagePath() != null) {
            deleteImageFile(competition.getImagePath());
        }

        try {
            Path dir = Paths.get(uploadDir, "competitions");
            Files.createDirectories(dir);

            String originalName = file.getOriginalFilename();
            String ext = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf('.'))
                    : ".jpg";
            String fileName = UUID.randomUUID() + ext;
            Path filePath = dir.resolve(fileName).toAbsolutePath();
            file.transferTo(filePath.toFile());

            competition.updateImagePath("competitions/" + fileName);
        } catch (IOException e) {
            throw new RuntimeException("이미지 저장에 실패했습니다.", e);
        }
    }

    @Transactional
    public void deleteImage(Long id) {
        Competition competition = findById(id);
        if (competition.getImagePath() != null) {
            deleteImageFile(competition.getImagePath());
            competition.updateImagePath(null);
        }
    }

    private void deleteImageFile(String imagePath) {
        try {
            Path path = Paths.get(uploadDir, imagePath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * 대회 및 연관 데이터 전체 삭제.
     * 역방향 FK 순서로 bulk delete — N+1 쿼리 제거, O(1) round trips.
     */
    @Transactional
    public void delete(Long id) {
        Competition competition = findById(id);
        // 역방향 순서: EventResult → HeatEntry → EventHeat → EventRound → Event → CompetitionEntry → Competition
        eventResultRepository.deleteByCompetitionId(id);
        heatEntryRepository.deleteByCompetitionId(id);
        eventHeatRepository.deleteByCompetitionId(id);
        eventRoundRepository.deleteByCompetitionId(id);
        eventRepository.deleteByCompetitionId(id);
        competitionEntryRepository.deleteByCompetitionId(id);
        if (competition.getImagePath() != null) {
            deleteImageFile(competition.getImagePath());
        }
        competitionRepository.delete(competition);
    }
}
