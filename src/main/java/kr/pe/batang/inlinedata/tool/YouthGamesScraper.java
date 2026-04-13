package kr.pe.batang.inlinedata.tool;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * meet.sports.or.kr에서 역대 전국소년체육대회 롤러 경기 결과를 수집하여 DB에 저장한다.
 * 계주 종목은 제외한다.
 *
 * 실행: ./gradlew compileJava && java -cp "build/classes/java/main:..." kr.pe.batang.inlinedata.tool.YouthGamesScraper
 */
public class YouthGamesScraper {

    private static final String REC_URL = "https://meet.sports.or.kr/history/schedule/rec.do";
    private static final String DETAIL_URL = "https://meet.sports.or.kr/history/schedule/scheduleDetailR.do";
    private static final String GUBUN = "Y";
    private static final String CLASS_CD = "27"; // 롤러 종목코드 (소년대회)
    private static final int DELAY_MS = 400;

    private static final String DB_URL = "jdbc:mariadb://localhost:3306/inlinedata?useUnicode=true&characterEncoding=UTF-8";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "parksj11";

    // 롤러 데이터가 있는 회차 목록
    private static final int[] EDITIONS = {
        28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
        41, 42, 43, 44, 45, 46, 47, 48, 51, 52, 53, 54
    };

    public static void main(String[] args) throws Exception {
        disableSslVerification();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);

            // 회차 → competition_id 매핑 (전국소년체육대회)
            Map<Integer, Long> compMap = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, edition FROM competition WHERE short_name = '전국소년체육대회' AND edition IS NOT NULL")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) compMap.put(rs.getInt("edition"), rs.getLong("id"));
            }
            System.out.println("대회 매핑: " + compMap.size() + "건");

            int totalDone = 0, totalResults = 0, totalErrors = 0;

            for (int edition : EDITIONS) {
                Long compId = compMap.get(edition);
                if (compId == null) {
                    System.out.println("  대회 매핑 없음: " + edition + "회");
                    continue;
                }

                System.out.printf("=== 제%d회 전국소년체육대회 (compId=%d) ===%n", edition, compId);

                try {
                    // 1단계: rec.do에서 경기 목록 수집
                    List<ScheduleEntry> schedule = fetchSchedule(edition);
                    System.out.printf("  경기 목록: %d건 (계주 제외)%n", schedule.size());

                    // 2단계: 각 경기 상세 결과 수집
                    int done = 0, results = 0, errors = 0;
                    for (ScheduleEntry entry : schedule) {
                        try {
                            List<ResultRow> rows = fetchResults(edition, entry);
                            if (!rows.isEmpty()) {
                                saveResults(conn, compId, entry, rows);
                                conn.commit();
                                results += rows.size();
                            }
                            done++;
                        } catch (Exception e) {
                            errors++;
                            System.err.println("    오류: " + entry + " - " + e.getMessage());
                            conn.rollback();
                        }
                        Thread.sleep(DELAY_MS);
                    }

                    System.out.printf("  완료: %d건, 결과: %d건, 오류: %d건%n", done, results, errors);
                    totalDone += done;
                    totalResults += results;
                    totalErrors += errors;
                } catch (Exception e) {
                    System.err.println("  스케줄 조회 오류: " + e.getMessage());
                    totalErrors++;
                }

                Thread.sleep(500);
            }

            System.out.printf("%n전체 완료! 처리: %d, 결과: %d건, 오류: %d%n", totalDone, totalResults, totalErrors);
        }
    }

    // --- 스케줄 수집 ---

    record ScheduleEntry(String kindCd, String detailClassCd, String rhCd,
                         String division, String eventName, String roundName) {
        @Override
        public String toString() {
            return division + " " + eventName + " " + roundName;
        }
    }

    private static List<ScheduleEntry> fetchSchedule(int gameno) throws IOException {
        String url = REC_URL + "?searchGubun=" + GUBUN + "&searchGameno=" + gameno + "&searchClassCd=" + CLASS_CD;
        Document doc = Jsoup.connect(url).timeout(15000).get();

        List<ScheduleEntry> entries = new ArrayList<>();
        Elements rows = doc.select("tr[onclick^=openSide]");

        String currentDivision = "";
        String currentEventName = "";

        for (Element row : rows) {
            // openSide 파라미터 추출
            String onclick = row.attr("onclick");
            Matcher m = Pattern.compile("openSide\\('([^']*)',\\s*'([^']*)',\\s*'([^']*)',\\s*'([^']*)',\\s*'([^']*)'\\)").matcher(onclick);
            if (!m.find()) continue;

            String rhCd = m.group(1);
            String detailClassCd = m.group(4);
            String kindCd = m.group(5);

            // 종별, 세부종목, 경기구분 추출
            Elements tds = row.select("td");
            if (tds.size() < 3) continue;

            String divText = tds.get(0).text().trim();
            if (!divText.isEmpty()) currentDivision = divText;

            String evtText = tds.get(1).text().trim();
            if (!evtText.isEmpty()) currentEventName = evtText;

            String roundText = tds.get(2).text().trim();

            // 계주 제외
            if (currentEventName.contains("계주") || currentEventName.contains("릴레이") || currentEventName.contains("Relay")) {
                continue;
            }

            entries.add(new ScheduleEntry(kindCd, detailClassCd, rhCd,
                    currentDivision, currentEventName, roundText));
        }
        return entries;
    }

    // --- 결과 수집 ---

    record ResultRow(int ranking, String region, String athleteName, String playerId,
                     String teamName, String record, String note) {}

    private static List<ResultRow> fetchResults(int gameno, ScheduleEntry entry) throws IOException {
        String url = DETAIL_URL + "?searchClassCd=" + CLASS_CD + "&searchGubun=" + GUBUN
                + "&searchGameno=" + gameno
                + "&searchKindCd=" + entry.kindCd
                + "&searchDetailClassCd=" + entry.detailClassCd
                + "&searchRhCd=" + entry.rhCd
                + "&searchGmOrd=";

        Document doc = Jsoup.connect(url).timeout(15000).get();
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

            Element nameSpan = tds.get(2).selectFirst("span[onclick]");
            String athleteName;
            String playerId = null;
            if (nameSpan != null) {
                athleteName = nameSpan.text().trim();
                String onclickAttr = nameSpan.attr("onclick");
                Matcher pm = Pattern.compile("'(\\d+)'\\);?$").matcher(onclickAttr);
                if (pm.find()) playerId = pm.group(1);
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

    private static void saveResults(Connection conn, Long compId, ScheduleEntry entry,
                                    List<ResultRow> results) throws SQLException {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String gender = entry.division.startsWith("여") || entry.division.contains("여자") ? "F" : "M";
        boolean teamEvent = false;

        long eventId = findOrCreate(conn,
                "SELECT id FROM event WHERE competition_id=? AND division_name=? AND event_name=?",
                "INSERT INTO event (competition_id,division_name,gender,event_name,team_event,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                ps -> { ps.setLong(1, compId); ps.setString(2, entry.division); ps.setString(3, entry.eventName); },
                ps -> { ps.setLong(1, compId); ps.setString(2, entry.division); ps.setString(3, gender);
                        ps.setString(4, entry.eventName); ps.setBoolean(5, teamEvent);
                        ps.setTimestamp(6, now); ps.setTimestamp(7, now); });

        long roundId = findOrCreate(conn,
                "SELECT id FROM event_round WHERE event_id=? AND round=?",
                "INSERT INTO event_round (event_id,round,created_at,updated_at) VALUES (?,?,?,?)",
                ps -> { ps.setLong(1, eventId); ps.setString(2, entry.roundName); },
                ps -> { ps.setLong(1, eventId); ps.setString(2, entry.roundName);
                        ps.setTimestamp(3, now); ps.setTimestamp(4, now); });

        int heatNumber = parseHeatNumber(entry.rhCd);
        long heatId = findOrCreate(conn,
                "SELECT id FROM event_heat WHERE event_round_id=? AND heat_number=?",
                "INSERT INTO event_heat (event_round_id,heat_number,created_at) VALUES (?,?,?)",
                ps -> { ps.setLong(1, roundId); ps.setInt(2, heatNumber); },
                ps -> { ps.setLong(1, roundId); ps.setInt(2, heatNumber); ps.setTimestamp(3, now); });

        for (ResultRow r : results) {
            long entryId = findOrCreate(conn,
                    "SELECT id FROM competition_entry WHERE competition_id=? AND athlete_name=? AND gender=? AND team_name=?",
                    "INSERT INTO competition_entry (competition_id,athlete_name,gender,region,team_name,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                    ps -> { ps.setLong(1, compId); ps.setString(2, r.athleteName); ps.setString(3, gender); ps.setString(4, r.teamName); },
                    ps -> { ps.setLong(1, compId); ps.setString(2, r.athleteName); ps.setString(3, gender);
                            ps.setString(4, r.region); ps.setString(5, r.teamName);
                            ps.setTimestamp(6, now); ps.setTimestamp(7, now); });

            long heatEntryId = findOrCreate(conn,
                    "SELECT id FROM heat_entry WHERE heat_id=? AND entry_id=?",
                    "INSERT INTO heat_entry (heat_id,entry_id,bib_number) VALUES (?,?,0)",
                    ps -> { ps.setLong(1, heatId); ps.setLong(2, entryId); },
                    ps -> { ps.setLong(1, heatId); ps.setLong(2, entryId); });

            // event_result
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM event_result WHERE heat_entry_id=?")) {
                ps.setLong(1, heatEntryId);
                if (ps.executeQuery().next()) continue;
            }

            String newRecord = null;
            if (r.note != null && !r.note.isEmpty()) {
                if (r.note.contains("한국신")) newRecord = "한국신";
                else if (r.note.contains("대회신")) newRecord = "대회신";
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO event_result (heat_entry_id,ranking,record,new_record,note,created_at) VALUES (?,?,?,?,?,?)")) {
                ps.setLong(1, heatEntryId);
                ps.setInt(2, r.ranking);
                ps.setString(3, r.record.isEmpty() ? null : r.record);
                ps.setString(4, newRecord);
                ps.setString(5, r.note.isEmpty() ? null : r.note);
                ps.setTimestamp(6, now);
                ps.executeUpdate();
            }
        }
    }

    private static int parseHeatNumber(String rhCd) {
        if (rhCd.contains("9000") || rhCd.contains("9090")) return 0;
        try {
            int last = Integer.parseInt(rhCd.substring(rhCd.length() - 1));
            return Math.max(last, 0);
        } catch (NumberFormatException e) { return 0; }
    }

    @FunctionalInterface
    interface ParamSetter { void set(PreparedStatement ps) throws SQLException; }

    private static long findOrCreate(Connection conn, String selectSql, String insertSql,
                                     ParamSetter selectParams, ParamSetter insertParams) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            selectParams.set(ps);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        }
        try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            insertParams.set(ps);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void disableSslVerification() {
        try {
            TrustManager[] trustAll = { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String t) {}
                public void checkServerTrusted(X509Certificate[] c, String t) {}
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}