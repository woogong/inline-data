package kr.pe.batang.inlinedata.service;

import kr.pe.batang.inlinedata.entity.ResultImportFile;
import kr.pe.batang.inlinedata.entity.ResultImportSetting;
import kr.pe.batang.inlinedata.repository.CompetitionRepository;
import kr.pe.batang.inlinedata.repository.ResultImportFileRepository;
import kr.pe.batang.inlinedata.repository.ResultImportSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    /**
     * Spring 프록시를 통한 self 참조. {@link #importFile}를 파일마다 독립된 트랜잭션으로 실행하기 위해
     * 직접 this.importFile(...)을 호출하지 않고 프록시 경유로 호출한다.
     * (this.* 호출은 Spring AOP 프록시를 우회해서 @Transactional이 먹히지 않음)
     */
    @Autowired
    @Lazy
    private AutoResultImportService self;

    @Value("${app.result-import.watch-dir:}")
    private String watchDir;

    @Value("${app.result-import.archive-dir:}")
    private String archiveDir;

    @Value("${app.result-import.error-dir:}")
    private String errorDir;

    @Value("${app.result-import.stable-wait-seconds:15}")
    private long stableWaitSeconds;

    /**
     * OneDrive에서 다운로드한 파일을 임시 저장해 파싱하는 staging 디렉토리.
     * 비어있으면 ${java.io.tmpdir}/inline-staging 사용.
     */
    @Value("${app.result-import.staging-dir:}")
    private String stagingDir;

    /** Stage 1 (OneDrive → staging 복사) 파일당 타임아웃 (초). */
    @Value("${app.result-import.copy-timeout-seconds:60}")
    private long copyTimeoutSeconds;

    /** Stage 1 병렬 다운로드 스레드 수. I/O 바운드라 CPU 수와 무관하게 늘려도 됨. */
    @Value("${app.result-import.parallel-downloads:4}")
    private int parallelDownloads;

    /** 다운로드 전용 스레드 풀. lazy 초기화해 단위 테스트에서도 동작하도록 함. */
    private volatile ExecutorService downloadExecutor;

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

    /**
     * 이 메서드 자체에는 트랜잭션을 걸지 않는다. 파일 하나당 별도 트랜잭션을 {@link #importFile} 호출마다
     * 열도록 self 프록시를 경유해 호출한다. 이렇게 하면 550개 같은 대량 스캔에서도 Hibernate 세션이
     * 파일 단위로 닫혀 메모리/락 점유 시간이 제한된다.
     */
    public ScanSummary scanAndImport(Long competitionId) {
        if (competitionId == null || competitionId <= 0) {
            return new ScanSummary(0, 0, 0, 0, 0, 0);
        }
        if (watchDir == null || watchDir.isBlank()) {
            log.warn("자동 결과 등록 경로가 비어 있습니다.");
            return new ScanSummary(0, 0, 0, 0, 0, 0);
        }
        if (!running.compareAndSet(false, true)) {
            log.info("자동 결과 등록이 이미 실행 중입니다. 이번 호출은 건너뜁니다.");
            return new ScanSummary(0, 0, 0, 0, 0, 0);
        }

        long startedAt = System.currentTimeMillis();
        int scanned = 0;
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int results = 0;
        int newEntries = 0;

        try {
            List<Path> candidates = findCandidateFiles();

            // 이미 처리된 파일은 hash 계산/TX 진입 전에 (fileName, fileSize) 매칭으로 제외.
            // OneDrive 등으로 move가 차단되어 원본이 watch-dir에 남아있을 때 매 스캔마다
            // 550개를 반복 처리하는 것처럼 보이는 현상을 막는다.
            Set<String> processedKeys = Set.copyOf(
                    resultImportFileRepository.findProcessedFileKeys(competitionId));
            List<Path> newCandidates = new ArrayList<>();
            int preSkipped = 0;
            for (Path path : candidates) {
                if (isAlreadyProcessed(path, processedKeys)) {
                    preSkipped++;
                    // 이전 스캔에서 이동 실패한 파일은 archive로 재이동 시도.
                    moveIfConfigured(path, resolveArchivePath(path.getFileName().toString()));
                } else {
                    newCandidates.add(path);
                }
            }
            // === Stage 1: OneDrive → staging 병렬 다운로드 ===
            // 이전 크래시로 남은 staging 파일을 먼저 정리.
            cleanStagingOrphans();
            List<DownloadResult> downloads = downloadToStaging(newCandidates);
            int downloadFailed = 0;
            int downloadOk = 0;
            for (DownloadResult dr : downloads) {
                if (dr.error() != null) {
                    downloadFailed++;
                    log.warn("다운로드 실패 (다음 스캔 재시도): {} — {}",
                            dr.source().getFileName(), dr.error());
                } else {
                    downloadOk++;
                }
            }
            log.info("자동 결과 스캔 시작: competitionId={} watchDir={} 후보 {}건 (이미 처리 {}건 스킵, 신규 {}건 — 다운로드 성공 {}, 실패 {})",
                    competitionId, watchDir, candidates.size(), preSkipped, newCandidates.size(), downloadOk, downloadFailed);

            // === Stage 2: staging 파일을 각자 독립 TX로 처리 ===
            int processIdx = 0;
            for (DownloadResult dr : downloads) {
                if (dr.error() != null) continue;
                processIdx++;
                log.info("파일 처리 시작 [{}/{}]: {}", processIdx, downloadOk, dr.source().getFileName());
                scanned++;
                try {
                    ProcessOutcome outcome = self.importFile(dr.source(), dr.staged(), competitionId);
                    switch (outcome.status()) {
                        case "SUCCESS" -> {
                            imported++;
                            results += outcome.resultsCount();
                            newEntries += outcome.newEntriesCount();
                        }
                        case "SKIPPED" -> skipped++;
                        default -> failed++;
                    }
                } finally {
                    try { Files.deleteIfExists(dr.staged()); } catch (IOException ignore) {}
                }
            }
            long elapsed = System.currentTimeMillis() - startedAt;
            log.info("자동 결과 스캔 종료: {}ms / 처리 {}건 / 성공 {} / 스킵 {} / 실패 {} / 결과 {} / 새 엔트리 {} (pre-스킵 {}, 다운로드 실패 {})",
                    elapsed, scanned, imported, skipped, failed, results, newEntries, preSkipped, downloadFailed);
            return new ScanSummary(scanned, imported, skipped, failed, results, newEntries);
        } finally {
            running.set(false);
        }
    }

    /** Stage 1 결과. 성공이면 staged != null, error == null. 실패면 반대. */
    protected record DownloadResult(Path source, Path staged, String error) {}

    /** OneDrive watch-dir의 파일들을 staging으로 병렬 복사. 각 파일 {@link #copyTimeoutSeconds}초 타임아웃. */
    private List<DownloadResult> downloadToStaging(List<Path> sources) {
        if (sources.isEmpty()) return List.of();
        Path staging = resolveStagingDir();
        try {
            Files.createDirectories(staging);
        } catch (IOException e) {
            log.error("staging-dir 생성 실패: {} — {}", staging, e.getMessage());
            return sources.stream()
                    .map(s -> new DownloadResult(s, null, "staging 생성 실패: " + e.getMessage()))
                    .toList();
        }

        ExecutorService exec = getDownloadExecutor();
        List<CompletableFuture<DownloadResult>> futures = new ArrayList<>(sources.size());
        for (Path source : sources) {
            Path staged = staging.resolve(source.getFileName().toString());
            CompletableFuture<DownloadResult> f = CompletableFuture
                    .supplyAsync(() -> copyOne(source, staged), exec)
                    // get()으로 기다리면서 타임아웃 걸어 메인 스레드가 영영 블록되지 않게 함.
                    // supplyAsync 내부 스레드는 계속 동작할 수 있으나 staging 파일은 다음 cleanStagingOrphans에서 정리.
                    .orTimeout(copyTimeoutSeconds, TimeUnit.SECONDS)
                    .exceptionally(ex -> new DownloadResult(source, null,
                            "타임아웃 또는 복사 실패: " + firstLine(ex.getMessage())));
            futures.add(f);
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private DownloadResult copyOne(Path source, Path staged) {
        try {
            Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
            return new DownloadResult(source, staged, null);
        } catch (IOException e) {
            try { Files.deleteIfExists(staged); } catch (IOException ignore) {}
            return new DownloadResult(source, null, e.getMessage());
        }
    }

    private Path resolveStagingDir() {
        if (stagingDir == null || stagingDir.isBlank()) {
            return Path.of(System.getProperty("java.io.tmpdir"), "inline-staging");
        }
        return Path.of(stagingDir).toAbsolutePath();
    }

    /** staging 디렉토리 내 잔여 파일 일괄 삭제. 이전 스캔이 비정상 종료했을 때 방어. */
    private void cleanStagingOrphans() {
        Path staging = resolveStagingDir();
        if (!Files.isDirectory(staging)) return;
        try (Stream<Path> stream = Files.list(staging)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignore) {}
            });
        } catch (IOException e) {
            log.warn("staging-dir 정리 실패: {} — {}", staging, e.getMessage());
        }
    }

    /** 데몬 스레드 기반 고정 풀. lazy 초기화로 테스트 환경에서도 동작. */
    private ExecutorService getDownloadExecutor() {
        ExecutorService exec = downloadExecutor;
        if (exec == null) {
            synchronized (this) {
                exec = downloadExecutor;
                if (exec == null) {
                    int size = parallelDownloads > 0 ? parallelDownloads : 4;
                    exec = Executors.newFixedThreadPool(size, r -> {
                        Thread t = new Thread(r, "inline-dl");
                        t.setDaemon(true);
                        return t;
                    });
                    downloadExecutor = exec;
                }
            }
        }
        return exec;
    }

    private String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    /**
     * hash 계산 없이 (fileName, fileSize) 매칭으로 이미 처리된 파일인지 확인.
     * 파일명과 크기가 동일한 PENDING 아닌 기록이 있으면 true.
     */
    private boolean isAlreadyProcessed(Path path, Set<String> processedKeys) {
        try {
            String key = path.getFileName().toString() + "|" + Files.size(path);
            return processedKeys.contains(key);
        } catch (IOException e) {
            return false;  // I/O 오류 시 fallback — importFile의 hash 기반 dedupe가 재처리 방지
        }
    }

    /**
     * 단일 파일 임포트. public인 이유는 {@link #self} 프록시 경유 호출 대상이기 때문.
     * 이 메서드 호출마다 새 TX가 열려 Hibernate 세션 캐시가 파일 단위로 해제된다.
     *
     * @param source OneDrive/원본 경로. 이동(archive/error) 대상이며 DB {@code file_path}에 기록됨.
     * @param staged Stage 1에서 로컬로 복사된 파일. 해시/pdftotext 모든 read가 이 경로에서 이뤄짐.
     */
    @Transactional
    public ProcessOutcome importFile(Path source, Path staged, Long competitionId) {
        String fileName = source.getFileName().toString();
        ResultImportFile record = null;
        try {
            // isStableFile은 원본의 mtime을 보므로 source를 그대로 사용 (메타데이터 read는 가벼움).
            if (!isStableFile(source)) {
                return new ProcessOutcome("SKIPPED", 0, 0, "업로드 중이거나 최근 수정된 파일");
            }

            // 파일 내용 기반 연산은 staging 복사본으로. OneDrive on-demand 다운로드/eviction에 영향 안 받음.
            FileTime lastModified = Files.getLastModifiedTime(staged);
            long fileSize = Files.size(staged);
            String fileHash = computeSha256(staged);

            ResultImportFile existing = resultImportFileRepository
                    .findByCompetitionIdAndFileHash(competitionId, fileHash).orElse(null);
            if (existing != null && !"PENDING".equals(existing.getStatus())) {
                moveIfConfigured(source, resolveArchivePath(fileName));
                return new ProcessOutcome("SKIPPED", 0, 0, "이미 처리한 파일");
            }

            record = existing != null ? existing : resultImportFileRepository.save(ResultImportFile.builder()
                    .competitionId(competitionId)
                    .fileName(fileName)
                    .filePath(source.toAbsolutePath().toString())
                    .fileHash(fileHash)
                    .fileSize(fileSize)
                    .sourceLastModifiedAt(toLocalDateTime(lastModified))
                    .status("PENDING")
                    .build());

            // 파싱도 staging 복사본에서. pdftotext 실행 중 OneDrive가 파일을 evict해
            // 중간 바이트 잘림 같은 불안정을 원천 차단.
            ResultParsingService.ImportResult result = resultParsingService.parseResultPdf(
                    staged, competitionId, kr.pe.batang.inlinedata.entity.ResultSource.AUTO);
            if (result.filesProcessed() == 0 || result.results() == 0) {
                record.markSkipped("처리 대상 결과가 없거나 경기 매칭에 실패했습니다.");
                resultImportFileRepository.save(record);
                moveIfConfigured(source, resolveErrorPath(fileName));
                return new ProcessOutcome("SKIPPED", 0, 0, record.getMessage());
            }

            record.markSuccess(result.results(), result.newEntries(), null);
            resultImportFileRepository.save(record);
            moveIfConfigured(source, resolveArchivePath(fileName));
            log.info("자동 결과 등록 성공: {} -> 결과 {}건, 새 엔트리 {}건",
                    fileName, result.results(), result.newEntries());
            return new ProcessOutcome("SUCCESS", result.results(), result.newEntries(), null);
        } catch (Exception e) {
            String message = shortenMessage(describeFailure(e));
            if (record != null) {
                record.markFailed(message);
                resultImportFileRepository.save(record);
            } else {
                // saveFailureRecord의 해시 계산도 staging 복사본 기준. source는 이미 못 읽을 수 있음.
                saveFailureRecord(source, staged, competitionId, message);
            }
            moveIfConfigured(source, resolveErrorPath(fileName));
            // 마지막 인자로 넘기는 예외는 SLF4J가 풀 스택트레이스로 로깅한다.
            log.error("자동 결과 등록 실패: {} - {}", fileName, message, e);
            return new ProcessOutcome("FAILED", 0, 0, message);
        }
    }

    /**
     * 예외를 "클래스: 메시지 ← 원인클래스: 원인메시지 ← ..." 형태로 정리.
     * DB message 컬럼과 로그 단일 라인 모두에 담기 위한 요약 문자열.
     * 원인 체인은 무한루프 방지를 위해 최대 4단계까지만 따라간다.
     */
    private String describeFailure(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 4) {
            if (depth > 0) sb.append(" ← ");
            sb.append(current.getClass().getSimpleName());
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                sb.append(": ").append(current.getMessage());
            }
            if (current.getCause() == current) break;  // self-reference 가드
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }

    /**
     * 실패 이력 저장. 해시/크기/mtime은 staging 복사본에서 읽는다 (source는 OneDrive 미동기화로
     * 접근 불가일 수 있음). source는 DB {@code file_path}와 파일명 표시에만 사용.
     */
    private void saveFailureRecord(Path source, Path staged, Long competitionId, String message) {
        try {
            String fileHash = (staged != null && Files.exists(staged)) ? computeSha256(staged) : "unavailable";
            if (!"unavailable".equals(fileHash)
                    && resultImportFileRepository.existsByCompetitionIdAndFileHash(competitionId, fileHash)) {
                return;
            }
            long fileSize = (staged != null && Files.exists(staged)) ? Files.size(staged) : 0L;
            LocalDateTime modifiedAt = (staged != null && Files.exists(staged))
                    ? toLocalDateTime(Files.getLastModifiedTime(staged)) : null;
            resultImportFileRepository.save(ResultImportFile.builder()
                    .competitionId(competitionId)
                    .fileName(source.getFileName().toString())
                    .filePath(source.toAbsolutePath().toString())
                    .fileHash(fileHash)
                    .fileSize(fileSize)
                    .sourceLastModifiedAt(modifiedAt)
                    .status("FAILED")
                    .message(message)
                    .processedAt(LocalDateTime.now())
                    .build());
        } catch (Exception saveEx) {
            log.warn("실패 이력 저장 실패: {} - {}", source, saveEx.getMessage());
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
            try {
                Files.move(source, finalTarget, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveEx) {
                // OneDrive 등 클라우드 동기화 폴더에서는 rename이 차단될 수 있으므로 copy 후 삭제 시도
                Files.copy(source, finalTarget, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.deleteIfExists(source);
                } catch (IOException deleteEx) {
                    log.debug("원본 파일 삭제 실패 (해시 기반 중복 체크로 재처리 방지됨): {}", source);
                }
            }
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
