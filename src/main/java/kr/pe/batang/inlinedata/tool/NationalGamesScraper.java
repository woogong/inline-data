package kr.pe.batang.inlinedata.tool;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * roller_result_record.csv의 URL을 기반으로 meet.sports.or.kr에서
 * 역대 전국체육대회 롤러 경기 상세 결과를 수집하여 DB에 저장한다.
 *
 * 실행: ./gradlew compileJava && java -cp build/classes/java/main:build/libs/* kr.pe.batang.inlinedata.tool.NationalGamesScraper
 */
public class NationalGamesScraper {

    private static final String DETAIL_URL = "https://meet.sports.or.kr/history/schedule/scheduleDetailR.do";
    private static final int DELAY_MS = 400;

    private static final String DB_URL = "jdbc:mariadb://localhost:3306/inlinedata?useUnicode=true&characterEncoding=UTF-8";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "parksj11";

    public static void main(String[] args) throws Exception {
        disableSslVerification();

        // 1. CSV에서 고유 파라미터 조합 추출
        List<ScrapeTarget> targets = extractTargets();
        System.out.println("수집 대상: " + targets.size() + "건");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);

            // 2. gameno → competition_id 매핑
            Map<String, Long> compMap = loadCompetitionMap(conn);
            System.out.println("대회 매핑: " + compMap.size() + "건");

            int done = 0, skipped = 0, errors = 0;
            int totalResults = 0;

            for (ScrapeTarget target : targets) {
                Long compId = compMap.get(target.gameno);
                if (compId == null) {
                    if (skipped == 0) System.out.println("  대회 매핑 없음: gameno=" + target.gameno);
                    skipped++;
                    continue;
                }

                // 이미 저장된 데이터가 있으면 스킵
                if (hasExistingData(conn, compId, target)) {
                    skipped++;
                    continue;
                }

                try {
                    List<ResultRow> results = fetchResults(target);
                    if (!results.isEmpty()) {
                        saveResults(conn, compId, target, results);
                        conn.commit();
                        totalResults += results.size();
                    }
                    done++;
                    if (done % 50 == 0) {
                        System.out.printf("  진행: %d/%d (스킵 %d, 오류 %d, 결과 %d건)%n",
                                done, targets.size(), skipped, errors, totalResults);
                    }
                } catch (Exception e) {
                    errors++;
                    System.err.println("  오류: " + target + " - " + e.getMessage());
                    conn.rollback();
                }

                Thread.sleep(DELAY_MS);
            }

            System.out.printf("%n완료! 처리: %d, 스킵: %d, 오류: %d, 결과: %d건%n",
                    done, skipped, errors, totalResults);
        }
    }

    // --- CSV 파싱 ---

    record ScrapeTarget(String gameno, String kindCd, String detailClassCd, String rhCd,
                        String division, String eventName, String roundName) {
        @Override
        public String toString() {
            return "gameno=" + gameno + " " + division + " " + eventName + " " + roundName;
        }
    }

    private static List<ScrapeTarget> extractTargets() throws IOException {
        Path csvPath = Paths.get("refs/roller_result_record.csv");
        Set<String> seen = new LinkedHashSet<>();
        List<ScrapeTarget> targets = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) { isHeader = false; continue; }

                String[] fields = parseCsvLine(line);
                if (fields.length <= 9 || fields[9].isBlank()) continue;

                String url = fields[9];
                Map<String, String> params = parseQueryParams(url);

                String gameno = params.getOrDefault("searchGameno", params.getOrDefault("gameno", ""));
                String kindCd = params.getOrDefault("searchKindCd", params.getOrDefault("kindCd", ""));
                String detailClassCd = params.getOrDefault("searchDetailClassCd", params.getOrDefault("detailClassCd", ""));
                String rhCd = params.getOrDefault("rhCd", "");

                String key = gameno + "|" + kindCd + "|" + detailClassCd + "|" + rhCd;
                if (seen.contains(key)) continue;
                seen.add(key);

                String division = fields[2];   // 종별
                String eventName = fields[3];  // 세부종목
                String roundName = fields[4];  // 경기구분

                targets.add(new ScrapeTarget(gameno, kindCd, detailClassCd, rhCd,
                        division, eventName, roundName));
            }
        }
        return targets;
    }

    // --- 웹 스크래핑 ---

    record ResultRow(int ranking, String region, String athleteName, String playerId,
                     String teamName, String record, String note) {}

    private static List<ResultRow> fetchResults(ScrapeTarget target) throws IOException {
        String url = DETAIL_URL + "?searchClassCd=30&searchGubun=G"
                + "&searchGameno=" + target.gameno
                + "&searchKindCd=" + target.kindCd
                + "&searchDetailClassCd=" + target.detailClassCd
                + "&searchRhCd=" + target.rhCd
                + "&searchGmOrd=";

        Document doc = Jsoup.connect(url)
                .timeout(15000)
                .get();

        List<ResultRow> results = new ArrayList<>();
        Elements rows = doc.select("table.pcView tbody tr");

        for (Element row : rows) {
            Elements tds = row.select("td");
            if (tds.size() < 5) continue;

            String rankStr = tds.get(0).text().trim();
            int ranking;
            try { ranking = Integer.parseInt(rankStr); }
            catch (NumberFormatException e) { continue; }

            String region = tds.get(1).text().trim();

            // 선수명과 선수ID
            Element nameSpan = tds.get(2).selectFirst("span[onclick]");
            String athleteName;
            String playerId = null;
            if (nameSpan != null) {
                athleteName = nameSpan.text().trim();
                String onclick = nameSpan.attr("onclick");
                Matcher m = Pattern.compile("'(\\d+)'\\);?$").matcher(onclick);
                if (m.find()) playerId = m.group(1);
            } else {
                athleteName = tds.get(2).text().trim();
            }

            String teamName = tds.get(3).text().trim();
            String record = tds.get(4).text().trim();
            String note = tds.size() > 5 ? tds.get(5).text().trim() : "";

            if (!athleteName.isEmpty()) {
                results.add(new ResultRow(ranking, region, athleteName, playerId, teamName, record, note));
            }
        }
        return results;
    }

    // --- DB 저장 ---

    private static Map<String, Long> loadCompetitionMap(Connection conn) throws SQLException {
        Map<String, Long> map = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, edition FROM competition WHERE edition IS NOT NULL")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(String.valueOf(rs.getInt("edition")), rs.getLong("id"));
            }
        }
        return map;
    }

    private static boolean hasExistingData(Connection conn, Long compId, ScrapeTarget target) throws SQLException {
        // 같은 대회 + 종별 + 종목명으로 이벤트가 이미 있고, 결과도 있으면 스킵
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT e.id FROM event e " +
                "JOIN event_round er ON er.event_id = e.id " +
                "JOIN event_heat eh ON eh.event_round_id = er.id " +
                "JOIN heat_entry he ON he.heat_id = eh.id " +
                "JOIN event_result evr ON evr.heat_entry_id = he.id " +
                "WHERE e.competition_id = ? AND e.division_name = ? AND e.event_name = ? AND er.round = ? " +
                "LIMIT 1")) {
            ps.setLong(1, compId);
            ps.setString(2, target.division);
            ps.setString(3, target.eventName);
            ps.setString(4, target.roundName);
            return ps.executeQuery().next();
        }
    }

    private static void saveResults(Connection conn, Long compId, ScrapeTarget target,
                                    List<ResultRow> results) throws SQLException {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String gender = inferGender(target.division);
        boolean teamEvent = target.eventName.contains("계주") || target.eventName.contains("릴레이");

        // 1. Event
        long eventId = findOrCreateEvent(conn, compId, target.division, gender, target.eventName, teamEvent, now);

        // 2. EventRound
        long roundId = findOrCreateRound(conn, eventId, target.roundName, now);

        // 3. EventHeat (rhCd로 heat 번호 결정)
        int heatNumber = parseHeatNumber(target.rhCd, target.roundName);
        long heatId = findOrCreateHeat(conn, roundId, heatNumber, now);

        // 4. 각 결과 저장
        for (ResultRow r : results) {
            // CompetitionEntry
            long entryId = findOrCreateEntry(conn, compId, r.athleteName, gender, r.region, r.teamName, now);

            // HeatEntry
            long heatEntryId = findOrCreateHeatEntry(conn, heatId, entryId, 0);

            // EventResult
            createResultIfNotExists(conn, heatEntryId, r.ranking, r.record, r.note, now);
        }
    }

    private static String inferGender(String division) {
        if (division.startsWith("여") || division.contains("여자")) return "F";
        return "M";
    }

    private static int parseHeatNumber(String rhCd, String roundName) {
        // 309000 = 결승, 309090 = ?, 301001 = 예선1조, 301002 = 예선2조, ...
        // 307001 = 준결승1조, 308001 = 준준결승1조
        if (rhCd.startsWith("309")) return 0; // 결승
        if (rhCd.length() >= 6) {
            try {
                int lastDigit = Integer.parseInt(rhCd.substring(rhCd.length() - 1));
                // If roundName contains 조 number, extract it
                Matcher m = Pattern.compile("(\\d+)조").matcher(roundName);
                if (m.find()) return Integer.parseInt(m.group(1));
                return lastDigit > 0 ? lastDigit : 0;
            } catch (NumberFormatException e) { /* fall through */ }
        }
        return 0;
    }

    private static long findOrCreateEvent(Connection conn, long compId, String division,
                                          String gender, String eventName, boolean teamEvent,
                                          Timestamp now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM event WHERE competition_id = ? AND division_name = ? AND event_name = ?")) {
            ps.setLong(1, compId);
            ps.setString(2, division);
            ps.setString(3, eventName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("id");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO event (competition_id, division_name, gender, event_name, team_event, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, compId);
            ps.setString(2, division);
            ps.setString(3, gender);
            ps.setString(4, eventName);
            ps.setBoolean(5, teamEvent);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long findOrCreateRound(Connection conn, long eventId, String round,
                                          Timestamp now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM event_round WHERE event_id = ? AND round = ?")) {
            ps.setLong(1, eventId);
            ps.setString(2, round);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("id");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO event_round (event_id, round, created_at, updated_at) VALUES (?,?,?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, eventId);
            ps.setString(2, round);
            ps.setTimestamp(3, now);
            ps.setTimestamp(4, now);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long findOrCreateHeat(Connection conn, long roundId, int heatNumber,
                                         Timestamp now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM event_heat WHERE event_round_id = ? AND heat_number = ?")) {
            ps.setLong(1, roundId);
            ps.setInt(2, heatNumber);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("id");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO event_heat (event_round_id, heat_number, created_at) VALUES (?,?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, roundId);
            ps.setInt(2, heatNumber);
            ps.setTimestamp(3, now);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long findOrCreateEntry(Connection conn, long compId, String athleteName,
                                          String gender, String region, String teamName,
                                          Timestamp now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM competition_entry WHERE competition_id = ? AND athlete_name = ? AND gender = ? AND team_name = ?")) {
            ps.setLong(1, compId);
            ps.setString(2, athleteName);
            ps.setString(3, gender);
            ps.setString(4, teamName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("id");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO competition_entry (competition_id, athlete_name, gender, region, team_name, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, compId);
            ps.setString(2, athleteName);
            ps.setString(3, gender);
            ps.setString(4, region);
            ps.setString(5, teamName);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long findOrCreateHeatEntry(Connection conn, long heatId, long entryId,
                                              int bibNumber) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM heat_entry WHERE heat_id = ? AND entry_id = ?")) {
            ps.setLong(1, heatId);
            ps.setLong(2, entryId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("id");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO heat_entry (heat_id, entry_id, bib_number) VALUES (?,?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, heatId);
            ps.setLong(2, entryId);
            ps.setInt(3, bibNumber);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void createResultIfNotExists(Connection conn, long heatEntryId,
                                                int ranking, String record, String note,
                                                Timestamp now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM event_result WHERE heat_entry_id = ?")) {
            ps.setLong(1, heatEntryId);
            if (ps.executeQuery().next()) return;
        }

        String newRecord = null;
        if (note != null && !note.isEmpty()) {
            if (note.contains("한국신")) newRecord = "한국신";
            else if (note.contains("대회신")) newRecord = "대회신";
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO event_result (heat_entry_id, ranking, record, new_record, note, created_at) VALUES (?,?,?,?,?,?)")) {
            ps.setLong(1, heatEntryId);
            ps.setInt(2, ranking);
            ps.setString(3, record.isEmpty() ? null : record);
            ps.setString(4, newRecord);
            ps.setString(5, note.isEmpty() ? null : note);
            ps.setTimestamp(6, now);
            ps.executeUpdate();
        }
    }

    // --- 유틸리티 ---

    private static Map<String, String> parseQueryParams(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        int qIdx = url.indexOf('?');
        if (qIdx < 0) return params;
        String query = url.substring(qIdx + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = pair.substring(0, eq);
                String val = pair.substring(eq + 1);
                params.put(key, val); // 마지막 값 사용
            }
        }
        return params;
    }

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuote = false;
        int idx = 0;

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
                if (c == '"') { inQuote = true; idx++; }
                else if (c == ',') { fields.add(field.toString()); field.setLength(0); idx++; }
                else { field.append(c); idx++; }
            }
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }

    private static void disableSslVerification() {
        try {
            TrustManager[] trustAll = { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("SSL 비활성화 실패", e);
        }
    }
}