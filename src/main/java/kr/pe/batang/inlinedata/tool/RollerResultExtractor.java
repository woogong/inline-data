package kr.pe.batang.inlinedata.tool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * GIONS_HIS_RESULT_RECORD.csv에서 롤러 종목만 추출하여 별도 파일로 저장한다.
 * 종목 컬럼(2번째)이 "롤러", "로울러", "인라인롤러" 중 하나인 행만 추출.
 *
 * 실행: java -cp build/classes/java/main kr.pe.batang.inlinedata.tool.RollerResultExtractor
 */
public class RollerResultExtractor {

    public static void main(String[] args) throws IOException {
        Path inputPath = Paths.get("refs/GIONS_HIS_RESULT_RECORD.csv");
        Path outputPath = Paths.get("refs/roller_result_record.csv");

        Charset eucKr = Charset.forName("EUC-KR");

        int total = 0, extracted = 0;

        try (BufferedReader reader = Files.newBufferedReader(inputPath, eucKr);
             BufferedWriter writer = Files.newBufferedWriter(outputPath, java.nio.charset.StandardCharsets.UTF_8)) {

            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                total++;

                if (isHeader) {
                    writer.write(line);
                    writer.newLine();
                    isHeader = false;
                    continue;
                }

                String sport = extractField(line, 1);
                if (sport != null && (sport.contains("롤러") || sport.contains("로울러") || sport.contains("인라인"))) {
                    writer.write(line);
                    writer.newLine();
                    extracted++;
                }
            }
        }

        System.out.println("완료!");
        System.out.println("  입력: " + inputPath.toAbsolutePath());
        System.out.println("  출력: " + outputPath.toAbsolutePath());
        System.out.println("  전체: " + total + "행, 추출: " + extracted + "행");
    }

    /**
     * CSV 행에서 n번째(0-based) 필드를 추출한다. 큰따옴표로 감싸진 필드를 지원한다.
     */
    private static String extractField(String line, int fieldIndex) {
        int idx = 0;
        int fieldCount = 0;
        boolean inQuote = false;
        StringBuilder field = new StringBuilder();

        while (idx < line.length()) {
            char c = line.charAt(idx);

            if (inQuote) {
                if (c == '"') {
                    if (idx + 1 < line.length() && line.charAt(idx + 1) == '"') {
                        field.append('"');
                        idx += 2;
                    } else {
                        inQuote = false;
                        idx++;
                    }
                } else {
                    field.append(c);
                    idx++;
                }
            } else {
                if (c == '"') {
                    inQuote = true;
                    idx++;
                } else if (c == ',') {
                    if (fieldCount == fieldIndex) return field.toString();
                    fieldCount++;
                    field.setLength(0);
                    idx++;
                } else {
                    field.append(c);
                    idx++;
                }
            }
        }

        if (fieldCount == fieldIndex) return field.toString();
        return null;
    }
}

/*

													<option value="107">2026 제107회 전국체육대회</option>

													<option value="106">2025 제106회 전국체육대회</option>

													<option value="105">2024 제105회 전국체육대회</option>

													<option value="104">2023 제104회 전국체육대회</option>

													<option value="103">2022 제103회 전국체육대회</option>

													<option value="102">2021 제102회 전국체육대회</option>

													<option value="101">2020 제101회 전국체육대회</option>

													<option value="100">2019 제100회 전국체육대회</option>

													<option value="99">2018 제99회 전국체육대회</option>

													<option value="98">2017 제98회 전국체육대회</option>

													<option value="97">2016 제97회 전국체육대회</option>

													<option value="96">2015 제96회 전국체육대회</option>

													<option value="95">2014 제95회 전국체육대회</option>

													<option value="94">2013 제94회 전국체육대회</option>

													<option value="93">2012 제93회 전국체육대회</option>

													<option value="92">2011 제92회 전국체육대회</option>

													<option value="91">2010 제91회 전국체육대회</option>

													<option value="90">2009 제90회 전국체육대회</option>

													<option value="89">2008 제89회 전국체육대회</option>

													<option value="88">2007 제88회 전국체육대회</option>

													<option value="87">2006 제87회 전국체육대회</option>

													<option value="86">2005 제86회 전국체육대회</option>

													<option value="85">2004 제85회 전국체육대회</option>

													<option value="84">2003 제84회 전국체육대회</option>

													<option value="83">2002 제83회 전국체육대회</option>

													<option value="82">2001 제82회 전국체육대회</option>

													<option value="81">2000 제81회 전국체육대회</option>

													<option value="80">1999 제80회 전국체육대회</option>

													<option value="79">1998 제79회 전국체육대회</option>

													<option value="78">1997 제78회 전국체육대회</option>

													<option value="77">1996 제77회 전국체육대회</option>

													<option value="76">1995 제76회 전국체육대회</option>

													<option value="75">1994 제75회 전국체육대회</option>

													<option value="74">1993 제74회 전국체육대회</option>

													<option value="73">1992 제73회 전국체육대회</option>

													<option value="72">1991 제72회 전국체육대회</option>

													<option value="71">1990 제71회 전국체육대회</option>

													<option value="70">1989 제70회 전국체육대회</option>

													<option value="69">1988 제69회 전국체육대회</option>

													<option value="68">1987 제68회 전국체육대회</option>

													<option value="67">1986 제67회 전국체육대회</option>

													<option value="66">1985 제66회 전국체육대회</option>

													<option value="65">1984 제65회 전국체육대회</option>

													<option value="64" selected="selected">1983 제64회 전국체육대회</option>


 */