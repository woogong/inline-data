package kr.pe.batang.inlinedata.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
     * pdftotext를 실행해 stdout을 문자열로 반환.
     *
     * 동작 순서:
     * 1. 파일 앞 5바이트가 "%PDF-"인지 확인. 아니면 즉시 IOException (PDF가 아니거나 손상).
     * 2. pdftotext 실행. stdout과 stderr를 분리해 별도 스레드에서 읽는다
     *    (이전엔 redirectErrorStream(true)로 합쳤는데, 이 경우 손상된 PDF에서 나오는
     *    "Syntax Error: Couldn't find trailer dictionary" 같은 메시지가 본문에 섞여
     *    파서가 PDF 텍스트로 오해하고 그냥 0건으로 SKIPPED 처리하는 문제 발생).
     * 3. stdout 읽기는 {@link CompletableFuture#get(long, TimeUnit)}로 30초 타임아웃.
     *    타임아웃 시 {@link Process#destroyForcibly()}로 자식 프로세스를 죽인다.
     * 4. stderr에 "Syntax Error:"가 감지되면 파일 손상으로 판단하고 IOException을 던진다.
     *    (Syntax Warning은 단순 경고라 무시.)
     */
    private String run(Path pdfPath, String mode) throws IOException {
        checkPdfMagic(pdfPath);

        ProcessBuilder pb = new ProcessBuilder("pdftotext", mode, pdfPath.toString(), "-");
        Process process = pb.start();

        CompletableFuture<byte[]> stdoutReader = readAsync(process.getInputStream());
        CompletableFuture<byte[]> stderrReader = readAsync(process.getErrorStream());

        try {
            byte[] stdoutBytes = stdoutReader.get(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            byte[] stderrBytes = stderrReader.get(5, TimeUnit.SECONDS);
            process.waitFor(5, TimeUnit.SECONDS);

            String stderr = new String(stderrBytes, StandardCharsets.UTF_8).trim();
            if (stderr.contains("Syntax Error:")) {
                throw new IOException("pdftotext " + mode + " 구문 오류 (손상된 PDF): "
                        + pdfPath.getFileName() + " — " + firstLine(stderr));
            }
            return new String(stdoutBytes, StandardCharsets.UTF_8);
        } catch (TimeoutException e) {
            process.destroyForcibly();
            stdoutReader.cancel(true);
            stderrReader.cancel(true);
            throw new IOException("pdftotext " + mode + " 모드 타임아웃 (" + PROCESS_TIMEOUT_SECONDS + "초 초과): "
                    + pdfPath.getFileName());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            stdoutReader.cancel(true);
            stderrReader.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("pdftotext " + mode + " 실행 중 인터럽트: " + pdfPath.getFileName(), e);
        } catch (ExecutionException e) {
            process.destroyForcibly();
            throw new IOException("pdftotext " + mode + " 스트림 읽기 실패: " + pdfPath.getFileName(), e.getCause());
        }
    }

    /**
     * PDF 파일 시그니처 확인. %PDF-로 시작하지 않으면 손상/비 PDF.
     * 읽기 자체가 실패(OneDrive 클라우드 전용 파일, 권한 등)하면 그 사유를 별도로 래핑.
     */
    private void checkPdfMagic(Path pdfPath) throws IOException {
        byte[] magic;
        try (InputStream fis = Files.newInputStream(pdfPath)) {
            magic = fis.readNBytes(5);
        } catch (IOException e) {
            throw new IOException("PDF 파일 읽기 실패 (OneDrive 미동기화 또는 접근 불가): "
                    + pdfPath.getFileName() + " — " + e.getMessage(), e);
        }
        if (magic.length < 5 || !"%PDF-".equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new IOException("PDF 헤더(%PDF-) 없음. PDF가 아니거나 손상되었습니다: "
                    + pdfPath.getFileName());
        }
    }

    private CompletableFuture<byte[]> readAsync(InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream s = in) {
                return s.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}