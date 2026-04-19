package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.ResultImportFile;
import kr.pe.batang.inlinedata.entity.ResultImportSetting;
import kr.pe.batang.inlinedata.entity.ResultSource;
import kr.pe.batang.inlinedata.repository.CompetitionRepository;
import kr.pe.batang.inlinedata.repository.ResultImportFileRepository;
import kr.pe.batang.inlinedata.repository.ResultImportSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AutoResultImportServiceTest {

    @InjectMocks
    private AutoResultImportService autoResultImportService;

    @Mock
    private ResultParsingService resultParsingService;

    @Mock
    private ResultImportFileRepository resultImportFileRepository;

    @Mock
    private ResultImportSettingRepository resultImportSettingRepository;

    @Mock
    private CompetitionRepository competitionRepository;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("안정화된 PDF를 스캔해 자동 등록한다")
    void scanAndImportSuccess() throws Exception {
        Path watchDir = Files.createDirectory(tempDir.resolve("watch"));
        Path archiveDir = tempDir.resolve("archive");
        Path pdf = watchDir.resolve("1-result.pdf");
        Files.writeString(pdf, "dummy");
        Files.setLastModifiedTime(pdf, FileTime.from(Instant.now().minusSeconds(60)));

        configureService(watchDir, archiveDir, tempDir.resolve("error"), 10L);
        given(resultImportFileRepository.findByCompetitionIdAndFileHash(eq(10L), anyString())).willReturn(java.util.Optional.empty());
        given(resultImportFileRepository.save(any(ResultImportFile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(resultParsingService.parseResultPdf(any(Path.class), any(Long.class), eq(ResultSource.AUTO)))
                .willReturn(new ResultParsingService.ImportResult(3, 1, 1));

        AutoResultImportService.ScanSummary result = autoResultImportService.scanAndImport(10L);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.results()).isEqualTo(3);
        assertThat(result.newEntries()).isEqualTo(1);
        assertThat(Files.exists(archiveDir.resolve("1-result.pdf"))).isTrue();
        assertThat(Files.exists(pdf)).isFalse();
    }

    @Test
    @DisplayName("날짜별 하위 폴더의 PDF도 재귀 스캔한다")
    void scanNestedDirectory() throws Exception {
        Path watchDir = Files.createDirectory(tempDir.resolve("watch"));
        Path dayDir = Files.createDirectory(watchDir.resolve("1Day_T"));
        Path archiveDir = tempDir.resolve("archive");
        Path pdf = dayDir.resolve("nested-result.pdf");
        Files.writeString(pdf, "dummy");
        Files.setLastModifiedTime(pdf, FileTime.from(Instant.now().minusSeconds(60)));

        configureService(watchDir, archiveDir, tempDir.resolve("error"), 8L);
        given(resultImportFileRepository.findByCompetitionIdAndFileHash(eq(8L), anyString())).willReturn(java.util.Optional.empty());
        given(resultImportFileRepository.save(any(ResultImportFile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(resultParsingService.parseResultPdf(any(Path.class), eq(8L), eq(ResultSource.AUTO)))
                .willReturn(new ResultParsingService.ImportResult(2, 0, 1));

        AutoResultImportService.ScanSummary result = autoResultImportService.scanAndImport(8L);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(Files.exists(archiveDir.resolve("nested-result.pdf"))).isTrue();
        assertThat(Files.exists(pdf)).isFalse();
    }

    @Test
    @DisplayName("이미 처리한 파일은 스킵하고 파서를 호출하지 않는다")
    void skipAlreadyProcessedFile() throws Exception {
        Path watchDir = Files.createDirectory(tempDir.resolve("watch"));
        Path archiveDir = tempDir.resolve("archive");
        Path pdf = watchDir.resolve("2-result.pdf");
        Files.writeString(pdf, "dummy");
        Files.setLastModifiedTime(pdf, FileTime.from(Instant.now().minusSeconds(60)));

        configureService(watchDir, archiveDir, tempDir.resolve("error"), 3L);
        given(resultImportFileRepository.findByCompetitionIdAndFileHash(eq(3L), anyString())).willReturn(java.util.Optional.of(
                ResultImportFile.builder().competitionId(3L).fileName("2-result.pdf").filePath("/tmp/2-result.pdf")
                        .fileHash("abc").fileSize(5L).status("SUCCESS").build()));

        AutoResultImportService.ScanSummary result = autoResultImportService.scanAndImport(3L);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        then(resultParsingService).should(never()).parseResultPdf(any(Path.class), any(Long.class), eq(ResultSource.AUTO));
        assertThat(Files.exists(archiveDir.resolve("2-result.pdf"))).isTrue();
    }

    @Test
    @DisplayName("최근 수정된 파일은 업로드 중으로 보고 스킵한다")
    void skipUnstableFile() throws Exception {
        Path watchDir = Files.createDirectory(tempDir.resolve("watch"));
        Path pdf = watchDir.resolve("3-result.pdf");
        Files.writeString(pdf, "dummy");
        Files.setLastModifiedTime(pdf, FileTime.from(Instant.now()));

        configureService(watchDir, tempDir.resolve("archive"), tempDir.resolve("error"), 5L);

        AutoResultImportService.ScanSummary result = autoResultImportService.scanAndImport(5L);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        then(resultImportFileRepository).should(never()).save(any(ResultImportFile.class));
        then(resultParsingService).should(never()).parseResultPdf(any(Path.class), any(Long.class), eq(ResultSource.AUTO));
        assertThat(Files.exists(pdf)).isTrue();
    }

    @Test
    @DisplayName("최근 자동 등록 이력을 조회한다")
    void findRecentImports() {
        ResultImportFile item = ResultImportFile.builder()
                .competitionId(1L)
                .fileName("result.pdf")
                .filePath("/tmp/result.pdf")
                .fileHash("abc")
                .fileSize(10L)
                .status("SUCCESS")
                .build();
        given(resultImportFileRepository.findTop20ByCompetitionIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(item));

        assertThat(autoResultImportService.findRecentImports(1L)).hasSize(1);
    }

    @Test
    @DisplayName("현재 설정 기반으로 수동 스캔한다")
    void scanUsingCurrentSetting() throws Exception {
        Path watchDir = Files.createDirectory(tempDir.resolve("watch"));
        Path pdf = watchDir.resolve("current-setting.pdf");
        Files.writeString(pdf, "dummy");
        Files.setLastModifiedTime(pdf, FileTime.from(Instant.now().minusSeconds(60)));

        configureService(watchDir, tempDir.resolve("archive"), tempDir.resolve("error"), 7L);
        given(resultImportSettingRepository.findTopByOrderByIdAsc())
                .willReturn(java.util.Optional.of(ResultImportSetting.builder()
                        .autoScanEnabled(true)
                        .competitionId(7L)
                        .build()));
        given(resultImportFileRepository.findByCompetitionIdAndFileHash(eq(7L), anyString())).willReturn(java.util.Optional.empty());
        given(resultImportFileRepository.save(any(ResultImportFile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(resultParsingService.parseResultPdf(any(Path.class), eq(7L), eq(ResultSource.AUTO)))
                .willReturn(new ResultParsingService.ImportResult(1, 0, 1));

        AutoResultImportService.ScanSummary result = autoResultImportService.scanUsingCurrentSetting();

        assertThat(result.imported()).isEqualTo(1);
    }

    private void configureService(Path watchDir, Path archiveDir, Path errorDir, Long competitionId) {
        ReflectionTestUtils.setField(autoResultImportService, "watchDir", watchDir.toString());
        ReflectionTestUtils.setField(autoResultImportService, "archiveDir", archiveDir.toString());
        ReflectionTestUtils.setField(autoResultImportService, "errorDir", errorDir.toString());
        ReflectionTestUtils.setField(autoResultImportService, "stableWaitSeconds", 15L);
    }
}
