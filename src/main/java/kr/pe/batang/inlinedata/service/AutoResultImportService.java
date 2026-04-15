package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.ResultImportFile;
import kr.pe.batang.inlinedata.entity.ResultImportSetting;
import kr.pe.batang.inlinedata.repository.CompetitionRepository;
import kr.pe.batang.inlinedata.repository.ResultImportFileRepository;
import kr.pe.batang.inlinedata.repository.ResultImportSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoResultImportService {

    private final ResultParsingService resultParsingService;
    private final ResultImportFileRepository resultImportFileRepository;
    private final ResultImportSettingRepository resultImportSettingRepository;
    private final CompetitionRepository competitionRepository;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.result-import.watch-dir:}")
    private String watchDir;

    @Value("${app.result-import.archive-dir:}")
    private String archiveDir;

    @Value("${app.result-import.error-dir:}")
    private String errorDir;

    @Value("${app.result-import.stable-wait-seconds:15}")
    private long stableWaitSeconds;

    public record ScanSummary(int scanned, int imported, int skipped, int failed, int results, int newEntries) {}

    public record AutoImportStatus(boolean autoScanEnabled, String watchDir, Long selectedCompetitionId,
                                   String selectedCompetitionName, long stableWaitSeconds) {}

    public AutoImportStatus getStatus() {
        ResultImportSetting setting = getOrCreateSetting();
        Long competitionId = setting.getCompetitionId();
        String competitionName = competitionId != null
                ? competitionRepository.findById(competitionId).map(c -> c.getShortName()).orElse(null)
                : null;
        return new AutoImportStatus(setting.isAutoScanEnabled(), watchDir, competitionId, competitionName, stableWaitSeconds);
    }

    public List<ResultImportFile> findRecentImports(Long competitionId) {
        if (competitionId == null) {
            return List.of();
        }
        return resultImportFileRepository.findTop20ByCompetitionIdOrderByCreatedAtDesc(competitionId);
    }

    @Transactional
    public void updateSetting(boolean autoScanEnabled, Long competitionId) {
        ResultImportSetting setting = getOrCreateSetting();
        if (competitionId != null && competitionRepository.findById(competitionId).isEmpty()) {
            throw new IllegalArgumentException("대회를 찾을 수 없습니다. id=" + competitionId);
        }
        if (autoScanEnabled && competitionId == null) {
            throw new IllegalArgumentException("자동 스캔을 사용하려면 대회를 선택해야 합니다.");
        }
        setting.update(autoScanEnabled, competitionId);
    }

    public ScanSummary scanUsingCurrentSetting() {
        ResultImportSetting setting = getOrCreateSetting();
        return scanAndImport(setting.getCompetitionId());
    }

    @Scheduled(fixedDelayString = "${app.result-import.polling-interval-ms:10000}")
    public void scheduledScan() {
        ResultImportSetting setting = getOrCreateSetting();
        if (!setting.isAutoScanEnabled()) {
            return;
        }
        Long competitionId = setting.getCompetitionId();
        if (competitionId == null || competitionId <= 0) {
            log.warn("자동 결과 등록 비활성 처리: 관리 페이지에서 대회를 선택해야 합니다.");
            return;
        }
        scanAndImport(competitionId);
    }

    @Transactional
    public ScanSummary scanAndImport(Long competitionId) {
        if (competitionId == null || competitionId <= 0) {
            return new ScanSummary(0, 0, 0, 0, 0, 0);
        }
        if (watchDir == null || watchDir.isBlank()) {
            log.warn("자동 결과 등록 경로가 비어 있습니다.");
            return new ScanSummary(0, 0, 0, 0, 0, 0);
        }
        if (!running.compareAndSet(false, true)) {
            log.info("자동 결과 등록이 이미 실행 중입니다.");
            return new ScanSummary(0, 0, 0, 0, 0, 0);
        }

        int scanned = 0;
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int results = 0;
        int newEntries = 0;

        try {
            for (Path path : findCandidateFiles()) {
                scanned++;
                ProcessOutcome outcome = importFile(path, competitionId);
                switch (outcome.status()) {
                    case "SUCCESS" -> {
                        imported++;
                        results += outcome.resultsCount();
                        newEntries += outcome.newEntriesCount();
                    }
                    case "SKIPPED" -> skipped++;
                    default -> failed++;
                }
            }
            return new ScanSummary(scanned, imported, skipped, failed, results, newEntries);
        } finally {
            running.set(false);
        }
    }

    @Transactional
    protected ProcessOutcome importFile(Path path, Long competitionId) {
        String fileName = path.getFileName().toString();
        try {
            if (!isStableFile(path)) {
                return new ProcessOutcome("SKIPPED", 0, 0, "업로드 중이거나 최근 수정된 파일");
            }

            FileTime lastModified = Files.getLastModifiedTime(path);
            long fileSize = Files.size(path);
            String fileHash = computeSha256(path);

            if (resultImportFileRepository.existsByCompetitionIdAndFileHash(competitionId, fileHash)) {
                moveIfConfigured(path, resolveArchivePath(fileName));
                return new ProcessOutcome("SKIPPED", 0, 0, "이미 처리한 파일");
            }

            ResultImportFile importFile = resultImportFileRepository.save(ResultImportFile.builder()
                    .competitionId(competitionId)
                    .fileName(fileName)
                    .filePath(path.toAbsolutePath().toString())
                    .fileHash(fileHash)
                    .fileSize(fileSize)
                    .sourceLastModifiedAt(toLocalDateTime(lastModified))
                    .status("PENDING")
                    .build());

            ResultParsingService.ImportResult result = resultParsingService.parseResultPdf(path, competitionId);
            if (result.filesProcessed() == 0 || result.results() == 0) {
                importFile.markSkipped("처리 대상 결과가 없거나 경기 매칭에 실패했습니다.");
                moveIfConfigured(path, resolveErrorPath(fileName));
                return new ProcessOutcome("SKIPPED", 0, 0, importFile.getMessage());
            }

            importFile.markSuccess(result.results(), result.newEntries(), null);
            moveIfConfigured(path, resolveArchivePath(fileName));
            log.info("자동 결과 등록 성공: {} -> 결과 {}건, 새 엔트리 {}건",
                    fileName, result.results(), result.newEntries());
            return new ProcessOutcome("SUCCESS", result.results(), result.newEntries(), null);
        } catch (Exception e) {
            String message = shortenMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            saveFailureRecord(path, competitionId, message);
            moveIfConfigured(path, resolveErrorPath(fileName));
            log.error("자동 결과 등록 실패: {} - {}", fileName, message, e);
            return new ProcessOutcome("FAILED", 0, 0, message);
        }
    }

    private void saveFailureRecord(Path path, Long competitionId, String message) {
        try {
            String fileHash = Files.exists(path) ? computeSha256(path) : "unavailable";
            if (resultImportFileRepository.existsByCompetitionIdAndFileHash(competitionId, fileHash)) {
                return;
            }
            long fileSize = Files.exists(path) ? Files.size(path) : 0L;
            LocalDateTime modifiedAt = Files.exists(path) ? toLocalDateTime(Files.getLastModifiedTime(path)) : null;
            ResultImportFile importFile = resultImportFileRepository.save(ResultImportFile.builder()
                    .competitionId(competitionId)
                    .fileName(path.getFileName().toString())
                    .filePath(path.toAbsolutePath().toString())
                    .fileHash(fileHash)
                    .fileSize(fileSize)
                    .sourceLastModifiedAt(modifiedAt)
                    .status("PENDING")
                    .build());
            importFile.markFailed(message);
        } catch (Exception saveEx) {
            log.warn("실패 이력 저장 실패: {} - {}", path, saveEx.getMessage());
        }
    }

    private List<Path> findCandidateFiles() {
        Path baseDir = Path.of(watchDir);
        if (!Files.isDirectory(baseDir)) {
            return List.of();
        }
        Path archiveBase = resolveConfiguredDir(archiveDir);
        Path errorBase = resolveConfiguredDir(errorDir);
        try (Stream<Path> stream = Files.walk(baseDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isManagedDirectoryFile(path, archiveBase))
                    .filter(path -> !isManagedDirectoryFile(path, errorBase))
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            log.error("자동 결과 등록 경로 조회 실패: {}", baseDir, e);
            return List.of();
        }
    }

    private boolean isStableFile(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) <= 0) {
            return false;
        }
        Instant threshold = Instant.now().minusSeconds(Math.max(stableWaitSeconds, 0));
        return Files.getLastModifiedTime(path).toInstant().isBefore(threshold);
    }

    private String computeSha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 해시를 사용할 수 없습니다.", e);
        }
    }

    private LocalDateTime toLocalDateTime(FileTime fileTime) {
        return LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
    }

    private Path resolveArchivePath(String fileName) {
        Path base = resolveConfiguredDir(archiveDir);
        if (base == null) {
            return null;
        }
        return base.resolve(fileName);
    }

    private Path resolveErrorPath(String fileName) {
        Path base = resolveConfiguredDir(errorDir);
        if (base == null) {
            return null;
        }
        return base.resolve(fileName);
    }

    private void moveIfConfigured(Path source, Path target) {
        if (target == null || !Files.exists(source)) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Path finalTarget = ensureUniqueTarget(target);
            Files.move(source, finalTarget, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("자동 결과 파일 이동 실패: {} -> {} ({})", source, target, e.getMessage());
        }
    }

    private Path ensureUniqueTarget(Path target) {
        if (!Files.exists(target)) {
            return target;
        }
        String original = target.getFileName().toString();
        int dotIndex = original.lastIndexOf('.');
        String base = dotIndex >= 0 ? original.substring(0, dotIndex) : original;
        String ext = dotIndex >= 0 ? original.substring(dotIndex) : "";
        return target.getParent().resolve(base + "-" + System.currentTimeMillis() + ext);
    }

    private String shortenMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private Path resolveConfiguredDir(String dir) {
        if (dir == null || dir.isBlank()) {
            return null;
        }
        return Path.of(dir).normalize().toAbsolutePath();
    }

    private boolean isManagedDirectoryFile(Path path, Path managedDir) {
        if (managedDir == null) {
            return false;
        }
        Path absolutePath = path.normalize().toAbsolutePath();
        return absolutePath.startsWith(managedDir);
    }

    protected record ProcessOutcome(String status, int resultsCount, int newEntriesCount, String message) {}

    private ResultImportSetting getOrCreateSetting() {
        return resultImportSettingRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> resultImportSettingRepository.save(ResultImportSetting.builder()
                        .autoScanEnabled(false)
                        .competitionId(null)
                        .build()));
    }
}
