package kr.pe.batang.inlinedata.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class PdfTextExtractor {

    /** pdftotext 한 번 실행의 최대 허용 시간. 이 시간 안에 stdout이 EOF에 도달하지 않으면 강제 종료한다. */
    private static final long PROCESS_TIMEOUT_SECONDS = 30L;

    public String extractText(Path pdfPath) throws IOException {
        return run(pdfPath, "-layout");
    }

    public String extractTextRaw(Path pdfPath) throws IOException {
        return run(pdfPath, "-raw");
    }

    /**
     * pdftotext를 실행하고 stdout을 읽어 문자열로 반환.
     *
     * 이전 구현은 {@code process.getInputStream().readAllBytes()}를 메인 스레드에서 호출해
     * pdftotext가 stdout을 닫기 전까지 블로킹되었고, 그 뒤에 오는 {@code waitFor(timeout)}은
     * 이미 데드락 상태라 타임아웃이 전혀 발동하지 않았다. 특정 PDF에서 pdftotext가
     * 무한루프에 빠지면 스캔 전체가 멈췄다.
     *
     * 이제는 stdout 읽기를 별도 스레드(공유 ForkJoinPool)에 위임하고
     * {@link CompletableFuture#get(long, TimeUnit)}로 타임아웃을 강제한다. 타임아웃 시
     * {@link Process#destroyForcibly()}로 자식 프로세스를 죽이면 파이프가 닫히고
     * 읽기 스레드도 자연스럽게 종료된다.
     */
    private String run(Path pdfPath, String mode) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("pdftotext", mode, pdfPath.toString(), "-");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
            try (InputStream in = process.getInputStream()) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        try {
            byte[] bytes = reader.get(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            process.waitFor(5, TimeUnit.SECONDS);  // stdout EOF 후 프로세스 종료 완료 대기 (짧게)
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (TimeoutException e) {
            process.destroyForcibly();
            reader.cancel(true);
            log.warn("pdftotext 타임아웃 ({}초 초과): {}", PROCESS_TIMEOUT_SECONDS, pdfPath.getFileName());
            return null;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            reader.cancel(true);
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            process.destroyForcibly();
            log.warn("pdftotext stdout 읽기 실패: {} - {}", pdfPath.getFileName(), e.getCause());
            return null;
        }
    }
}