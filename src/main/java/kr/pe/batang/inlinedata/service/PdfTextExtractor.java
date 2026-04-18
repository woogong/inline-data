package kr.pe.batang.inlinedata.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PdfTextExtractor {

    public String extractText(Path pdfPath) throws IOException {
        return run(pdfPath, "-layout");
    }

    public String extractTextRaw(Path pdfPath) throws IOException {
        return run(pdfPath, "-raw");
    }

    private String run(Path pdfPath, String mode) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("pdftotext", mode, pdfPath.toString(), "-");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            String text = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("pdftotext timed out for {}", pdfPath.getFileName());
                return null;
            }
            return text;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return null;
        }
    }
}